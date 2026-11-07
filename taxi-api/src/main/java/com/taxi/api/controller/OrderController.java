package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.dto.OrderAbnormalRequest;
import com.taxi.application.dto.OrderCalculateRequest;
import com.taxi.application.dto.OrderCancelRequest;
import com.taxi.application.dto.OrderCreateRequest;
import com.taxi.application.dto.OrderDispatchRequest;
import com.taxi.application.dto.OrderFinishRequest;
import com.taxi.application.dto.OrderStatusLogView;
import com.taxi.application.dto.OrderView;
import com.taxi.application.service.OrderAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单接口：电召建单、派单、接客、结束、核算、取消、异常与时间线。
 */
@Tag(name = "订单调度")
@RestController
@RequestMapping("/api/v1/orders")
public class OrderController {

    private final OrderAppService orderAppService;

    public OrderController(OrderAppService orderAppService) {
        this.orderAppService = orderAppService;
    }

    @Operation(summary = "分页查询订单", description = "支持订单号/乘客称呼模糊、状态、车队、司机与时间范围过滤")
    @GetMapping
    @RequiresPerm("order:list")
    public R<PageResult<OrderView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long fleetId,
            @RequestParam(required = false) Long driverId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime startTime,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime endTime,
            @RequestParam(defaultValue = "1") long page,
            @RequestParam(defaultValue = "20") long size) {
        return R.ok(orderAppService.page(keyword, status, fleetId, driverId, startTime, endTime, page, size));
    }

    @Operation(summary = "订单详情")
    @GetMapping("/{id}")
    @RequiresPerm("order:list")
    public R<OrderView> detail(@PathVariable Long id) {
        return R.ok(orderAppService.detail(id));
    }

    @Operation(summary = "订单状态时间线")
    @GetMapping("/{id}/timeline")
    @RequiresPerm("order:list")
    public R<List<OrderStatusLogView>> timeline(@PathVariable Long id) {
        return R.ok(orderAppService.timeline(id));
    }

    @Operation(summary = "创建电召订单",
            description = "可带 X-Idempotency-Key：同一 key 重复提交返回同一订单 ID，有效期 10 分钟")
    @PostMapping
    @RequiresPerm("order:create")
    @OpLog(module = "订单调度", action = "创建电召订单", bizType = "ORDER")
    public R<Long> create(@Valid @RequestBody OrderCreateRequest request,
                          @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey) {
        return R.ok(orderAppService.create(request, idempotencyKey));
    }

    @Operation(summary = "派单", description = "车辆须有生效的终端-计价器绑定，司机与车辆均不能有未完成订单")
    @PostMapping("/{id}/dispatch")
    @RequiresPerm("order:dispatch")
    @OpLog(module = "订单调度", action = "派单", bizType = "ORDER")
    public R<Void> dispatch(@PathVariable Long id, @Valid @RequestBody OrderDispatchRequest request) {
        orderAppService.dispatch(id, request);
        return R.ok();
    }

    @Operation(summary = "撤回派单")
    @PostMapping("/{id}/undispatch")
    @RequiresPerm("order:dispatch")
    @OpLog(module = "订单调度", action = "撤回派单", bizType = "ORDER")
    public R<Void> undispatch(@PathVariable Long id) {
        orderAppService.undispatch(id);
        return R.ok();
    }

    @Operation(summary = "确认接到乘客", description = "阶段 4 起由 808 0x0B02 自动触发")
    @PostMapping("/{id}/pickup")
    @RequiresPerm("order:dispatch")
    @OpLog(module = "订单调度", action = "确认接客", bizType = "ORDER")
    public R<Void> pickup(@PathVariable Long id,
                          @RequestParam(required = false)
                          @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime pickupTime) {
        orderAppService.pickup(id, pickupTime);
        return R.ok();
    }

    @Operation(summary = "行程结束", description = "阶段 4 起由 808 0x0B05 自动触发")
    @PostMapping("/{id}/finish")
    @RequiresPerm("order:dispatch")
    @OpLog(module = "订单调度", action = "行程结束", bizType = "ORDER")
    public R<Void> finish(@PathVariable Long id, @Valid @RequestBody OrderFinishRequest request) {
        orderAppService.finish(id, request);
        return R.ok();
    }

    @Operation(summary = "费用核算")
    @PostMapping("/{id}/calculate")
    @RequiresPerm("order:calculate")
    @OpLog(module = "订单调度", action = "费用核算", bizType = "ORDER")
    public R<Void> calculate(@PathVariable Long id, @Valid @RequestBody OrderCalculateRequest request) {
        orderAppService.calculate(id, request);
        return R.ok();
    }

    @Operation(summary = "取消订单", description = "载客后不允许取消，只能转异常")
    @PostMapping("/{id}/cancel")
    @RequiresPerm("order:cancel")
    @OpLog(module = "订单调度", action = "取消订单", bizType = "ORDER")
    public R<Void> cancel(@PathVariable Long id, @Valid @RequestBody OrderCancelRequest request) {
        orderAppService.cancel(id, request);
        return R.ok();
    }

    @Operation(summary = "转异常订单")
    @PostMapping("/{id}/abnormal")
    @RequiresPerm("order:abnormal")
    @OpLog(module = "订单调度", action = "转异常订单", bizType = "ORDER")
    public R<Void> markAbnormal(@PathVariable Long id, @Valid @RequestBody OrderAbnormalRequest request) {
        orderAppService.markAbnormal(id, request);
        return R.ok();
    }
}
