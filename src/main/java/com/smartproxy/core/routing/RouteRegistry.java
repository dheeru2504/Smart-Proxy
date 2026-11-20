package com.smartproxy.core.routing;

import com.smartproxy.core.model.RouteDefinition;

import java.util.List;
import java.util.Optional;

/**
 * High-performance registry managing active Gateway route definitions with atomic snapshot swaps.
 */
public interface RouteRegistry {

    /**
     * Finds the winning route definition for an incoming request.
     */
    Optional<RouteDefinition> match(String httpMethod, String path);

    /**
     * Looks up a route by its unique routeId.
     */
    Optional<RouteDefinition> getRoute(String routeId);

    /**
     * Atomically replaces the current routing snapshot with a new list of routes.
     */
    void reload(List<RouteDefinition> routes);

    /**
     * Adds or updates a single route in the snapshot.
     */
    void register(RouteDefinition route);

    /**
     * Removes a route by its ID from the snapshot.
     */
    void unregister(String routeId);

    /**
     * Returns an immutable list of all active routes.
     */
    List<RouteDefinition> getRoutes();

    /**
     * Obtains the current immutable snapshot.
     */
    RouteSnapshot getSnapshot();
}
