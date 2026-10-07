package com.omnichannel.order.service;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.order.dto.OrderDtos.StockResponse;
import com.omnichannel.order.entity.Inventory;
import com.omnichannel.order.repository.InventoryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InventoryService {

    private final InventoryRepository inventory;
    private final StockGuard stockGuard;

    public InventoryService(InventoryRepository inventory, StockGuard stockGuard) {
        this.inventory = inventory;
        this.stockGuard = stockGuard;
    }

    @Transactional(readOnly = true)
    public StockResponse get(String sku) {
        Inventory i = inventory.findById(sku).orElseThrow(() -> new AppException(ErrorCode.NOT_FOUND, "Unknown SKU"));
        return new StockResponse(i.getSku(), i.getAvailable(), i.getReserved());
    }

    /** Admin: set the sellable quantity of a SKU (creates the row when needed) and refresh the Redis counter. */
    @Transactional
    public StockResponse setAvailable(String sku, int available) {
        if (inventory.setAvailable(sku, available) == 0) {
            inventory.save(new Inventory(sku, available));
        }
        stockGuard.set(sku, available);
        return get(sku);
    }
}
