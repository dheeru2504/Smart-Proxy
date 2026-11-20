package com.smartproxy.core.auth;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class JwtTokenServiceTest {

    private JwtTokenService jwtTokenService;

    @BeforeEach
    void setUp() {
        jwtTokenService = new JwtTokenService(
                "super-secret-smart-proxy-hmac-key-minimum-256-bits-length!",
                3600000L
        );
    }

    @Test
    void shouldGenerateAndValidateJwtToken() {
        String token = jwtTokenService.generateToken(
                "user-123",
                "tenant-alpha",
                Set.of("USER", "OPERATOR"),
                Set.of("read:orders", "write:orders"),
                3600000L
        );

        assertNotNull(token);

        Optional<ClientIdentity> identityOpt = jwtTokenService.validateAndExtractIdentity(token);
        assertTrue(identityOpt.isPresent());

        ClientIdentity identity = identityOpt.get();
        assertEquals("user-123", identity.clientId());
        assertEquals("tenant-alpha", identity.tenantId());
        assertEquals(AuthType.JWT, identity.authType());
        assertTrue(identity.authenticated());
        assertTrue(identity.hasRole("USER"));
        assertTrue(identity.hasRole("OPERATOR"));
        assertTrue(identity.hasScope("read:orders"));
        assertEquals("tenant-alpha:user-123", identity.getRateLimitKey());
    }

    @Test
    void shouldRejectExpiredJwtToken() throws InterruptedException {
        // Token with 1ms TTL
        String token = jwtTokenService.generateToken(
                "user-123",
                "tenant-alpha",
                Set.of("USER"),
                Set.of("read"),
                1L
        );

        Thread.sleep(10);

        Optional<ClientIdentity> identityOpt = jwtTokenService.validateAndExtractIdentity(token);
        assertTrue(identityOpt.isEmpty(), "Expired token must be rejected");
    }

    @Test
    void shouldRejectTamperedJwtToken() {
        String token = jwtTokenService.generateToken(
                "user-123",
                "tenant-alpha",
                Set.of("USER"),
                Set.of("read"),
                3600000L
        );

        // Tamper with payload
        String tamperedToken = token.substring(0, token.length() - 5) + "abcde";

        Optional<ClientIdentity> identityOpt = jwtTokenService.validateAndExtractIdentity(tamperedToken);
        assertTrue(identityOpt.isEmpty(), "Tampered token must be rejected");
    }
}
