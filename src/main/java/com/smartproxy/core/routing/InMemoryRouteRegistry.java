package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Thread-safe in-memory route registry backed by AtomicReference for zero-lock reads and atomic swaps.
 */
@Component
public class InMemoryRouteRegistry implements RouteRegistry {

    private static final Logger log = LoggerFactory.getLogger(InMemoryRouteRegistry.class);

    private final AtomicReference<RouteSnapshot> currentSnapshot = new AtomicReference<>(RouteSnapshot.EMPTY);

    public InMemoryRouteRegistry() {
        this(List.of());
    }

    public InMemoryRouteRegistry(List<RouteDefinition> initialRoutes) {
        if (initialRoutes != null && !initialRoutes.isEmpty()) {
            reload(initialRoutes);
        }
    }

    @Override
    public Optional<RouteDefinition> match(String httpMethod, String path) {
        return currentSnapshot.get().findMatch(httpMethod, path);
    }

    @Override
    public Optional<RouteDefinition> getRoute(String routeId) {
        return currentSnapshot.get().getRouteById(routeId);
    }

    @Override
    public void reload(List<RouteDefinition> routes) {
        RouteSnapshot newSnapshot = new RouteSnapshot(routes != null ? routes : List.of());
        currentSnapshot.set(newSnapshot);
        log.info("Successfully swapped route snapshot with {} active routes.", newSnapshot.size());
    }

    @Override
    public synchronized void register(RouteDefinition route) {
        if (route == null) return;
        List<RouteDefinition> existing = new ArrayList<>(getRoutes());
        existing.removeIf(r -> r.getRouteId().equals(route.getRouteId()));
        existing.add(route);
        reload(existing);
    }

    @Override
    public synchronized void unregister(String routeId) {
        if (routeId == null) return;
        List<RouteDefinition> existing = new ArrayList<>(getRoutes());
        boolean removed = existing.removeIf(r -> r.getRouteId().equals(routeId));
        if (removed) {
            reload(existing);
        }
    }

    @Override
    public List<RouteDefinition> getRoutes() {
        return currentSnapshot.get().getAllRoutes();
    }

    @Override
    public RouteSnapshot getSnapshot() {
        return currentSnapshot.get();
    }
}
