package com.omnichannel.identity.service;

import com.omnichannel.common.exception.AppException;
import com.omnichannel.common.exception.ErrorCode;
import com.omnichannel.identity.dto.AuthDtos.LoginRequest;
import com.omnichannel.identity.dto.AuthDtos.RefreshRequest;
import com.omnichannel.identity.dto.AuthDtos.RegisterRequest;
import com.omnichannel.identity.dto.AuthDtos.TokenResponse;
import com.omnichannel.identity.entity.RefreshToken;
import com.omnichannel.identity.entity.Role;
import com.omnichannel.identity.entity.User;
import com.omnichannel.identity.repository.RefreshTokenRepository;
import com.omnichannel.identity.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

@Service
public class AuthService {

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder encoder;
    private final JwtEncoder jwtEncoder;
    private final Duration accessTtl;
    private final Duration refreshTtl;
    private final SecureRandom random = new SecureRandom();

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, PasswordEncoder encoder,
                       JwtEncoder jwtEncoder,
                       @Value("${identity.jwt.access-ttl}") Duration accessTtl,
                       @Value("${identity.jwt.refresh-ttl}") Duration refreshTtl) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.encoder = encoder;
        this.jwtEncoder = jwtEncoder;
        this.accessTtl = accessTtl;
        this.refreshTtl = refreshTtl;
    }

    @Transactional
    public TokenResponse register(RegisterRequest req) {
        if (users.existsByEmail(req.email())) {
            throw new AppException(ErrorCode.USER_ALREADY_EXISTS);
        }
        User user = users.save(new User(req.email(), encoder.encode(req.password()),
                req.fullName(), req.phone(), Set.of(Role.CUSTOMER)));
        return issue(user);
    }

    @Transactional
    public TokenResponse login(LoginRequest req) {
        User user = users.findByEmail(req.email())
                .filter(u -> encoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_CREDENTIALS));
        return issue(user);
    }

    /** Refresh token rotation: the presented token is revoked and a new pair is issued. */
    @Transactional
    public TokenResponse refresh(RefreshRequest req) {
        RefreshToken stored = refreshTokens.findByTokenHash(hash(req.refreshToken()))
                .filter(t -> !t.isRevoked() && t.getExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));
        stored.revoke();
        User user = users.findById(stored.getUserId())
                .orElseThrow(() -> new AppException(ErrorCode.INVALID_REFRESH_TOKEN));
        return issue(user);
    }

    @Transactional
    public void logout(RefreshRequest req) {
        refreshTokens.findByTokenHash(hash(req.refreshToken())).ifPresent(RefreshToken::revoke);
    }

    private TokenResponse issue(User user) {
        Instant now = Instant.now();
        List<String> roles = user.getRoles().stream().map(Enum::name).toList();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("omnichannel-identity")
                .subject(user.getId().toString())
                .issuedAt(now)
                .expiresAt(now.plus(accessTtl))
                .claim("email", user.getEmail())
                .claim("roles", roles)
                .build();
        String access = jwtEncoder.encode(JwtEncoderParameters.from(
                JwsHeader.with(MacAlgorithm.HS256).build(), claims)).getTokenValue();

        byte[] raw = new byte[32];
        random.nextBytes(raw);
        String refresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        refreshTokens.save(new RefreshToken(user.getId(), hash(refresh), now.plus(refreshTtl)));

        return new TokenResponse(access, refresh, "Bearer", accessTtl.toSeconds());
    }

    private static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
