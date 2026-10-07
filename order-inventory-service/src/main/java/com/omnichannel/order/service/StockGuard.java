package com.omnichannel.order.service;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.order.repository.InventoryRepository;
import org.redisson.api.RLock;
import org.redisson.api.RScript;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.TimeUnit;

/**
 * Fast guard in front of the database. An atomic Lua script decrements a per-SKU counter in Redis and refuses to
 * go below zero, so under a flash sale most excess requests are rejected without touching PostgreSQL.
 * PostgreSQL stays the source of truth (see InventoryRepository.reserve); if Redis is down the guard is skipped.
 */
@Component
public class StockGuard {

    private static final Logger log = LoggerFactory.getLogger(StockGuard.class);

    /** -2 = counter missing, -1 = insufficient, otherwise the remaining quantity. */
    private static final String RESERVE_LUA = """
            local cur = redis.call('GET', KEYS[1])
            if not cur then return -2 end
            if tonumber(cur) < tonumber(ARGV[1]) then return -1 end
            return redis.call('DECRBY', KEYS[1], ARGV[1])
            """;

    private final RedissonClient redisson;
    private final InventoryRepository inventory;

    public StockGuard(RedissonClient redisson, InventoryRepository inventory) {
        this.redisson = redisson;
        this.inventory = inventory;
    }

    /**
     * Reserves all SKUs or none.
     *
     * @return true when the guard was applied (so the caller must {@link #release} on failure), false when Redis was unavailable
     * @throws AppException OUT_OF_STOCK when any SKU lacks stock
     */
    public boolean tryReserve(Map<String, Integer> quantities) {
        List<String> done = new ArrayList<>();
        try {
            for (var e : new TreeMap<>(quantities).entrySet()) {
                long result = decrement(e.getKey(), e.getValue());
                if (result == -2) {
                    loadFromDatabase(e.getKey());
                    result = decrement(e.getKey(), e.getValue());
                }
                if (result < 0) {
                    rollback(done, quantities);
                    throw new AppException(ErrorCode.OUT_OF_STOCK, "Insufficient stock for " + e.getKey());
                }
                done.add(e.getKey());
            }
            return true;
        } catch (AppException e) {
            throw e;
        } catch (RuntimeException e) {
            log.warn("Redis stock guard unavailable, relying on database only: {}", e.getMessage());
            return false;
        }
    }

    public void release(Map<String, Integer> quantities) {
        try {
            quantities.forEach((sku, qty) -> redisson.getAtomicLong(key(sku)).addAndGet(qty));
        } catch (RuntimeException e) {
            log.warn("Could not give stock back to Redis: {}", e.getMessage());
        }
    }

    /** Overwrites the counter, used by admin stock adjustments. */
    public void set(String sku, int available) {
        try {
            redisson.getAtomicLong(key(sku)).set(available);
        } catch (RuntimeException e) {
            log.warn("Could not update Redis counter for {}: {}", sku, e.getMessage());
        }
    }

    private long decrement(String sku, int qty) {
        Long r = redisson.getScript(StringCodec.INSTANCE).eval(RScript.Mode.READ_WRITE, RESERVE_LUA,
                RScript.ReturnType.INTEGER, List.of(key(sku)), String.valueOf(qty));
        return r == null ? -2 : r;
    }

    private void loadFromDatabase(String sku) {
        RLock lock = redisson.getLock("lock:stock-init:" + sku);
        try {
            if (lock.tryLock(2, 5, TimeUnit.SECONDS)) {
                try {
                    int available = inventory.findById(sku).map(i -> i.getAvailable()).orElse(0);
                    redisson.getBucket(key(sku), StringCodec.INSTANCE).trySet(String.valueOf(available));
                } finally {
                    lock.unlock();
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void rollback(List<String> reserved, Map<String, Integer> quantities) {
        for (String sku : reserved) {
            redisson.getAtomicLong(key(sku)).addAndGet(quantities.get(sku));
        }
    }

    private static String key(String sku) {
        return "stock:" + sku;
    }
}
