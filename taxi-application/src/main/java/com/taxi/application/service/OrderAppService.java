package com.taxi.application.service;

import com.taxi.application.dto.OrderAbnormalRequest;
import com.taxi.application.dto.OrderCalculateRequest;
import com.taxi.application.dto.OrderCancelRequest;
import com.taxi.application.dto.OrderCreateRequest;
import com.taxi.application.dto.OrderDispatchRequest;
import com.taxi.application.dto.OrderFinishRequest;
import com.taxi.application.dto.OrderStatusLogView;
import com.taxi.application.dto.OrderView;
import com.taxi.application.support.ScopeGuard;
import com.taxi.common.context.LoginUser;
import com.taxi.common.context.UserContext;
import com.taxi.common.crypto.MaskUtil;
import com.taxi.common.enums.OrderStatus;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.DeviceBinding;
import com.taxi.domain.entity.Driver;
import com.taxi.domain.entity.Fleet;
import com.taxi.domain.entity.Order;
import com.taxi.domain.entity.OrderStatusLog;
import com.taxi.domain.entity.Vehicle;
import com.taxi.domain.repository.DeviceBindingRepository;
import com.taxi.domain.repository.DriverRepository;
import com.taxi.domain.repository.FleetRepository;
import com.taxi.domain.repository.OrderRepository;
import com.taxi.domain.repository.OrderStatusLogRepository;
import com.taxi.domain.repository.VehicleRepository;
import com.taxi.domain.statemachine.OrderAction;
import com.taxi.domain.statemachine.OrderStateMachine;
import com.taxi.infra.cache.IdempotencyStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

/**
 * 订单用例：电召建单、派单、接客、结束、核算、取消、转异常。
 * <p>所有状态变更只经过 {@link OrderStateMachine}，并在同一事务内写状态流转日志。</p>
 */
@Service
public class OrderAppService {

    private static final Logger log = LoggerFactory.getLogger(OrderAppService.class);
    private static final DateTimeFormatter ORDER_NO_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String IDEM_SCENE = "order:create";
    private static final Duration IDEM_TTL = Duration.ofMinutes(10);
    private static final int SOURCE_ELECTRONIC = 2;
    /** 订单来源：1 巡游（905） */
    private static final int SOURCE_CRUISE = 1;
    /** 流转日志来源：1 后台 2 终端 */
    private static final int LOG_SOURCE_TERMINAL = 2;

    private final OrderRepository orderRepository;
    private final OrderStatusLogRepository statusLogRepository;
    private final DriverRepository driverRepository;
    private final VehicleRepository vehicleRepository;
    private final FleetRepository fleetRepository;
    private final DeviceBindingRepository bindingRepository;
    private final IdempotencyStore idempotencyStore;

    public OrderAppService(OrderRepository orderRepository,
                           OrderStatusLogRepository statusLogRepository,
                           DriverRepository driverRepository,
                           VehicleRepository vehicleRepository,
                           FleetRepository fleetRepository,
                           DeviceBindingRepository bindingRepository,
                           IdempotencyStore idempotencyStore) {
        this.orderRepository = orderRepository;
        this.statusLogRepository = statusLogRepository;
        this.driverRepository = driverRepository;
        this.vehicleRepository = vehicleRepository;
        this.fleetRepository = fleetRepository;
        this.bindingRepository = bindingRepository;
        this.idempotencyStore = idempotencyStore;
    }

    public PageResult<OrderView> page(String keyword, String status, Long fleetId, Long driverId,
                                      LocalDateTime startTime, LocalDateTime endTime,
                                      long page, long size) {
        long safePage = Math.max(1L, page);
        long safeSize = Math.min(Math.max(1L, size), 100L);
        if (StringUtils.hasText(status) && OrderStatus.of(status) == null) {
            throw BizException.badRequest("状态取值非法：" + status);
        }
        Long effectiveFleet = ScopeGuard.fleetFilter(fleetId);
        PageResult<Order> result = orderRepository.search(keyword, status, effectiveFleet, driverId,
                startTime, endTime, (int) safePage, (int) safeSize);
        return PageResult.of(result.total(), result.page(), result.size(), toViews(result.list()));
    }

