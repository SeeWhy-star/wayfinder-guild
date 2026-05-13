package com.seewhy.syaiagent.security;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Arrays;

@Service
public class OwnerAccessService {

    public static final String OWNER_TOKEN_HEADER = "X-Wayfinder-Owner-Token";
    public static final String OWNER_TOKEN_COOKIE = "WAYFINDER_OWNER_TOKEN";

    private final String ownerToken;

    public OwnerAccessService(@Value("${wayfinder.security.owner-token:}") String ownerToken) {
        this.ownerToken = ownerToken == null ? "" : ownerToken.trim();
    }

    public boolean hasConfiguredOwnerToken() {
        return !ownerToken.isBlank();
    }

    public boolean hasOwnerAccess(HttpServletRequest request) {
        if (!hasConfiguredOwnerToken()) {
            return false;
        }
        return matchesOwnerToken(headerToken(request))
                || matchesOwnerToken(bearerToken(request))
                || matchesOwnerToken(cookieToken(request));
    }

    private boolean matchesOwnerToken(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return false;
        }
        byte[] expected = ownerToken.getBytes(StandardCharsets.UTF_8);
        byte[] actual = candidate.trim().getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expected, actual);
    }

    private String headerToken(HttpServletRequest request) {
        return request.getHeader(OWNER_TOKEN_HEADER);
    }

    private String bearerToken(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (authorization == null || !authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return "";
        }
        return authorization.substring(7);
    }

    private String cookieToken(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null || cookies.length == 0) {
            return "";
        }
        return Arrays.stream(cookies)
                .filter(cookie -> OWNER_TOKEN_COOKIE.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse("");
    }
}
