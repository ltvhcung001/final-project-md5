package com.omnichannel.payment.gateway;

import com.omnichannel.payment.entity.PaymentTransaction;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VnPayProviderTest {

    private final VnPayProvider provider = new VnPayProvider("TESTCODE", "SECRETSECRETSECRET",
            "https://sandbox.vnpayment.vn/paymentv2/vpcpay.html", "http://localhost/return");

    private Map<String, String> params(String url) {
        Map<String, String> m = new LinkedHashMap<>();
        for (String pair : URI.create(url).getRawQuery().split("&")) {
            String[] kv = pair.split("=", 2);
            m.put(kv[0], URLDecoder.decode(kv[1], StandardCharsets.UTF_8));
        }
        return m;
    }

    @Test
    void paymentUrlIsSignedAndCallbackRoundTrips() {
        UUID orderId = UUID.randomUUID();
        var tx = new PaymentTransaction(orderId, "user-1", new BigDecimal("199000"), "VNPAY");

        Map<String, String> p = params(provider.createPaymentUrl(tx));
        assertEquals("19900000", p.get("vnp_Amount")); // VNPay amounts are multiplied by 100
        assertTrue(p.containsKey("vnp_SecureHash"));

        // pretend VNPay answers with a success code and signs it with the same secret
        p.remove("vnp_SecureHash");
        p.put("vnp_ResponseCode", "00");
        p.put("vnp_TransactionStatus", "00");
        p.put("vnp_TransactionNo", "14000001");
        p.put("vnp_SecureHash", sign(p));

        CallbackResult r = provider.verify(p);
        assertTrue(r.validSignature());
        assertTrue(r.success());
        assertEquals(orderId, r.orderId());
        assertEquals(0, new BigDecimal("199000").compareTo(r.amount()));
    }

    @Test
    void tamperedAmountIsRejected() {
        var tx = new PaymentTransaction(UUID.randomUUID(), "user-1", new BigDecimal("199000"), "VNPAY");
        Map<String, String> p = params(provider.createPaymentUrl(tx));
        p.put("vnp_Amount", "100"); // attacker lowers the price but cannot re-sign

        assertFalse(provider.verify(p).validSignature());
    }

    @Test
    void missingSignatureIsRejected() {
        assertFalse(provider.verify(Map.of("vnp_TxnRef", UUID.randomUUID().toString())).validSignature());
    }

    /** Independent re-implementation of the VNPay signing rule, so the test does not just call the code under test. */
    private static String sign(Map<String, String> p) {
        var sorted = new java.util.TreeMap<>(p);
        sorted.remove("vnp_SecureHash");
        String data = sorted.entrySet().stream()
                .map(e -> java.net.URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII) + "="
                        + java.net.URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(java.util.stream.Collectors.joining("&"));
        return Hmac.hex("HmacSHA512", "SECRETSECRETSECRET", data);
    }
}
