package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Optimized, pre-compiled representation of a RouteDefinition for high-speed evaluation.
 */
public class CompiledRoute {

    private final RouteDefinition definition;
    private final Set<String> normalizedMethods;
    private final boolean matchesAllMethods;
    private final boolean isExactPattern;

    public CompiledRoute(RouteDefinition definition) {
        this.definition = definition;
        Set<String> methods = definition.getMethods();
        if (methods == null || methods.isEmpty() || methods.contains("*") || methods.contains("ALL")) {
            this.normalizedMethods = Collections.emptySet();
            this.matchesAllMethods = true;
        } else {
            Set<String> upperMethods = new HashSet<>();
            for (String m : methods) {
                if (m != null) upperMethods.add(m.trim().toUpperCase());
            }
            this.normalizedMethods = Collections.unmodifiableSet(upperMethods);
            this.matchesAllMethods = false;
        }

        String pat = definition.getPathPattern();
        this.isExactPattern = pat != null && !pat.contains("*") && !pat.contains("?");
    }

    public RouteDefinition getDefinition() {
        return definition;
    }

    public boolean matches(String httpMethod, String path) {
        if (!definition.isEnabled()) {
            return false;
        }

        // Method verification
        if (!matchesAllMethods && httpMethod != null) {
            if (!normalizedMethods.contains(httpMethod.toUpperCase())) {
                return false;
            }
        }

        // Path verification
        if (isExactPattern) {
            return definition.getPathPattern().equals(path);
        }

        return PathMatcher.matches(definition.getPathPattern(), path);
    }
}
