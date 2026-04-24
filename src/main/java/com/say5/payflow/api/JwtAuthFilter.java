package com.say5.payflow.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

@Component
public class JwtAuthFilter extends OncePerRequestFilter {
    private final JwtService jwt;

    public JwtAuthFilter(JwtService jwt) { this.jwt = jwt; }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String auth = req.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            String token = auth.substring("Bearer ".length()).trim();
            try {
                UUID merchantId = jwt.verify(token);
                var springAuth = new UsernamePasswordAuthenticationToken(
                    merchantId, null, List.of());
                springAuth.setDetails(merchantId);
                SecurityContextHolder.getContext().setAuthentication(springAuth);
                req.setAttribute("merchantId", merchantId);
            } catch (Exception ignored) {
                // Leave context unauthenticated; the security chain will 401.
            }
        }
        chain.doFilter(req, res);
    }
}
