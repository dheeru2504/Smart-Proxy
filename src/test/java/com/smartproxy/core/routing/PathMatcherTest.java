package com.smartproxy.core.routing;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PathMatcherTest {

    @Test
    void shouldMatchExactPaths() {
        assertTrue(PathMatcher.matches("/api/v1/users", "/api/v1/users"));
        assertFalse(PathMatcher.matches("/api/v1/users", "/api/v1/orders"));
        assertFalse(PathMatcher.matches("/api/v1/users", "/api/v1/users/123"));
    }

    @Test
    void shouldMatchPrefixWildcards() {
        assertTrue(PathMatcher.matches("/api/v1/**", "/api/v1/users"));
        assertTrue(PathMatcher.matches("/api/v1/**", "/api/v1/users/123/orders"));
        assertTrue(PathMatcher.matches("/api/v1/**", "/api/v1"));
        assertFalse(PathMatcher.matches("/api/v1/**", "/api/v2/users"));
    }

    @Test
    void shouldMatchSingleSegmentWildcards() {
        assertTrue(PathMatcher.matches("/api/*/users", "/api/v1/users"));
        assertTrue(PathMatcher.matches("/api/*/users", "/api/v2/users"));
        assertFalse(PathMatcher.matches("/api/*/users", "/api/v1/v2/users"));
    }

    @Test
    void shouldMatchCatchAll() {
        assertTrue(PathMatcher.matches("/**", "/any/random/path"));
        assertTrue(PathMatcher.matches("/*", "/single"));
    }

    @Test
    void shouldStripPrefix() {
        assertEquals("/v1/users", PathMatcher.stripPrefix("/api/v1/users", 1));
        assertEquals("/users", PathMatcher.stripPrefix("/api/v1/users", 2));
        assertEquals("/", PathMatcher.stripPrefix("/api/v1/users", 3));
        assertEquals("/api/v1/users", PathMatcher.stripPrefix("/api/v1/users", 0));
    }
}
