package com.omnichannel.payment.repository;

import com.omnichannel.payment.entity.PaymentTransaction;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentTransactionRepository extends JpaRepository<PaymentTransaction, UUID> {

    Optional<PaymentTransaction> findByOrderId(UUID orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM PaymentTransaction t WHERE t.orderId = :orderId")
    Optional<PaymentTransaction> findByOrderIdForUpdate(@Param("orderId") UUID orderId);

    @Query("SELECT t.status, COUNT(t), COALESCE(SUM(t.amount), 0) FROM PaymentTransaction t "
            + "WHERE t.createdAt >= :from AND t.createdAt < :to GROUP BY t.status")
    List<Object[]> summarise(@Param("from") Instant from, @Param("to") Instant to);
}
