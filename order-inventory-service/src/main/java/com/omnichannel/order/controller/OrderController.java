package com.omnichannel.order.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.common.api.PageResponse;
import com.omnichannel.common.event.Headers;
import com.omnichannel.common.security.Roles;
import com.omnichannel.order.dto.OrderDtos.OrderResponse;
import com.omnichannel.order.dto.OrderDtos.PlaceOrderRequest;
import com.omnichannel.order.dto.OrderDtos.UpdateStatusRequest;
import com.omnichannel.order.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService service;

    public OrderController(OrderService service) {
        this.service = service;
    }

    /** Idempotency-Key makes client retries safe: the same key returns the same order. */
    @PostMapping
    public ResponseEntity<ApiResponse<OrderResponse>> place(
            @RequestHeader(Headers.USER_ID) String userId,
            @RequestHeader(value = Headers.USER_EMAIL, required = false) String email,
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody PlaceOrderRequest req) {
        OrderResponse order = service.placeOrder(userId, email, idempotencyKey, req);
        return ResponseEntity.created(URI.create("/api/orders/" + order.id())).body(ApiResponse.ok(order));
    }

    @GetMapping
    public ApiResponse<PageResponse<OrderResponse>> mine(
            @RequestHeader(Headers.USER_ID) String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(service.listMine(userId, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<OrderResponse> get(
            @RequestHeader(Headers.USER_ID) String userId,
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @PathVariable UUID id) {
        return ApiResponse.ok(service.get(userId, roles, id));
    }

    @PostMapping("/{id}/cancel")
    public ApiResponse<OrderResponse> cancel(
            @RequestHeader(Headers.USER_ID) String userId,
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @PathVariable UUID id) {
        return ApiResponse.ok(service.cancelByUser(userId, roles, id));
    }

    @PutMapping("/{id}/status")
    public ApiResponse<OrderResponse> updateStatus(
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @PathVariable UUID id, @Valid @RequestBody UpdateStatusRequest req) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(service.advance(id, req.status()));
    }
}
