package com.ecommerce.Security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Component
public class HeaderAuthFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
        throws ServletException, IOException {

        String userId = request.getHeader("X-User-Id");
        if (userId == null) {
            chain.doFilter(request, response);
            return;
        }

        List<SimpleGrantedAuthority> authorities = new ArrayList<>();

        // 角色
        String rolesHeader = request.getHeader("X-Roles");
        if (rolesHeader != null && !rolesHeader.isEmpty()) {
            Arrays.stream(rolesHeader.split(","))
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        }

        // 权限
        String permsHeader = request.getHeader("X-Permissions");
        if (permsHeader != null && !permsHeader.isEmpty()) {
            Arrays.stream(permsHeader.split(","))
                .map(SimpleGrantedAuthority::new)
                .forEach(authorities::add);
        }

        UsernamePasswordAuthenticationToken auth =
            new UsernamePasswordAuthenticationToken(
                Long.valueOf(userId), null, authorities);

        // merchantId
        String merchantId = request.getHeader("X-Merchant-Id");
        if (merchantId != null && !merchantId.isEmpty()) {
            auth.setDetails(Long.valueOf(merchantId));
        }

        SecurityContextHolder.getContext().setAuthentication(auth);
        chain.doFilter(request, response);
    }
}
