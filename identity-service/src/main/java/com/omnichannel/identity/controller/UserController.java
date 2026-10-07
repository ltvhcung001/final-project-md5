package com.omnichannel.identity.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.common.event.Headers;
import com.omnichannel.common.security.Roles;
import com.omnichannel.identity.dto.UserDtos.AddressRequest;
import com.omnichannel.identity.dto.UserDtos.AddressResponse;
import com.omnichannel.identity.dto.UserDtos.UpdateRolesRequest;
import com.omnichannel.identity.dto.UserDtos.UserResponse;
import com.omnichannel.identity.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** The caller identity comes from the X-User-* headers that only the gateway can set. */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService users;

    public UserController(UserService users) {
        this.users = users;
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@RequestHeader(Headers.USER_ID) UUID userId) {
        return ApiResponse.ok(users.me(userId));
    }

    @GetMapping("/me/addresses")
    public ApiResponse<List<AddressResponse>> addresses(@RequestHeader(Headers.USER_ID) UUID userId) {
        return ApiResponse.ok(users.listAddresses(userId));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<ApiResponse<AddressResponse>> addAddress(@RequestHeader(Headers.USER_ID) UUID userId,
                                                                   @Valid @RequestBody AddressRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.ok(users.addAddress(userId, req)));
    }

    @DeleteMapping("/me/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(@RequestHeader(Headers.USER_ID) UUID userId, @PathVariable UUID id) {
        users.deleteAddress(userId, id);
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/{id}/roles")
    public ApiResponse<UserResponse> updateRoles(
            @RequestHeader(value = Headers.USER_ROLES, defaultValue = "") String roles,
            @PathVariable UUID id, @Valid @RequestBody UpdateRolesRequest req) {
        Roles.requireAdmin(roles);
        return ApiResponse.ok(users.updateRoles(id, req.roles()));
    }
}
