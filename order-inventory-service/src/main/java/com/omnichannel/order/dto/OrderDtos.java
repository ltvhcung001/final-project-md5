package com.omnichannel.order.dto;

import com.omnichannel.common.event.OrderStatus;
import com.omnichannel.order.entity.Order;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {

    private OrderDtos() {
    }

    public record ItemRequest(@NotBlank String sku, @Min(1) @Max(100) int quantity) {
    }

    public record ShippingRequest(@NotBlank String receiverName, @NotBlank String phone, @NotBlank String address) {
    }

    public record PlaceOrderRequest(
            @NotEmpty @Valid List<ItemRequest> items,
            @NotNull @Valid ShippingRequest shipping,
            /** VNPAY, MOMO or MOCK (sandbox demo / load tests). */
            String paymentMethod) {
    }

    public record ItemResponse(String sku, String productName, BigDecimal unitPrice, int quantity) {
    }

    public record OrderResponse(UUID id, OrderStatus status, BigDecimal totalAmount, String currency,
                                String paymentMethod, String shippingName, String shippingPhone,
                                String shippingAddress, String cancelReason, Instant createdAt,
                                List<ItemResponse> items) {
        public static OrderResponse of(Order o) {
            return new OrderResponse(o.getId(), o.getStatus(), o.getTotalAmount(), o.getCurrency(),
                    o.getPaymentMethod(), o.getShippingName(), o.getShippingPhone(), o.getShippingAddress(),
                    o.getCancelReason(), o.getCreatedAt(),
                    o.getItems().stream()
                            .map(i -> new ItemResponse(i.getSku(), i.getProductName(), i.getUnitPrice(), i.getQuantity()))
                            .toList());
        }
    }

    public record UpdateStatusRequest(@NotNull OrderStatus status) {
    }

    public record StockRequest(@PositiveOrZero int available) {
    }

    public record StockResponse(String sku, int available, int reserved) {
    }
}
