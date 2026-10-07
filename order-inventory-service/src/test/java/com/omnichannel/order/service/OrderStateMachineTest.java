package com.omnichannel.order.service;

import com.omnichannel.common.event.OrderStatus;
import com.omnichannel.common.exception.AppException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderStateMachineTest {

    @Test
    void happyPathIsAllowed() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CONFIRMED));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.SHIPPING));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.SHIPPING, OrderStatus.COMPLETED));
    }

    @Test
    void pendingAndConfirmedCanBeCancelled() {
        assertTrue(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.CANCELLED));
        assertTrue(OrderStateMachine.canTransition(OrderStatus.CONFIRMED, OrderStatus.CANCELLED));
    }

    @Test
    void terminalStatesAreFinal() {
        for (OrderStatus next : OrderStatus.values()) {
            assertFalse(OrderStateMachine.canTransition(OrderStatus.COMPLETED, next));
            assertFalse(OrderStateMachine.canTransition(OrderStatus.CANCELLED, next));
        }
    }

    @Test
    void cannotSkipSteps() {
        assertFalse(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.SHIPPING));
        assertFalse(OrderStateMachine.canTransition(OrderStatus.PENDING, OrderStatus.COMPLETED));
        assertThrows(AppException.class,
                () -> OrderStateMachine.assertTransition(OrderStatus.SHIPPING, OrderStatus.CANCELLED));
    }
}
