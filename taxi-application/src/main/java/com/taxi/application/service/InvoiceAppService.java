package com.taxi.application.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.taxi.application.port.TerminalCommandPort;
import com.taxi.common.exception.BizException;
import com.taxi.common.result.PageResult;
import com.taxi.domain.entity.Order;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 电子发票：申请、开票、终端下发（0x8F01 → 0x8300 降级）、红冲。
 *
 * <p>默认 mock 模式（invoice.mock=true）用于本地联调；生产配置诺诺网关地址与密钥后，
 * 将 issue 中的 mock 分支替换为真实 HTTP 调用即可，幂等键仍使用 order_id。</p>
 */
@Service
public class InvoiceAppService {

    private final JdbcTemplate jdbcTemplate;
    private final OrderAppService orderAppService;
    private final TerminalCommandPort terminalCommandPort;

    @Value("${invoice.mock:true}")
    private boolean mock = true;

    @Value("${invoice.pdf-base:https://invoice.example.com/pdf}")
    private String pdfBase = "https://invoice.example.com/pdf";

    public InvoiceAppService(JdbcTemplate jdbcTemplate, OrderAppService orderAppService,
                             TerminalCommandPort terminalCommandPort) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderAppService = orderAppService;
        this.terminalCommandPort = terminalCommandPort;
    }

    public record InvoiceView(Long id, Long orderId, String orderNo, String invoiceNo, int invoiceType,
                              String buyerName, String buyerEmail, BigDecimal amount, int status,
                              String statusDesc, String pdfUrl, String qrCodeUrl, String failMsg,
                              int retryCount, LocalDateTime invoiceTime, LocalDateTime redTime,
                              String redReason) {
    }

    public record ApplyRequest(Long orderId, String buyerName, String buyerEmail, String buyerTaxNo) {
    }

    /** 申请开票：幂等（t_invoice_record.uk_order_id），成功后推进订单到 INVOICE_SUCCESS。 */
    @Transactional(rollbackFor = Exception.class)
    public InvoiceView apply(ApplyRequest request) {
        if (request.orderId() == null) {
            throw BizException.badRequest("订单 ID 不能为空");
        }
        Order order = orderAppService.findOrThrow(request.orderId());
        if (!"CALCULATED".equals(order.getStatus()) && !"INVOICE_FAIL".equals(order.getStatus())
                && !"INVOICE_PENDING".equals(order.getStatus())) {
            throw BizException.badRequest("当前订单状态不可开票：" + order.getStatus());
        }
        InvoiceView existing = findByOrder(order.getId());
        if (existing != null && existing.status() == 3) {
            return existing;
        }
        BigDecimal amount = order.getPayableAmount() == null ? BigDecimal.ZERO : order.getPayableAmount();
        if (amount.signum() <= 0) {
            throw BizException.badRequest("订单应收金额为 0，不能开票");
        }
        String invoiceNo = mockInvoiceNo(order.getOrderNo());
        String pdfUrl = pdfBase + "/" + invoiceNo + ".pdf";
        BigDecimal tax = amount.multiply(new BigDecimal("0.06"))
                .divide(new BigDecimal("1.06"), 2, RoundingMode.HALF_UP);
        Long id = IdWorker.getId();
        if (existing == null) {
            jdbcTemplate.update("""
                    INSERT INTO t_invoice_record (id, order_id, order_no, invoice_no, invoice_type, buyer_name,
                        buyer_email, buyer_tax_no, amount, tax_amount, status, qr_code_url, pdf_url, invoice_time)
                    VALUES (?,?,?,?,1,?,?,?,?,?,3,?,?,NOW())
                    """, id, order.getId(), order.getOrderNo(), invoiceNo, request.buyerName(),
                    request.buyerEmail(), request.buyerTaxNo(), amount, tax, pdfUrl, pdfUrl);
        }
        if ("CALCULATED".equals(order.getStatus())) {
            orderAppService.submitInvoice(order.getId());
        }
        orderAppService.invoiceSuccess(order.getId());
        boolean sent = dispatch(order, invoiceNo, pdfUrl);
        InvoiceView view = findByOrder(order.getId());
        return new InvoiceView(view.id(), view.orderId(), view.orderNo(), view.invoiceNo(),
                view.invoiceType(), view.buyerName(), view.buyerEmail(), view.amount(), view.status(),
                view.statusDesc(), view.pdfUrl(), view.qrCodeUrl(), sent ? null : "终端离线，已加入待下发",
                view.retryCount(), view.invoiceTime(), view.redTime(), view.redReason());
    }

    public PageResult<InvoiceView> page(String keyword, Integer status, int page, int size) {
        StringBuilder where = new StringBuilder(" WHERE i.deleted = 0 ");
        List<Object> args = new ArrayList<>();
        if (keyword != null && !keyword.isBlank()) {
            where.append(" AND (i.order_no LIKE ? OR i.invoice_no LIKE ?) ");
            args.add("%" + keyword.trim() + "%");
            args.add("%" + keyword.trim() + "%");
        }
        if (status != null) {
            where.append(" AND i.status = ? ");
            args.add(status);
        }
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM t_invoice_record i" + where,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add((page - 1) * size);
        List<InvoiceView> list = jdbcTemplate.query("""
                SELECT i.id, i.order_id, i.order_no, i.invoice_no, i.invoice_type, i.buyer_name,
                       i.buyer_email, i.amount, i.status, i.pdf_url, i.qr_code_url, i.fail_msg,
                       i.retry_count, i.invoice_time, i.red_time, i.red_reason
                FROM t_invoice_record i
                """ + where + " ORDER BY i.id DESC LIMIT ? OFFSET ?",
                (rs, i) -> map(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
                        rs.getString("invoice_no"), rs.getInt("invoice_type"), rs.getString("buyer_name"),
                        rs.getString("buyer_email"), rs.getBigDecimal("amount"), rs.getInt("status"),
                        rs.getString("pdf_url"), rs.getString("qr_code_url"), rs.getString("fail_msg"),
                        rs.getInt("retry_count"), rs.getObject("invoice_time", LocalDateTime.class),
                        rs.getObject("red_time", LocalDateTime.class), rs.getString("red_reason")),
                pageArgs.toArray());
        return PageResult.of(total == null ? 0 : total, page, size, list);
    }

    @Transactional(rollbackFor = Exception.class)
    public InvoiceView red(Long id, String reason) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_invoice_record WHERE id = ? AND deleted = 0", Integer.class, id);
        if (status == null) {
            throw BizException.notFound("发票不存在");
        }
        if (status != 3) {
            throw BizException.badRequest("仅开票成功的发票可红冲");
        }
        jdbcTemplate.update("""
                UPDATE t_invoice_record SET status = 5, red_time = NOW(), red_reason = ?
                WHERE id = ? AND status = 3
                """, reason, id);
        return findById(id);
    }

    public InvoiceView findByOrder(Long orderId) {
        List<InvoiceView> list = jdbcTemplate.query("""
                SELECT id, order_id, order_no, invoice_no, invoice_type, buyer_name, buyer_email, amount,
                       status, pdf_url, qr_code_url, fail_msg, retry_count, invoice_time, red_time, red_reason
                FROM t_invoice_record WHERE order_id = ? AND deleted = 0 LIMIT 1
                """, (rs, i) -> map(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
                rs.getString("invoice_no"), rs.getInt("invoice_type"), rs.getString("buyer_name"),
                rs.getString("buyer_email"), rs.getBigDecimal("amount"), rs.getInt("status"),
                rs.getString("pdf_url"), rs.getString("qr_code_url"), rs.getString("fail_msg"),
                rs.getInt("retry_count"), rs.getObject("invoice_time", LocalDateTime.class),
                rs.getObject("red_time", LocalDateTime.class), rs.getString("red_reason")), orderId);
        return list.isEmpty() ? null : list.get(0);
    }

    public InvoiceView findById(Long id) {
        List<InvoiceView> list = jdbcTemplate.query("""
                SELECT id, order_id, order_no, invoice_no, invoice_type, buyer_name, buyer_email, amount,
                       status, pdf_url, qr_code_url, fail_msg, retry_count, invoice_time, red_time, red_reason
                FROM t_invoice_record WHERE id = ? AND deleted = 0
                """, (rs, i) -> map(rs.getLong("id"), rs.getLong("order_id"), rs.getString("order_no"),
                rs.getString("invoice_no"), rs.getInt("invoice_type"), rs.getString("buyer_name"),
                rs.getString("buyer_email"), rs.getBigDecimal("amount"), rs.getInt("status"),
                rs.getString("pdf_url"), rs.getString("qr_code_url"), rs.getString("fail_msg"),
                rs.getInt("retry_count"), rs.getObject("invoice_time", LocalDateTime.class),
                rs.getObject("red_time", LocalDateTime.class), rs.getString("red_reason")), id);
        if (list.isEmpty()) {
            throw BizException.notFound("发票不存在");
        }
        return list.get(0);
    }

    private boolean dispatch(Order order, String invoiceNo, String pdfUrl) {
        boolean sent = false;
        int type = 1;
        if (terminalCommandPort.online(order.getTerminalId())) {
            sent = terminalCommandPort.sendInvoice(order.getTerminalId(), order.getOrderNo(), invoiceNo, pdfUrl);
            if (!sent) {
                type = 2;
                sent = terminalCommandPort.sendText(order.getTerminalId(),
                        "电子发票已开具，订单" + order.getOrderNo() + "，下载：" + pdfUrl);
            }
        }
        jdbcTemplate.update("""
                INSERT INTO t_terminal_invoice_dispatch (id, invoice_id, order_id, terminal_id, vehicle_id,
                    dispatch_type, dispatch_content, dispatch_status, retry_count, last_send_time)
                VALUES (?,?,?,?,?,?,?,?,0,NOW())
                """, IdWorker.getId(), idOf(order.getId()), order.getId(), order.getTerminalId(),
                order.getVehicleId(), type, type == 1 ? "0x8F01 " + invoiceNo : "0x8300 " + pdfUrl,
                sent ? 1 : 3);
        return sent;
    }

    private Long idOf(Long orderId) {
        InvoiceView view = findByOrder(orderId);
        return view == null ? 0L : view.id();
    }

    private String mockInvoiceNo(String orderNo) {
        return "INV" + orderNo.replaceAll("\\D", "");
    }

    private static InvoiceView map(Long id, Long orderId, String orderNo, String invoiceNo, int type,
                                   String buyerName, String buyerEmail, BigDecimal amount, int status,
                                   String pdfUrl, String qr, String failMsg, int retry,
                                   LocalDateTime invoiceTime, LocalDateTime redTime, String redReason) {
        String desc = switch (status) {
            case 1 -> "待开";
            case 2 -> "开票中";
            case 3 -> "成功";
            case 4 -> "失败";
            case 5 -> "已红冲";
            default -> "未知";
        };
        return new InvoiceView(id, orderId, orderNo, invoiceNo, type, buyerName, buyerEmail, amount,
                status, desc, pdfUrl, qr, failMsg, retry, invoiceTime, redTime, redReason);
    }
}