    public OrderView detail(Long id) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetReadable(order.getFleetId());
        return toViews(List.of(order)).get(0);
    }

    public List<OrderStatusLogView> timeline(Long id) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetReadable(order.getFleetId());
        return statusLogRepository.listByOrder(id).stream()
                .map(l -> new OrderStatusLogView(l.getId(), l.getFromStatus(), desc(l.getFromStatus()),
                        l.getToStatus(), desc(l.getToStatus()), l.getAction(), l.getOperatorId(),
                        l.getOperatorName(), l.getSource(), l.getRemark(), l.getCreateTime()))
                .toList();
    }

    /**
     * 创建电召订单。相同 {@code idempotencyKey} 重复提交返回同一订单 ID。
     */
    @Transactional(rollbackFor = Exception.class)
    public Long create(OrderCreateRequest request, String idempotencyKey) {
        LoginUser current = UserContext.require();
        String key = StringUtils.hasText(idempotencyKey) ? idempotencyKey.trim() : null;
        if (key != null) {
            if (!idempotencyStore.tryAcquire(IDEM_SCENE, key, IDEM_TTL)) {
                String existing = idempotencyStore.findResult(IDEM_SCENE, key);
                if (existing == null) {
                    throw BizException.badRequest("相同请求正在处理中，请稍后重试");
                }
                Order duplicated = orderRepository.findByOrderNo(existing);
                if (duplicated == null) {
                    throw BizException.badRequest("幂等记录已失效，请重新提交");
                }
                log.info("电召建单命中幂等 key={} orderNo={}", key, existing);
                return duplicated.getId();
            }
        }
        try {
            Order order = new Order();
            order.setOrderNo(nextOrderNo());
            order.setOrderSource(SOURCE_ELECTRONIC);
            order.setStatus(OrderStatus.CREATED.name());
            order.setFleetId(current.isGlobalScope() ? null : current.fleetId());
            order.setPassengerName(request.passengerName());
            order.setPassengerPhone(StringUtils.hasText(request.passengerPhone())
                    ? request.passengerPhone().trim() : null);
            order.setStartAddress(request.startAddress());
            order.setStartLng(request.startLng());
            order.setStartLat(request.startLat());
            order.setEndAddress(request.endAddress());
            order.setEndLng(request.endLng());
            order.setEndLat(request.endLat());
            order.setPricingPlanId(request.pricingPlanId());
            order.setDiscountAmount(BigDecimal.ZERO);
            order.setSettleStatus(0);
            order.setRemark(request.remark());
            orderRepository.save(order);

            writeLog(order, null, OrderStatus.CREATED, "CREATE", null, 1);
            if (key != null) {
                idempotencyStore.complete(IDEM_SCENE, key, order.getOrderNo(), IDEM_TTL);
            }
            log.info("创建电召订单 orderNo={} fleetId={} operator={}", order.getOrderNo(),
                    order.getFleetId(), UserContext.currentUsername());
            return order.getId();
        } catch (RuntimeException e) {
            if (key != null) {
                idempotencyStore.release(IDEM_SCENE, key);
            }
            throw e;
        }
    }

    /** 派单：写入车辆、司机、终端、计价器，并置为已派单 */
    @Transactional(rollbackFor = Exception.class)
    public void dispatch(Long id, OrderDispatchRequest request) {
        Order order = findOrThrow(id);
        Driver driver = driverRepository.findById(request.driverId());
        if (driver == null) {
            throw BizException.badRequest("司机不存在：id=" + request.driverId());
        }
        if (driver.getStatus() == null || driver.getStatus() != 1) {
            throw BizException.badRequest("司机不在职，不能派单：" + driver.getName());
        }
        Vehicle vehicle = vehicleRepository.findById(request.vehicleId());
        if (vehicle == null) {
            throw BizException.badRequest("车辆不存在：id=" + request.vehicleId());
        }
        if (vehicle.getStatus() != null && (vehicle.getStatus() == 3 || vehicle.getStatus() == 4)) {
            throw BizException.badRequest("车辆处于维修/停运状态，不能派单：" + vehicle.getPlateNo());
        }
        if (order.getFleetId() != null && vehicle.getFleetId() != null
                && !order.getFleetId().equals(vehicle.getFleetId())) {
            throw BizException.badRequest("车辆与订单不属于同一车队");
        }
        ScopeGuard.requireFleetWritable(vehicle.getFleetId());

        DeviceBinding binding = bindingRepository.findActiveByVehicle(vehicle.getId());
        if (binding == null) {
            throw BizException.badRequest("车辆未绑定终端与计价器，无法派单：" + vehicle.getPlateNo());
        }
        if (orderRepository.countActiveByDriver(driver.getId()) > 0) {
            throw BizException.badRequest("司机还有未完成订单，不能重复派单：" + driver.getName());
        }
        if (orderRepository.countActiveByVehicle(vehicle.getId()) > 0) {
            throw BizException.badRequest("车辆还有未完成订单，不能重复派单：" + vehicle.getPlateNo());
        }

        LocalDateTime now = nowSeconds();
        transit(order, OrderAction.DISPATCH, patch -> {
            patch.setDriverId(driver.getId());
            patch.setVehicleId(vehicle.getId());
            patch.setFleetId(vehicle.getFleetId());
            patch.setTerminalId(binding.getTerminalId());
            patch.setTaximeterSn(binding.getTaximeterSn());
            patch.setDispatchTime(now);
            if (StringUtils.hasText(request.remark())) {
                patch.setRemark(request.remark());
            }
        }, "派单给 " + driver.getName() + " / " + vehicle.getPlateNo(), 1);
    }

    /** 撤回派单，回到已创建状态 */
    @Transactional(rollbackFor = Exception.class)
    public void undispatch(Long id) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        OrderStatus next = OrderStateMachine.transit(OrderStatus.of(order.getStatus()), OrderAction.UNDISPATCH);
        orderRepository.clearDispatch(id, next.name());
        writeLog(order, OrderStatus.of(order.getStatus()), next, OrderAction.UNDISPATCH.name(), "撤回派单", 1);
    }

    /** 接到乘客（阶段 4 起由 808 0x0B02 触发） */
    @Transactional(rollbackFor = Exception.class)
    public void pickup(Long id, LocalDateTime pickupTime) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        LocalDateTime time = pickupTime == null ? nowSeconds() : pickupTime.withNano(0);
        transit(order, OrderAction.PICKUP, patch -> patch.setPickupTime(time), "确认接到乘客", 1);
    }

    /** 行程结束 */
    @Transactional(rollbackFor = Exception.class)
    public void finish(Long id, OrderFinishRequest request) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        LocalDateTime time = request.finishTime() == null ? nowSeconds() : request.finishTime().withNano(0);
        if (order.getPickupTime() != null && time.isBefore(order.getPickupTime())) {
            throw BizException.badRequest("下车时间不能早于上车时间");
        }
        transit(order, OrderAction.FINISH, patch -> {
            patch.setFinishTime(time);
            patch.setMileage(request.mileage());
            patch.setEmptyMileage(request.emptyMileage());
            patch.setWaitingDuration(request.waitingDuration());
            patch.setDurationSec(request.durationSec());
            if (StringUtils.hasText(request.remark())) {
                patch.setRemark(request.remark());
            }
        }, "行程结束", 1);
    }

    /** 费用核算：阶段 3 人工录入金额，阶段 5 由计价引擎调用同名能力 */
    @Transactional(rollbackFor = Exception.class)
    public void calculate(Long id, OrderCalculateRequest request) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        if (discount.compareTo(request.totalAmount()) > 0) {
            throw BizException.badRequest("优惠金额不能大于计价金额");
        }
        BigDecimal payable = request.totalAmount().subtract(discount);
        transit(order, OrderAction.CALCULATE, patch -> {
            patch.setTotalAmount(request.totalAmount());
            patch.setDiscountAmount(discount);
            patch.setPayableAmount(payable);
            patch.setCalcTime(nowSeconds());
            patch.setPricingPlanId(request.pricingPlanId());
        }, "费用核算：应收 " + payable.setScale(2, java.math.RoundingMode.HALF_UP) + " 元", 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void cancel(Long id, OrderCancelRequest request) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        transit(order, OrderAction.CANCEL, patch -> patch.setCancelReason(request.reason()),
                request.reason(), 1);
    }

    @Transactional(rollbackFor = Exception.class)
    public void markAbnormal(Long id, OrderAbnormalRequest request) {
        Order order = findOrThrow(id);
        ScopeGuard.requireFleetWritable(order.getFleetId());
        transit(order, OrderAction.MARK_ABNORMAL, patch -> patch.setAbnormalReason(request.reason()),
                request.reason(), 1);
    }

    /**
     * 905 0x0B02 营运开始：巡游单落库，并在同一事务内流转到「接到乘客」。
     * <p>以 businessId 幂等：终端重传直接返回已有订单，不重复落单。
     * 车辆已有进行中订单时拒绝，避免一车两单。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Order ingestOperationStart(String businessId, String terminalId, LocalDateTime startTime,
                                      BigDecimal lng, BigDecimal lat) {
        if (!StringUtils.hasText(businessId)) {
            throw BizException.badRequest("0x0B02 营运 ID 为空");
        }
        Order existing = orderRepository.findByBusinessId(businessId);
        if (existing != null) {
            log.info("0x0B02 重复上报 businessId={} 返回已有订单 {}", businessId, existing.getOrderNo());
            return existing;
        }
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        if (binding == null) {
            throw BizException.badRequest("终端未绑定车辆，拒绝落单：" + terminalId);
        }
        Vehicle vehicle = vehicleRepository.findById(binding.getVehicleId());
        if (vehicle == null) {
            throw BizException.badRequest("绑定车辆不存在：id=" + binding.getVehicleId());
        }
        if (orderRepository.countActiveByVehicle(vehicle.getId()) > 0) {
            throw BizException.badRequest("车辆存在未结束订单，拒绝重复开始：" + vehicle.getPlateNo());
        }

        LocalDateTime time = startTime == null ? nowSeconds() : startTime.withNano(0);
        Order order = new Order();
        order.setOrderNo(nextOrderNo("TO"));
        order.setOrderSource(SOURCE_CRUISE);
        order.setBusinessId(businessId.trim());
        order.setStatus(OrderStatus.CREATED.name());
        order.setFleetId(vehicle.getFleetId());
        order.setVehicleId(vehicle.getId());
        order.setDriverId(vehicle.getDriverId());
        order.setTerminalId(binding.getTerminalId() == null ? terminalId : binding.getTerminalId());
        order.setTaximeterSn(binding.getTaximeterSn());
        order.setStartLng(lng);
        order.setStartLat(lat);
        order.setDiscountAmount(BigDecimal.ZERO);
        order.setSettleStatus(0);
        order.setRemark("905 营运开始报文落单");
        orderRepository.save(order);
        writeLog(order, null, OrderStatus.CREATED, "CREATE", "905 0x0B02 营运开始", LOG_SOURCE_TERMINAL);

        // 巡游单无平台派单动作，此处补一条 DISPATCH 日志保持时间线完整，来源标记为终端
        transit(order, OrderAction.DISPATCH, patch -> patch.setDispatchTime(time),
                "巡游订单自动关联 " + vehicle.getPlateNo() + " / " + terminalId, LOG_SOURCE_TERMINAL);
        order.setStatus(OrderStatus.DISPATCHED.name());
        transit(order, OrderAction.PICKUP, patch -> patch.setPickupTime(time),
                "905 0x0B02 接到乘客", LOG_SOURCE_TERMINAL);
        order.setStatus(OrderStatus.PICKUP.name());
        order.setPickupTime(time);
        log.info("905 营运开始落单 orderNo={} businessId={} terminal={} plate={}",
                order.getOrderNo(), businessId, terminalId, vehicle.getPlateNo());
        return order;
    }

    /**
     * 905 0x0B05 营运结束：行程结束 + 计价金额核算。
     * <p>订单不存在时补建异常单（不新增状态），供人工复核；重复上报按幂等忽略。</p>
     */
    @Transactional(rollbackFor = Exception.class)
    public Order ingestOperationEnd(String businessId, String terminalId, LocalDateTime endTime,
                                    BigDecimal lng, BigDecimal lat, BigDecimal mileage,
                                    Integer waitingSeconds, BigDecimal totalAmount, BigDecimal discountAmount) {
        if (!StringUtils.hasText(businessId)) {
            throw BizException.badRequest("0x0B05 营运 ID 为空");
        }
        LocalDateTime time = endTime == null ? nowSeconds() : endTime.withNano(0);
        BigDecimal total = totalAmount == null ? BigDecimal.ZERO : totalAmount;
        BigDecimal discount = discountAmount == null ? BigDecimal.ZERO : discountAmount;
        if (discount.compareTo(total) > 0) {
            throw BizException.badRequest("0x0B05 优惠金额不能大于计价金额");
        }
        Order order = orderRepository.findByBusinessId(businessId);
        if (order == null) {
            return supplementAbnormal(businessId, terminalId, time, lng, lat, mileage,
                    waitingSeconds, total, discount);
        }
        OrderStatus current = OrderStatus.of(order.getStatus());
        if (current == OrderStatus.PICKUP) {
            int duration = 0;
            if (order.getPickupTime() != null) {
                long seconds = Duration.between(order.getPickupTime(), time).getSeconds();
                duration = seconds < 0 ? 0 : (int) seconds;
            }
            int finalDuration = duration;
            transit(order, OrderAction.FINISH, patch -> {
                patch.setFinishTime(time);
                patch.setEndLng(lng);
                patch.setEndLat(lat);
                patch.setMileage(mileage);
                patch.setWaitingDuration(waitingSeconds);
                patch.setDurationSec(finalDuration);
            }, "905 0x0B05 行程结束", LOG_SOURCE_TERMINAL);
            order.setStatus(OrderStatus.FINISHED.name());
            order.setFinishTime(time);
            current = OrderStatus.FINISHED;
        }
        if (current == OrderStatus.FINISHED) {
            BigDecimal payable = total.subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP);
            transit(order, OrderAction.CALCULATE, patch -> {
                patch.setTotalAmount(total);
                patch.setDiscountAmount(discount);
                patch.setPayableAmount(payable);
                patch.setCalcTime(nowSeconds());
            }, "905 0x0B05 计价金额核算：应收 " + payable + " 元", LOG_SOURCE_TERMINAL);
            order.setStatus(OrderStatus.CALCULATED.name());
            log.info("905 营运结束 orderNo={} businessId={} 计价={} 应收={}",
                    order.getOrderNo(), businessId, total, payable);
            return order;
        }
        if (current == OrderStatus.CREATED || current == OrderStatus.DISPATCHED) {
            String reason = "收到 0x0B05 但订单未处于载客状态（" + current + "）";
            transit(order, OrderAction.MARK_ABNORMAL, patch -> patch.setAbnormalReason(reason),
                    reason, LOG_SOURCE_TERMINAL);
            order.setStatus(OrderStatus.ABNORMAL.name());
            return order;
        }
        log.warn("0x0B05 到达时订单 {} 状态为 {}，按幂等忽略", order.getOrderNo(), current);
        return order;
    }

    /** 仅收到 0x0B05 的补建异常单，覆盖终端缓存丢失/平台故障场景 */
    private Order supplementAbnormal(String businessId, String terminalId, LocalDateTime time,
                                     BigDecimal lng, BigDecimal lat, BigDecimal mileage,
                                     Integer waitingSeconds, BigDecimal total, BigDecimal discount) {
        DeviceBinding binding = bindingRepository.findActiveByTerminal(terminalId);
        Vehicle vehicle = binding == null ? null : vehicleRepository.findById(binding.getVehicleId());
        Order order = new Order();
        order.setOrderNo(nextOrderNo("TO"));
        order.setOrderSource(SOURCE_CRUISE);
        order.setBusinessId(businessId.trim());
        order.setStatus(OrderStatus.ABNORMAL.name());
        order.setFleetId(vehicle == null ? null : vehicle.getFleetId());
        order.setVehicleId(vehicle == null ? null : vehicle.getId());
        order.setDriverId(vehicle == null ? null : vehicle.getDriverId());
        order.setTerminalId(binding == null ? terminalId : binding.getTerminalId());
        order.setTaximeterSn(binding == null ? null : binding.getTaximeterSn());
        order.setFinishTime(time);
        order.setEndLng(lng);
        order.setEndLat(lat);
        order.setMileage(mileage);
        order.setWaitingDuration(waitingSeconds);
        order.setTotalAmount(total);
        order.setDiscountAmount(discount);
        order.setPayableAmount(total.subtract(discount).setScale(2, java.math.RoundingMode.HALF_UP));
        order.setSettleStatus(0);
        order.setAbnormalReason("仅收到 0x0B05 营运结束报文，订单为补建异常单");
        orderRepository.save(order);
        writeLog(order, null, OrderStatus.ABNORMAL, "SUPPLEMENT",
                order.getAbnormalReason(), LOG_SOURCE_TERMINAL);
        log.warn("0x0B05 无对应订单，已补建异常单 orderNo={} businessId={}", order.getOrderNo(), businessId);
        return order;
    }

    public Order findOrThrow(Long id) {
        Order order = orderRepository.findById(id);
        if (order == null) {
            throw BizException.notFound("订单不存在：id=" + id);
        }
        return order;
    }

    /** 提交开票（阶段 8 调用） */
    @Transactional(rollbackFor = Exception.class)
    public void submitInvoice(Long id) {
        Order order = findOrThrow(id);
        transit(order, OrderAction.SUBMIT_INVOICE, patch -> {
        }, "提交开票", 1);
    }

    /** 开票成功（阶段 8 调用） */
    @Transactional(rollbackFor = Exception.class)
    public void invoiceSuccess(Long id) {
        Order order = findOrThrow(id);
        transit(order, OrderAction.INVOICE_SUCCESS, patch -> {
        }, "开票成功", 1);
    }

    /** 开票失败（阶段 8 调用） */
    @Transactional(rollbackFor = Exception.class)
    public void invoiceFail(Long id, String reason) {
        Order order = findOrThrow(id);
        transit(order, OrderAction.INVOICE_FAIL, patch -> patch.setAbnormalReason(reason), reason, 1);
    }

    /** 统一的流转入口：校验状态机 → 更新字段 → 写流转日志 */
    private void transit(Order order, OrderAction action, Consumer<Order> mutator, String remark, int source) {
        OrderStatus current = OrderStatus.of(order.getStatus());
        OrderStatus next = OrderStateMachine.transit(current, action);

        Order patch = new Order();
        patch.setId(order.getId());
        patch.setStatus(next.name());
        mutator.accept(patch);
        orderRepository.update(patch);

        writeLog(order, current, next, action.name(), remark, source);
        log.info("订单流转 orderNo={} {} -> {} action={} operator={}", order.getOrderNo(),
                current, next, action, UserContext.currentUsername());
    }

    private void writeLog(Order order, OrderStatus from, OrderStatus to, String action, String remark, int source) {
        OrderStatusLog entity = new OrderStatusLog();
        entity.setOrderId(order.getId());
        entity.setOrderNo(order.getOrderNo());
        entity.setFromStatus(from == null ? null : from.name());
        entity.setToStatus(to.name());
        entity.setAction(action);
        LoginUser current = UserContext.get();
        if (current != null) {
            entity.setOperatorId(current.userId());
            entity.setOperatorName(StringUtils.hasText(current.realName())
                    ? current.realName() : current.username());
        }
        entity.setSource(source);
        entity.setRemark(remark);
        statusLogRepository.save(entity);
    }

    private List<OrderView> toViews(List<Order> orders) {
        Map<Long, String> plates = new HashMap<>();
        Map<Long, String> driverNames = new HashMap<>();
        Map<Long, String> fleetNames = new HashMap<>();
        List<OrderView> views = new ArrayList<>(orders.size());
        for (Order o : orders) {
            String plateNo = o.getVehicleId() == null ? null
                    : plates.computeIfAbsent(o.getVehicleId(), vid -> {
                        Vehicle v = vehicleRepository.findById(vid);
                        return v == null ? null : v.getPlateNo();
                    });
            String driverName = o.getDriverId() == null ? null
                    : driverNames.computeIfAbsent(o.getDriverId(), did -> {
                        Driver d = driverRepository.findById(did);
                        return d == null ? null : d.getName();
                    });
            String fleetName = o.getFleetId() == null ? null
                    : fleetNames.computeIfAbsent(o.getFleetId(), fid -> {
                        Fleet f = fleetRepository.findById(fid);
                        return f == null ? null : f.getFleetName();
                    });
            OrderStatus status = OrderStatus.of(o.getStatus());
            views.add(new OrderView(o.getId(), o.getOrderNo(), o.getOrderSource(), o.getStatus(),
                    status == null ? null : status.getDesc(), o.getVehicleId(), plateNo, o.getDriverId(),
                    driverName, o.getFleetId(), fleetName, o.getTerminalId(), o.getTaximeterSn(),
                    o.getPassengerName(), MaskUtil.maskPhone(o.getPassengerPhone()), o.getStartAddress(),
                    o.getStartLng(), o.getStartLat(), o.getEndAddress(), o.getEndLng(), o.getEndLat(),
                    o.getDispatchTime(), o.getPickupTime(), o.getFinishTime(), o.getCalcTime(), o.getMileage(),
                    o.getWaitingDuration(), o.getDurationSec(), o.getTotalAmount(), o.getDiscountAmount(),
                    o.getPayableAmount(), o.getAbnormalReason(), o.getCancelReason(), o.getRemark(),
                    nextActions(status), o.getCreateTime()));
        }
        return views;
    }

    private List<String> nextActions(OrderStatus status) {
        List<String> actions = new ArrayList<>();
        for (OrderAction action : OrderAction.values()) {
            if (OrderStateMachine.allowed(status, action)) {
                actions.add(action.name());
            }
        }
        return actions;
    }

    private String desc(String statusName) {
        OrderStatus status = OrderStatus.of(statusName);
        return status == null ? null : status.getDesc();
    }

    /** 统一到秒：MySQL DATETIME 无小数位，纳秒比较会因四舍五入产生假冲突 */
    private LocalDateTime nowSeconds() {
        return LocalDateTime.now().withNano(0);
    }

    private String nextOrderNo() {
        return nextOrderNo("EO");
    }

    private String nextOrderNo(String prefix) {
        for (int i = 0; i < 5; i++) {
            String candidate = prefix + LocalDateTime.now().format(ORDER_NO_TIME)
                    + String.format("%04d", ThreadLocalRandom.current().nextInt(10000));
            if (orderRepository.findByOrderNo(candidate) == null) {
                return candidate;
            }
        }
        throw new BizException("订单号生成失败，请重试");
    }
}
