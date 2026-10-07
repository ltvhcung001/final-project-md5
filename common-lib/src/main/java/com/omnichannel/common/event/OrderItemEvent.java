package com.omnichannel.common.event;

import java.math.BigDecimal;

public record OrderItemEvent(String sku, String productName, int quantity, BigDecimal unitPrice) {
}
