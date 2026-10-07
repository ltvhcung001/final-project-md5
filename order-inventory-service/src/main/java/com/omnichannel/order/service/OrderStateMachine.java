package com.omnichannel.order.service;

import com.omnichannel.common.event.OrderStatus;
import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** PENDING -> CONFIRMED -> SHIPPING -> COMPLETED, and PENDING/CONFIRMED -> CANCELLED. */
public final class OrderStateMachine {

    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED.put(OrderStatus.PENDING, EnumSet.of(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.CONFIRMED, EnumSet.of(OrderStatus.SHIPPING, OrderStatus.CANCELLED));
        ALLOWED.put(OrderStatus.SHIPPING, EnumSet.of(OrderStatus.COMPLETED));
        ALLOWED.put(OrderStatus.COMPLETED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private OrderStateMachine() {
    }

    public static boolean canTransition(OrderStatus from, OrderStatus to) {
        return ALLOWED.get(from).contains(to);
    }

    public static void assertTransition(OrderStatus from, OrderStatus to) {
        if (!canTransition(from, to)) {
            throw new AppException(ErrorCode.INVALID_ORDER_STATE, "Cannot move order from " + from + " to " + to);
        }
    }
}
