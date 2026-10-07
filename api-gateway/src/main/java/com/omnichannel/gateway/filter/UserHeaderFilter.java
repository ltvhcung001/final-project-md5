package com.omnichannel.gateway.filter;

import com.omnichannel.common.event.Headers;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Strips any client supplied X-User-* header, then (for authenticated requests)
 * adds X-User-Id / X-User-Roles taken from the verified JWT.
 */
@Component
public class UserHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, jakarta.servlet.http.HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        Map<String, String> injected = new HashMap<>();
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth instanceof JwtAuthenticationToken token) {
            Jwt jwt = token.getToken();
            injected.put(Headers.USER_ID, jwt.getSubject());
            List<String> roles = jwt.getClaimAsStringList("roles");
            injected.put(Headers.USER_ROLES, roles == null ? "" : String.join(",", roles));
            String email = jwt.getClaimAsString("email");
            if (email != null) {
                injected.put(Headers.USER_EMAIL, email);
            }
        }
        chain.doFilter(new TrustedHeaderRequest(request, injected), response);
    }

    private static final class TrustedHeaderRequest extends HttpServletRequestWrapper {

        private final Map<String, String> injected;

        TrustedHeaderRequest(HttpServletRequest request, Map<String, String> injected) {
            super(request);
            this.injected = injected;
        }

        private static boolean isUserHeader(String name) {
            return name != null && name.regionMatches(true, 0, Headers.USER_PREFIX, 0, Headers.USER_PREFIX.length());
        }

        private String injectedValue(String name) {
            for (var e : injected.entrySet()) {
                if (e.getKey().equalsIgnoreCase(name)) {
                    return e.getValue();
                }
            }
            return null;
        }

        @Override
        public String getHeader(String name) {
            return isUserHeader(name) ? injectedValue(name) : super.getHeader(name);
        }

        @Override
        public Enumeration<String> getHeaders(String name) {
            if (isUserHeader(name)) {
                String v = injectedValue(name);
                return v == null ? Collections.emptyEnumeration() : Collections.enumeration(List.of(v));
            }
            return super.getHeaders(name);
        }

        @Override
        public Enumeration<String> getHeaderNames() {
            Set<String> names = new LinkedHashSet<>();
            for (String n : Collections.list(super.getHeaderNames())) {
                if (!isUserHeader(n)) {
                    names.add(n);
                }
            }
            names.addAll(injected.keySet());
            return Collections.enumeration(names);
        }
    }
}
