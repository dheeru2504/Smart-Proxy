package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;

import java.util.*;

/**
 * Immutable in-memory snapshot of active gateway routes.
 * Employs deterministic ordering and fast-path exact index lookup.
 */
public class RouteSnapshot {

    public static final RouteSnapshot EMPTY = new RouteSnapshot(Collections.emptyList());

    private final List<CompiledRoute> compiledRoutes;
    private final Map<String, List<CompiledRoute>> exactPathIndex;
    private final Map<String, RouteDefinition> routesById;

    public RouteSnapshot(List<RouteDefinition> definitions) {
        if (definitions == null || definitions.isEmpty()) {
            this.compiledRoutes = Collections.emptyList();
            this.exactPathIndex = Collections.emptyMap();
            this.routesById = Collections.emptyMap();
            return;
        }

        // Sort routes deterministically by priority, specificity, method, and ID
        List<RouteDefinition> sorted = new ArrayList<>(definitions);
        sorted.sort(RouteComparator.INSTANCE);

        List<CompiledRoute> compiled = new ArrayList<>(sorted.size());
        Map<String, List<CompiledRoute>> exactMap = new HashMap<>();
        Map<String, RouteDefinition> byIdMap = new HashMap<>();

        for (RouteDefinition def : sorted) {
            CompiledRoute cr = new CompiledRoute(def);
            compiled.add(cr);
            if (def.getRouteId() != null) {
                byIdMap.put(def.getRouteId(), def);
            }

            String pattern = def.getPathPattern();
            if (pattern != null && !pattern.contains("*") && !pattern.contains("?")) {
                exactMap.computeIfAbsent(pattern, k -> new ArrayList<>()).add(cr);
            }
        }

        this.compiledRoutes = Collections.unmodifiableList(compiled);
        this.exactPathIndex = Collections.unmodifiableMap(exactMap);
        this.routesById = Collections.unmodifiableMap(byIdMap);
    }

    /**
     * Finds the highest-precedence matching route for the given HTTP method and path.
     */
    public Optional<RouteDefinition> findMatch(String httpMethod, String path) {
        if (path == null) {
            return Optional.empty();
        }

        // 1. Fast Path: Check exact match index
        List<CompiledRoute> exactMatches = exactPathIndex.get(path);
        if (exactMatches != null) {
            for (CompiledRoute cr : exactMatches) {
                if (cr.matches(httpMethod, path)) {
                    return Optional.of(cr.getDefinition());
                }
            }
        }

        // 2. Linear scan over deterministically ordered compiled routes
        for (CompiledRoute cr : compiledRoutes) {
            if (cr.matches(httpMethod, path)) {
                return Optional.of(cr.getDefinition());
            }
        }

        return Optional.empty();
    }

    public Optional<RouteDefinition> getRouteById(String routeId) {
        return Optional.ofNullable(routesById.get(routeId));
    }

    public List<RouteDefinition> getAllRoutes() {
        return compiledRoutes.stream()
                .map(CompiledRoute::getDefinition)
                .toList();
    }

    public int size() {
        return compiledRoutes.size();
    }
}
