package com.omnichannel.order.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.common.event.Headers;
import com.omnichannel.common.security.Roles;
import com.omnichannel.order.dto.OrderDtos.StockRequest;
import com.omnichannel.order.dto.OrderDtos.StockResponse;
import com.omnichannel.order.service.InventoryService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/{sku}")
    public ApiResponse<StockResponse> get(@RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
                                          @PathVariable String sku) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(service.get(sku));
    }

    @PutMapping("/{sku}")
    public ApiResponse<StockResponse> set(@RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
                                          @PathVariable String sku, @Valid @RequestBody StockRequest req) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(service.setAvailable(sku, req.available()));
    }
}
