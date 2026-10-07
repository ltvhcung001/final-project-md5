package com.omnichannel.identity.controller;

import com.omnichannel.common.api.ApiResponse;
import com.omnichannel.identity.dto.AuthDtos.LoginRequest;
import com.omnichannel.identity.dto.AuthDtos.RefreshRequest;
import com.omnichannel.identity.dto.AuthDtos.RegisterRequest;
import com.omnichannel.identity.dto.AuthDtos.TokenResponse;
import com.omnichannel.identity.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    @PostMapping("/register")
    public ApiResponse<TokenResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok(auth.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok(auth.login(req));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.ok(auth.refresh(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@Valid @RequestBody RefreshRequest req) {
        auth.logout(req);
        return ApiResponse.ok();
    }
}
