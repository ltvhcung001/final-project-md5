package com.omnichannel.gateway.filter;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class UserHeaderFilterTest {

    private final UserHeaderFilter filter = new UserHeaderFilter();

    @AfterEach
    void clear() {
        SecurityContextHolder.clearContext();
    }

    private HttpServletRequest run(MockHttpServletRequest request) throws Exception {
        var seen = new AtomicReference<HttpServletRequest>();
        filter.doFilter(request, new MockHttpServletResponse(),
                new MockFilterChain(new jakarta.servlet.http.HttpServlet() {
                    @Override
                    protected void service(HttpServletRequest req, jakarta.servlet.http.HttpServletResponse res) {
                        seen.set(req);
                    }
                }));
        return seen.get();
    }

    @Test
    void clientSuppliedUserHeadersAreStrippedWhenAnonymous() throws Exception {
        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "attacker");
        request.addHeader("X-User-Roles", "ADMIN");
        request.addHeader("Accept", "application/json");

        HttpServletRequest downstream = run(request);

        assertNull(downstream.getHeader("X-User-Id"));
        assertNull(downstream.getHeader("X-User-Roles"));
        assertEquals("application/json", downstream.getHeader("Accept"));
        assertFalse(Collections.list(downstream.getHeaderNames()).stream()
                .anyMatch(n -> n.toLowerCase().startsWith("x-user-")));
    }

    @Test
    void verifiedJwtClaimsReplaceSpoofedHeaders() throws Exception {
        Jwt jwt = new Jwt("token", Instant.now(), Instant.now().plusSeconds(60), Map.of("alg", "HS256"),
                Map.of("sub", "real-user", "roles", List.of("CUSTOMER"), "email", "a@b.c"));
        SecurityContextHolder.getContext().setAuthentication(
                new JwtAuthenticationToken(jwt, AuthorityUtils.createAuthorityList("ROLE_CUSTOMER")));

        var request = new MockHttpServletRequest("GET", "/api/orders");
        request.addHeader("X-User-Id", "attacker");
        request.addHeader("x-user-roles", "ADMIN");

        HttpServletRequest downstream = run(request);

        assertEquals("real-user", downstream.getHeader("X-User-Id"));
        assertEquals("CUSTOMER", downstream.getHeader("X-User-Roles"));
        assertEquals("a@b.c", downstream.getHeader("X-User-Email"));
    }
}
