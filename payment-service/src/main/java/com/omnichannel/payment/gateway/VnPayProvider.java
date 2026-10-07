package com.omnichannel.payment.gateway;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.payment.entity.PaymentTransaction;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Collectors;

/** VNPay sandbox (API 2.1.0): HMAC-SHA512 over the sorted, url-encoded vnp_* parameters. */
@Component
public class VnPayProvider implements PaymentProvider {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final ZoneId VN = ZoneId.of("Asia/Ho_Chi_Minh");

    private final String tmnCode;
    private final String hashSecret;
    private final String payUrl;
    private final String returnUrl;

    public VnPayProvider(@Value("${payment.vnpay.tmn-code:}") String tmnCode,
                         @Value("${payment.vnpay.hash-secret:}") String hashSecret,
                         @Value("${payment.vnpay.pay-url}") String payUrl,
                         @Value("${payment.vnpay.return-url}") String returnUrl) {
        this.tmnCode = tmnCode;
        this.hashSecret = hashSecret;
        this.payUrl = payUrl;
        this.returnUrl = returnUrl;
    }

    @Override
    public String name() {
        return "VNPAY";
    }

    @Override
    public String createPaymentUrl(PaymentTransaction tx) {
        if (tmnCode.isBlank() || hashSecret.isBlank()) {
            throw new AppException(ErrorCode.BAD_REQUEST, "VNPay credentials are not configured");
        }
        ZonedDateTime now = ZonedDateTime.now(VN);
        Map<String, String> p = new TreeMap<>();
        p.put("vnp_Version", "2.1.0");
        p.put("vnp_Command", "pay");
        p.put("vnp_TmnCode", tmnCode);
        p.put("vnp_Amount", tx.getAmount().multiply(BigDecimal.valueOf(100)).toBigInteger().toString());
        p.put("vnp_CreateDate", now.format(FORMAT));
        p.put("vnp_ExpireDate", now.plusMinutes(15).format(FORMAT));
        p.put("vnp_CurrCode", "VND");
        p.put("vnp_IpAddr", "127.0.0.1");
        p.put("vnp_Locale", "vn");
        p.put("vnp_OrderInfo", "Thanh toan don hang " + tx.getOrderId());
        p.put("vnp_OrderType", "other");
        p.put("vnp_ReturnUrl", returnUrl);
        p.put("vnp_TxnRef", tx.getOrderId().toString());
        String query = encode(p);
        return payUrl + "?" + query + "&vnp_SecureHash=" + Hmac.hex("HmacSHA512", hashSecret, query);
    }

    /** Verifies the vnp_SecureHash of an IPN / return request and extracts the result. */
    public CallbackResult verify(Map<String, String> params) {
        String received = params.get("vnp_SecureHash");
        Map<String, String> signed = new TreeMap<>();
        params.forEach((k, v) -> {
            if (k.startsWith("vnp_") && !k.equals("vnp_SecureHash") && !k.equals("vnp_SecureHashType")) {
                signed.put(k, v);
            }
        });
        if (hashSecret.isBlank() || !Hmac.safeEquals(received, Hmac.hex("HmacSHA512", hashSecret, encode(signed)))) {
            return CallbackResult.invalid();
        }
        try {
            UUID orderId = UUID.fromString(signed.get("vnp_TxnRef"));
            BigDecimal amount = new BigDecimal(signed.get("vnp_Amount")).movePointLeft(2);
            boolean ok = "00".equals(signed.get("vnp_ResponseCode")) && "00".equals(signed.get("vnp_TransactionStatus"));
            return new CallbackResult(true, orderId, ok, signed.get("vnp_TransactionNo"), amount,
                    "VNPay code " + signed.get("vnp_ResponseCode"));
        } catch (RuntimeException e) {
            return CallbackResult.invalid();
        }
    }

    private static String encode(Map<String, String> sorted) {
        return sorted.entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isEmpty())
                .map(e -> URLEncoder.encode(e.getKey(), StandardCharsets.US_ASCII) + "="
                        + URLEncoder.encode(e.getValue(), StandardCharsets.US_ASCII))
                .collect(Collectors.joining("&"));
    }
}
