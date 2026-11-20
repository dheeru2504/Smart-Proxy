package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;

import java.util.Comparator;

/**
 * Deterministic comparator ensuring predictable, consistent route precedence.
 *
 * Precedence hierarchy:
 * 1. Explicit Order / Priority (lower number = higher precedence)
 * 2. Exact Path vs Wildcard (exact path matches before wildcard patterns)
 * 3. Literal Prefix Length (longer static prefix wins)
 * 4. Wildcard Count (fewer wildcards wins)
 * 5. Method Specificity (specific HTTP method wins over ANY/all)
 * 6. Lexicographical Route ID (guarantees 100% deterministic tie-break)
 */
public class RouteComparator implements Comparator<RouteDefinition> {

    public static final RouteComparator INSTANCE = new RouteComparator();

    @Override
    public int compare(RouteDefinition r1, RouteDefinition r2) {
        if (r1 == r2) return 0;
        if (r1 == null) return 1;
        if (r2 == null) return -1;

        // 1. Explicit Order
        int orderCompare = Integer.compare(r1.getOrder(), r2.getOrder());
        if (orderCompare != 0) {
            return orderCompare;
        }

        // 2. Exact Match Specificity
        boolean r1Exact = isExact(r1.getPathPattern());
        boolean r2Exact = isExact(r2.getPathPattern());
        if (r1Exact && !r2Exact) return -1;
        if (!r1Exact && r2Exact) return 1;

        // 3. Longest Literal Prefix
        int r1PrefixLen = getLiteralPrefixLength(r1.getPathPattern());
        int r2PrefixLen = getLiteralPrefixLength(r2.getPathPattern());
        if (r1PrefixLen != r2PrefixLen) {
            return Integer.compare(r2PrefixLen, r1PrefixLen); // Higher length = higher precedence (earlier)
        }

        // 4. Wildcard Count (fewer wildcards = more specific)
        int r1Wildcards = countWildcards(r1.getPathPattern());
        int r2Wildcards = countWildcards(r2.getPathPattern());
        if (r1Wildcards != r2Wildcards) {
            return Integer.compare(r1Wildcards, r2Wildcards);
        }

        // 5. Method Specificity (specific method list > wildcard/empty)
        boolean r1HasMethods = r1.getMethods() != null && !r1.getMethods().isEmpty() && !r1.getMethods().contains("*");
        boolean r2HasMethods = r2.getMethods() != null && !r2.getMethods().isEmpty() && !r2.getMethods().contains("*");
        if (r1HasMethods && !r2HasMethods) return -1;
        if (!r1HasMethods && r2HasMethods) return 1;

        // 6. Stable Tie-Breaker
        String id1 = r1.getRouteId() != null ? r1.getRouteId() : "";
        String id2 = r2.getRouteId() != null ? r2.getRouteId() : "";
        return id1.compareTo(id2);
    }

    private boolean isExact(String pattern) {
        return pattern != null && !pattern.contains("*") && !pattern.contains("?");
    }

    private int getLiteralPrefixLength(String pattern) {
        if (pattern == null) return 0;
        int wildcardIdx = pattern.indexOf('*');
        if (wildcardIdx == -1) {
            return pattern.length();
        }
        return wildcardIdx;
    }

    private int countWildcards(String pattern) {
        if (pattern == null) return 0;
        int count = 0;
        for (char c : pattern.toCharArray()) {
            if (c == '*') count++;
        }
        return count;
    }
}
