package com.taxi.api.controller;

import com.taxi.api.aspect.OpLog;
import com.taxi.api.security.RequiresPerm;
import com.taxi.application.service.InvoiceAppService;
import com.taxi.common.result.PageResult;
import com.taxi.common.result.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 电子发票：申请、查询、红冲。
 */
@Tag(name = "电子发票")
@RestController
@RequestMapping("/api/v1/invoices")
public class InvoiceController {

    private final InvoiceAppService invoiceAppService;

    public InvoiceController(InvoiceAppService invoiceAppService) {
        this.invoiceAppService = invoiceAppService;
    }

    @Operation(summary = "分页查询发票")
    @GetMapping
    @RequiresPerm("invoice:view")
    public R<PageResult<InvoiceAppService.InvoiceView>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return R.ok(invoiceAppService.page(keyword, status, page, size));
    }

    @Operation(summary = "申请开票", description = "以 order_id 幂等；成功后优先 0x8F01 下发，失败降级 0x8300")
    @PostMapping("/apply")
    @RequiresPerm("invoice:issue")
    @OpLog(module = "电子发票", action = "申请开票", bizType = "INVOICE")
    public R<InvoiceAppService.InvoiceView> apply(@Valid @RequestBody InvoiceAppService.ApplyRequest request) {
        return R.ok(invoiceAppService.apply(request));
    }

    @Operation(summary = "发票详情")
    @GetMapping("/{id}")
    @RequiresPerm("invoice:view")
    public R<InvoiceAppService.InvoiceView> detail(@PathVariable Long id) {
        return R.ok(invoiceAppService.findById(id));
    }

    @Operation(summary = "发票红冲")
    @PostMapping("/{id}/red")
    @RequiresPerm("invoice:red")
    @OpLog(module = "电子发票", action = "发票红冲", bizType = "INVOICE")
    public R<InvoiceAppService.InvoiceView> red(@PathVariable Long id,
                                                @RequestParam String reason) {
        return R.ok(invoiceAppService.red(id, reason));
    }
}
