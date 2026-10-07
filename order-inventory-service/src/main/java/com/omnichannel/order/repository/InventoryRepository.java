package com.omnichannel.order.repository;

import com.omnichannel.order.entity.Inventory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface InventoryRepository extends JpaRepository<Inventory, String> {

    /** Atomic reserve; returns 0 when stock is insufficient so available can never go negative. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.available = i.available - :qty, i.reserved = i.reserved + :qty, "
            + "i.version = i.version + 1 WHERE i.sku = :sku AND i.available >= :qty")
    int reserve(@Param("sku") String sku, @Param("qty") int qty);

    /** Compensation: give reserved stock back. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.available = i.available + :qty, i.reserved = i.reserved - :qty, "
            + "i.version = i.version + 1 WHERE i.sku = :sku AND i.reserved >= :qty")
    int release(@Param("sku") String sku, @Param("qty") int qty);

    /** Commit: reserved stock is consumed after payment succeeded. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.reserved = i.reserved - :qty, i.version = i.version + 1 "
            + "WHERE i.sku = :sku AND i.reserved >= :qty")
    int commit(@Param("sku") String sku, @Param("qty") int qty);

    /** Admin stock adjustment: sets the sellable quantity. */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE Inventory i SET i.available = :qty, i.version = i.version + 1 WHERE i.sku = :sku")
    int setAvailable(@Param("sku") String sku, @Param("qty") int qty);
}
