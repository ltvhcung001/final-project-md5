package com.omnichannel.payment.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.common.event.Headers;
import com.omnichannel.common.security.Roles;
import com.omnichannel.payment.dto.PaymentDtos.PaymentResponse;
import com.omnichannel.payment.dto.PaymentDtos.ReconciliationRow;
import com.omnichannel.payment.service.PaymentService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/payments")
public class PaymentController {

    private final PaymentService payments;

    public PaymentController(PaymentService payments) {
        this.payments = payments;
    }

    /** The payment URL is created asynchronously after the order, so clients poll this endpoint. */
    @GetMapping("/order/{orderId}")
    public ApiResponse<PaymentResponse> get(@RequestHeader(Headers.USER_ID) String userId,
                                            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
                                            @PathVariable UUID orderId) {
        return ApiResponse.ok(payments.get(userId, roles, orderId));
    }

    @PostMapping("/mock/{orderId}/complete")
    public ApiResponse<Map<String, String>> completeMock(
            @RequestHeader(Headers.USER_ID) String userId,
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @PathVariable UUID orderId, @RequestParam(defaultValue = "true") boolean success) {
        return ApiResponse.ok(Map.of("result", payments.completeMock(userId, roles, orderId, success).name()));
    }

    @PostMapping("/{orderId}/refund")
    public ApiResponse<PaymentResponse> refund(
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles, @PathVariable UUID orderId) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(payments.refund(orderId));
    }

    @GetMapping("/reconciliation")
    public ApiResponse<List<ReconciliationRow>> reconciliation(
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(payments.reconcile(date != null ? date : LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh"))));
    }
}
