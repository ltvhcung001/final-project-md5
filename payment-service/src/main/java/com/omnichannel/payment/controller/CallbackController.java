package com.omnichannel.payment.controller;

import com.omnichannel.payment.gateway.CallbackResult;
import com.omnichannel.payment.gateway.MomoProvider;
import com.omnichannel.payment.gateway.VnPayProvider;
import com.omnichannel.payment.service.PaymentService;
import com.omnichannel.payment.service.PaymentService.Outcome;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Public provider callbacks (the gateway lets them through unauthenticated). Authenticity comes from the
 * signature check, and processing is idempotent.
 */
@RestController
@RequestMapping("/api/payments")
public class CallbackController {

    private final PaymentService payments;
    private final VnPayProvider vnpay;
    private final MomoProvider momo;

    public CallbackController(PaymentService payments, VnPayProvider vnpay, MomoProvider momo) {
        this.payments = payments;
        this.vnpay = vnpay;
        this.momo = momo;
    }

    /** VNPay server-to-server notification. Response codes are defined by VNPay. */
    @GetMapping("/ipn/vnpay")
    public Map<String, String> vnpayIpn(@RequestParam Map<String, String> params) {
        CallbackResult r = vnpay.verify(params);
        if (!r.validSignature()) {
            return Map.of("RspCode", "97", "Message", "Invalid signature");
        }
        Outcome o = payments.applyResult(r.orderId(), r.success(), r.providerTxnId(), r.amount(), r.message());
        return switch (o) {
            case APPLIED -> Map.of("RspCode", "00", "Message", "Confirm Success");
            case DUPLICATE -> Map.of("RspCode", "02", "Message", "Order already confirmed");
            case NOT_FOUND -> Map.of("RspCode", "01", "Message", "Order not found");
            case AMOUNT_MISMATCH -> Map.of("RspCode", "04", "Message", "Invalid amount");
        };
    }

    /** Browser redirect after paying. The same idempotent handler runs, so it also works when the IPN cannot reach us (local sandbox). */
    @GetMapping("/return/vnpay")
    public Map<String, Object> vnpayReturn(@RequestParam Map<String, String> params) {
        CallbackResult r = vnpay.verify(params);
        Map<String, Object> body = new LinkedHashMap<>();
        if (!r.validSignature()) {
            body.put("status", "INVALID_SIGNATURE");
            return body;
        }
        Outcome o = payments.applyResult(r.orderId(), r.success(), r.providerTxnId(), r.amount(), r.message());
        body.put("orderId", r.orderId());
        body.put("status", r.success() ? "SUCCEEDED" : "FAILED");
        body.put("processing", o.name());
        return body;
    }

    /** MoMo redirects the browser here; the authoritative result arrives via the signed IPN. */
    @GetMapping("/return/momo")
    public Map<String, String> momoReturn() {
        return Map.of("status", "RECEIVED", "message", "Check the order status for the final result");
    }

    @PostMapping("/ipn/momo")
    public ResponseEntity<Void> momoIpn(@RequestBody Map<String, Object> body) {
        CallbackResult r = momo.verify(body);
        if (!r.validSignature()) {
            return ResponseEntity.badRequest().build();
        }
        payments.applyResult(r.orderId(), r.success(), r.providerTxnId(), r.amount(), r.message());
        return ResponseEntity.noContent().build(); // MoMo expects 204 once the IPN is processed
    }
}
