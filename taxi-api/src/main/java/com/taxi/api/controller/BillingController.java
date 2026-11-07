package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.service.BillingAppService;
import com.taxi.application.support.FareCalculator;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

/**
 * 计价方案、试算与司机结算。
 */
@Tag(name = "计价结算")
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingAppService billingAppService;

    public BillingController(BillingAppService billingAppService) {
        this.billingAppService = billingAppService;
    }

    @Operation(summary = "计价方案列表")
    @GetMapping("/plans")
    @RequiresPerm("pricing:view")
    public R<List<BillingAppService.PlanView>> plans() {
        return R.ok(billingAppService.plans());
    }

    @Operation(summary = "费用试算")
    @PostMapping("/quote")
    @RequiresPerm("pricing:view")
    public R<FareCalculator.Result> quote(@Valid @RequestBody BillingAppService.QuoteRequest request) {
        return R.ok(billingAppService.quote(request));
    }

    @Operation(summary = "分页查询结算单")
    @GetMapping("/settlements")
    @RequiresPerm("settle:view")
    public R<PageResult<BillingAppService.SettleView>> settlements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long fleetId,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(billingAppService.page(start, end, fleetId, status, page, size));
    }

    @Operation(summary = "生成结算单", description = "按司机汇总周期的应收订单，重复生成自动跳过")
    @PostMapping("/settlements/generate")
    @RequiresPerm("settle:generate")
    @OpLog(module = "计价结算", action = "生成结算单", bizType = "SETTLE")
    public R<Integer> generate(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
                               @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
                               @RequestParam(required = false) Long fleetId) {
        return R.ok(billingAppService.generate(start, end, fleetId));
    }

    @Operation(summary = "确认结算单")
    @PostMapping("/settlements/{id}/confirm")
    @RequiresPerm("settle:confirm")
    @OpLog(module = "计价结算", action = "确认结算单", bizType = "SETTLE")
    public R<Void> confirm(@PathVariable Long id) {
        billingAppService.changeStatus(id, 1, 2);
        return R.ok();
    }

    @Operation(summary = "标记已打款")
    @PostMapping("/settlements/{id}/pay")
    @RequiresPerm("settle:pay")
    @OpLog(module = "计价结算", action = "结算打款", bizType = "SETTLE")
    public R<Void> pay(@PathVariable Long id) {
        billingAppService.changeStatus(id, 2, 3);
        return R.ok();
    }

    @Operation(summary = "导出结算单 CSV")
    @GetMapping("/settlements/export")
    @RequiresPerm("settle:export")
    public ResponseEntity<byte[]> export(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end,
            @RequestParam(required = false) Long fleetId) {
        StringBuilder csv = new StringBuilder("\uFEFF结算单号,周期起,周期止,司机,订单数,营业总额,平台服务费,司机分成,应结金额,状态\r\n");
        for (BillingAppService.SettleView v : billingAppService.export(start, end, fleetId)) {
            csv.append(v.settleNo()).append(',').append(v.periodStart()).append(',')
                    .append(v.periodEnd()).append(',').append(v.driverName() == null ? "" : v.driverName()).append(',')
                    .append(v.orderCount()).append(',').append(v.totalAmount()).append(',')
                    .append(v.platformFee()).append(',').append(v.driverShare()).append(',')
                    .append(v.payableAmount()).append(',').append(status(v.status())).append("\r\n");
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=settlements.csv")
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String status(int status) {
        return switch (status) {
            case 1 -> "待确认";
            case 2 -> "已确认";
            case 3 -> "已打款";
            default -> "未知";
        };
    }
}
