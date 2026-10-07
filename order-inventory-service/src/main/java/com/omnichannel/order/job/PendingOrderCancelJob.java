package com.omnichannel.order.job;

import com.omnichannel.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/** Cancels orders that stayed PENDING (unpaid) longer than the timeout and releases their stock. */
@Component
public class PendingOrderCancelJob {

    private static final Logger log = LoggerFactory.getLogger(PendingOrderCancelJob.class);

    private final OrderService orders;
    private final Duration timeout;

    public PendingOrderCancelJob(OrderService orders, @Value("${order.pending-timeout:PT15M}") Duration timeout) {
        this.orders = orders;
        this.timeout = timeout;
    }

    @Scheduled(fixedDelayString = "${order.cancel-job-interval-ms:60000}")
    public void run() {
        for (UUID id : orders.findExpiredPending(Instant.now().minus(timeout), 200)) {
            try {
                orders.cancel(id, "Payment timeout");
            } catch (RuntimeException e) {
                log.warn("Could not cancel expired order {}: {}", id, e.getMessage());
            }
        }
    }
}
