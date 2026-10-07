package com.omnichannel.payment.gateway;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/** MoMo test environment (captureWallet): HMAC-SHA256 over a fixed, alphabetically ordered field list. */
@Component
public class MomoProvider implements PaymentProvider {

    private final String partnerCode;
    private final String accessKey;
    private final String secretKey;
    private final String endpoint;
    private final String ipnUrl;
    private final String redirectUrl;
    private final RestClient http = RestClient.create();

    public MomoProvider(@Value("${payment.momo.partner-code:}") String partnerCode,
                        @Value("${payment.momo.access-key:}") String accessKey,
                        @Value("${payment.momo.secret-key:}") String secretKey,
                        @Value("${payment.momo.endpoint}") String endpoint,
                        @Value("${payment.momo.ipn-url}") String ipnUrl,
                        @Value("${payment.momo.redirect-url}") String redirectUrl) {
        this.partnerCode = partnerCode;
        this.accessKey = accessKey;
        this.secretKey = secretKey;
        this.endpoint = endpoint;
        this.ipnUrl = ipnUrl;
        this.redirectUrl = redirectUrl;
    }

    @Override
    public String name() {
        return "MOMO";
    }

    @Override
    @SuppressWarnings("unchecked")
    public String createPaymentUrl(PaymentTransaction tx) {
        if (partnerCode.isBlank() || accessKey.isBlank() || secretKey.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "MoMo credentials are not configured");
        }
        String orderId = tx.getOrderId().toString();
        String requestId = UUID.randomUUID().toString();
        String amount = tx.getAmount().toBigInteger().toString();
        String orderInfo = "Thanh toan don hang " + orderId;
        String raw = "accessKey=" + accessKey + "&amount=" + amount + "&extraData=&ipnUrl=" + ipnUrl
                + "&orderId=" + orderId + "&orderInfo=" + orderInfo + "&partnerCode=" + partnerCode
                + "&redirectUrl=" + redirectUrl + "&requestId=" + requestId + "&requestType=captureWallet";

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("partnerCode", partnerCode);
        body.put("requestType", "captureWallet");
        body.put("ipnUrl", ipnUrl);
        body.put("redirectUrl", redirectUrl);
        body.put("orderId", orderId);
        body.put("amount", Long.parseLong(amount));
        body.put("orderInfo", orderInfo);
        body.put("requestId", requestId);
        body.put("extraData", "");
        body.put("lang", "vi");
        body.put("signature", Hmac.hex("HmacSHA256", secretKey, raw));

        Map<String, Object> reply = http.post().uri(endpoint).body(body).retrieve().body(Map.class);
        if (reply == null || reply.get("payUrl") == null) {
            throw new AppException(ErrorCode.UPSTREAM_UNAVAILABLE, "MoMo did not return a payUrl: " + reply);
        }
        return reply.get("payUrl").toString();
    }

    /** Verifies the IPN body signature. */
    public CallbackResult verify(Map<String, Object> b) {
        String raw = "accessKey=" + accessKey + "&amount=" + s(b, "amount") + "&extraData=" + s(b, "extraData")
                + "&message=" + s(b, "message") + "&orderId=" + s(b, "orderId") + "&orderInfo=" + s(b, "orderInfo")
                + "&orderType=" + s(b, "orderType") + "&partnerCode=" + s(b, "partnerCode")
                + "&payType=" + s(b, "payType") + "&requestId=" + s(b, "requestId")
                + "&responseTime=" + s(b, "responseTime") + "&resultCode=" + s(b, "resultCode")
                + "&transId=" + s(b, "transId");
        if (secretKey.isBlank() || !Hmac.safeEquals(s(b, "signature"), Hmac.hex("HmacSHA256", secretKey, raw))) {
            return CallbackResult.invalid();
        }
        try {
            return new CallbackResult(true, UUID.fromString(s(b, "orderId")), "0".equals(s(b, "resultCode")),
                    s(b, "transId"), new BigDecimal(s(b, "amount")), s(b, "message"));
        } catch (RuntimeException e) {
            return CallbackResult.invalid();
        }
    }

    private static String s(Map<String, Object> b, String key) {
        Object v = b.get(key);
        return v == null ? "" : v.toString();
    }
}
