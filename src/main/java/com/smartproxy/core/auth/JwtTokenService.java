package com.smartproxy.core.auth;

import com.smartproxy.config.SmartProxyProperties;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Service for cryptographic verification and generation of JWT tokens.
 */
@Service
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);
    private static final String DEFAULT_SECRET = "smartproxy-default-hmac-sha256-secret-key-that-is-at-least-256-bits-long!";

    private final SecretKey signingKey;
    private final long defaultExpirationMs;

    @org.springframework.beans.factory.annotation.Autowired
    public JwtTokenService(SmartProxyProperties properties) {
        String configuredSecret = properties.getSecurity().getJwt().getSecret();
        String secret = (configuredSecret != null && configuredSecret.length() >= 32)
                ? configuredSecret
                : DEFAULT_SECRET;

        this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.defaultExpirationMs = properties.getSecurity().getJwt().getExpirationMs();
    }

    public JwtTokenService(String secret, long expirationMs) {
        String effectiveSecret = (secret != null && secret.length() >= 32) ? secret : DEFAULT_SECRET;
        this.signingKey = Keys.hmacShaKeyFor(effectiveSecret.getBytes(StandardCharsets.UTF_8));
        this.defaultExpirationMs = expirationMs > 0 ? expirationMs : 86400000;
    }

    /**
     * Generates a signed JWT token.
     */
    public String generateToken(String clientId, String tenantId, Set<String> roles, Set<String> scopes, Long customTtlMs) {
        long ttl = customTtlMs != null ? customTtlMs : defaultExpirationMs;
        Date now = new Date();
        Date expiry = new Date(now.getTime() + ttl);

        Map<String, Object> claims = new HashMap<>();
        claims.put("tenant_id", tenantId != null ? tenantId : "default");
        claims.put("roles", roles != null ? new ArrayList<>(roles) : List.of());
        claims.put("scopes", scopes != null ? new ArrayList<>(scopes) : List.of());

        return Jwts.builder()
                .subject(clientId)
                .claims(claims)
                .issuer("smart-proxy")
                .issuedAt(now)
                .expiration(expiry)
                .signWith(signingKey)
                .compact();
    }

    /**
     * Parses and cryptographically validates a JWT token string.
     *
     * @param token raw JWT string without "Bearer " prefix
     * @return Optional containing verified ClientIdentity, or empty if invalid/expired
     */
    public Optional<ClientIdentity> validateAndExtractIdentity(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        try {
            Claims claims = Jwts.parser()
                    .verifyWith(signingKey)
                    .build()
                    .parseSignedClaims(token.trim())
                    .getPayload();

            String clientId = claims.getSubject();
            if (clientId == null || clientId.isBlank()) {
                log.debug("JWT rejected: subject (sub) claim is missing");
                return Optional.empty();
            }

            String tenantId = claims.get("tenant_id", String.class);
            if (tenantId == null) {
                tenantId = "default";
            }

            Set<String> roles = extractStringCollection(claims.get("roles"));
            Set<String> scopes = extractStringCollection(claims.get("scopes"));

            Map<String, Object> allClaims = new HashMap<>(claims);

            ClientIdentity identity = ClientIdentity.authenticated(
                    clientId, tenantId, roles, scopes, AuthType.JWT, allClaims
            );

            return Optional.of(identity);
        } catch (ExpiredJwtException e) {
            log.debug("JWT token has expired: {}", e.getMessage());
        } catch (JwtException e) {
            log.debug("Invalid JWT token: {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Unexpected error parsing JWT: {}", e.getMessage());
        }

        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private Set<String> extractStringCollection(Object obj) {
        if (obj == null) {
            return Collections.emptySet();
        }
        if (obj instanceof Collection<?> col) {
            Set<String> set = new HashSet<>();
            for (Object item : col) {
                if (item != null) set.add(item.toString().trim());
            }
            return set;
        }
        if (obj instanceof String str) {
            return Arrays.stream(str.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(HashSet::new, Set::add, Set::addAll);
        }
        return Collections.emptySet();
    }
}
