package com.smartproxy.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "smartproxy")
public class SmartProxyProperties {

    private GatewayConfig gateway = new GatewayConfig();
    private LoadBalancerConfig loadbalancer = new LoadBalancerConfig();
    private RateLimiterConfig ratelimiter = new RateLimiterConfig();
    private CircuitBreakerConfig circuitbreaker = new CircuitBreakerConfig();
    private SecurityConfig security = new SecurityConfig();
    private TelemetryConfig telemetry = new TelemetryConfig();

    public GatewayConfig getGateway() { return gateway; }
    public void setGateway(GatewayConfig gateway) { this.gateway = gateway; }

    public LoadBalancerConfig getLoadbalancer() { return loadbalancer; }
    public void setLoadbalancer(LoadBalancerConfig loadbalancer) { this.loadbalancer = loadbalancer; }

    public RateLimiterConfig getRatelimiter() { return ratelimiter; }
    public void setRatelimiter(RateLimiterConfig ratelimiter) { this.ratelimiter = ratelimiter; }

    public CircuitBreakerConfig getCircuitbreaker() { return circuitbreaker; }
    public void setCircuitbreaker(CircuitBreakerConfig circuitbreaker) { this.circuitbreaker = circuitbreaker; }

    public SecurityConfig getSecurity() { return security; }
    public void setSecurity(SecurityConfig security) { this.security = security; }

    public TelemetryConfig getTelemetry() { return telemetry; }
    public void setTelemetry(TelemetryConfig telemetry) { this.telemetry = telemetry; }

    public static class GatewayConfig {
        private boolean enabled = true;
        private int port = 8080;
        private int adminPort = 8081;
        private int maxHeaderSizeBytes = 16384;
        private int maxBodySizeBytes = 10485760;
        private int connectTimeoutMs = 3000;
        private int responseTimeoutMs = 10000;
        private ConnectionPoolConfig pool = new ConnectionPoolConfig();

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getPort() { return port; }
        public void setPort(int port) { this.port = port; }
        public int getAdminPort() { return adminPort; }
        public void setAdminPort(int adminPort) { this.adminPort = adminPort; }
        public int getMaxHeaderSizeBytes() { return maxHeaderSizeBytes; }
        public void setMaxHeaderSizeBytes(int maxHeaderSizeBytes) { this.maxHeaderSizeBytes = maxHeaderSizeBytes; }
        public int getMaxBodySizeBytes() { return maxBodySizeBytes; }
        public void setMaxBodySizeBytes(int maxBodySizeBytes) { this.maxBodySizeBytes = maxBodySizeBytes; }
        public int getConnectTimeoutMs() { return connectTimeoutMs; }
        public void setConnectTimeoutMs(int connectTimeoutMs) { this.connectTimeoutMs = connectTimeoutMs; }
        public int getResponseTimeoutMs() { return responseTimeoutMs; }
        public void setResponseTimeoutMs(int responseTimeoutMs) { this.responseTimeoutMs = responseTimeoutMs; }
        public ConnectionPoolConfig getPool() { return pool; }
        public void setPool(ConnectionPoolConfig pool) { this.pool = pool; }
    }

    public static class ConnectionPoolConfig {
        private int maxConnections = 1000;
        private int maxIdleTimeMs = 30000;
        private int maxLifeTimeMs = 60000;
        private int acquireTimeoutMs = 2000;

        public int getMaxConnections() { return maxConnections; }
        public void setMaxConnections(int maxConnections) { this.maxConnections = maxConnections; }
        public int getMaxIdleTimeMs() { return maxIdleTimeMs; }
        public void setMaxIdleTimeMs(int maxIdleTimeMs) { this.maxIdleTimeMs = maxIdleTimeMs; }
        public int getMaxLifeTimeMs() { return maxLifeTimeMs; }
        public void setMaxLifeTimeMs(int maxLifeTimeMs) { this.maxLifeTimeMs = maxLifeTimeMs; }
        public int getAcquireTimeoutMs() { return acquireTimeoutMs; }
        public void setAcquireTimeoutMs(int acquireTimeoutMs) { this.acquireTimeoutMs = acquireTimeoutMs; }
    }

    public static class LoadBalancerConfig {
        private String defaultStrategy = "ROUND_ROBIN";
        private HealthCheckConfig healthCheck = new HealthCheckConfig();

        public String getDefaultStrategy() { return defaultStrategy; }
        public void setDefaultStrategy(String defaultStrategy) { this.defaultStrategy = defaultStrategy; }
        public HealthCheckConfig getHealthCheck() { return healthCheck; }
        public void setHealthCheck(HealthCheckConfig healthCheck) { this.healthCheck = healthCheck; }
    }

    public static class HealthCheckConfig {
        private boolean enabled = true;
        private int intervalSeconds = 10;
        private int timeoutMs = 2000;
        private int healthyThreshold = 2;
        private int unhealthyThreshold = 3;
        private boolean passiveEnabled = true;
        private int passiveFailureThreshold = 5;
        private int passiveWindowSeconds = 10;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getIntervalSeconds() { return intervalSeconds; }
        public void setIntervalSeconds(int intervalSeconds) { this.intervalSeconds = intervalSeconds; }
        public int getTimeoutMs() { return timeoutMs; }
        public void setTimeoutMs(int timeoutMs) { this.timeoutMs = timeoutMs; }
        public int getHealthyThreshold() { return healthyThreshold; }
        public void setHealthyThreshold(int healthyThreshold) { this.healthyThreshold = healthyThreshold; }
        public int getUnhealthyThreshold() { return unhealthyThreshold; }
        public void setUnhealthyThreshold(int unhealthyThreshold) { this.unhealthyThreshold = unhealthyThreshold; }
        public boolean isPassiveEnabled() { return passiveEnabled; }
        public void setPassiveEnabled(boolean passiveEnabled) { this.passiveEnabled = passiveEnabled; }
        public int getPassiveFailureThreshold() { return passiveFailureThreshold; }
        public void setPassiveFailureThreshold(int passiveFailureThreshold) { this.passiveFailureThreshold = passiveFailureThreshold; }
        public int getPassiveWindowSeconds() { return passiveWindowSeconds; }
        public void setPassiveWindowSeconds(int passiveWindowSeconds) { this.passiveWindowSeconds = passiveWindowSeconds; }
    }

    public static class RateLimiterConfig {
        private boolean enabled = true;
        private String defaultAlgorithm = "TOKEN_BUCKET";
        private String redisKeyPrefix = "smartproxy:rl:";
        private String fallbackMode = "FAIL_OPEN";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getDefaultAlgorithm() { return defaultAlgorithm; }
        public void setDefaultAlgorithm(String defaultAlgorithm) { this.defaultAlgorithm = defaultAlgorithm; }
        public String getRedisKeyPrefix() { return redisKeyPrefix; }
        public void setRedisKeyPrefix(String redisKeyPrefix) { this.redisKeyPrefix = redisKeyPrefix; }
        public String getFallbackMode() { return fallbackMode; }
        public void setFallbackMode(String fallbackMode) { this.fallbackMode = fallbackMode; }
    }

    public static class CircuitBreakerConfig {
        private boolean enabled = true;
        private int slidingWindowSize = 20;
        private int minimumNumberOfCalls = 10;
        private double failureRateThresholdPercentage = 50.0;
        private double slowCallRateThresholdPercentage = 50.0;
        private int slowCallDurationThresholdMs = 2000;
        private int waitDurationInOpenStateSec = 15;
        private int permittedCallsInHalfOpenState = 5;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public int getSlidingWindowSize() { return slidingWindowSize; }
        public void setSlidingWindowSize(int slidingWindowSize) { this.slidingWindowSize = slidingWindowSize; }
        public int getMinimumNumberOfCalls() { return minimumNumberOfCalls; }
        public void setMinimumNumberOfCalls(int minimumNumberOfCalls) { this.minimumNumberOfCalls = minimumNumberOfCalls; }
        public double getFailureRateThresholdPercentage() { return failureRateThresholdPercentage; }
        public void setFailureRateThresholdPercentage(double failureRateThresholdPercentage) { this.failureRateThresholdPercentage = failureRateThresholdPercentage; }
        public double getSlowCallRateThresholdPercentage() { return slowCallRateThresholdPercentage; }
        public void setSlowCallRateThresholdPercentage(double slowCallRateThresholdPercentage) { this.slowCallRateThresholdPercentage = slowCallRateThresholdPercentage; }
        public int getSlowCallDurationThresholdMs() { return slowCallDurationThresholdMs; }
        public void setSlowCallDurationThresholdMs(int slowCallDurationThresholdMs) { this.slowCallDurationThresholdMs = slowCallDurationThresholdMs; }
        public int getWaitDurationInOpenStateSec() { return waitDurationInOpenStateSec; }
        public void setWaitDurationInOpenStateSec(int waitDurationInOpenStateSec) { this.waitDurationInOpenStateSec = waitDurationInOpenStateSec; }
        public int getPermittedCallsInHalfOpenState() { return permittedCallsInHalfOpenState; }
        public void setPermittedCallsInHalfOpenState(int permittedCallsInHalfOpenState) { this.permittedCallsInHalfOpenState = permittedCallsInHalfOpenState; }
    }

    public static class SecurityConfig {
        private JwtConfig jwt = new JwtConfig();
        private ApiKeyConfig apiKey = new ApiKeyConfig();
        private FirewallConfig firewall = new FirewallConfig();
        private AdminAuthConfig admin = new AdminAuthConfig();

        public JwtConfig getJwt() { return jwt; }
        public void setJwt(JwtConfig jwt) { this.jwt = jwt; }
        public ApiKeyConfig getApiKey() { return apiKey; }
        public void setApiKey(ApiKeyConfig apiKey) { this.apiKey = apiKey; }
        public FirewallConfig getFirewall() { return firewall; }
        public void setFirewall(FirewallConfig firewall) { this.firewall = firewall; }
        public AdminAuthConfig getAdmin() { return admin; }
        public void setAdmin(AdminAuthConfig admin) { this.admin = admin; }
    }

    public static class AdminAuthConfig {
        private boolean enabled = true;
        private String adminKey = "smartproxy-admin-super-secret-key-2026";
        private String headerName = "X-Admin-Key";

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getAdminKey() { return adminKey; }
        public void setAdminKey(String adminKey) { this.adminKey = adminKey; }
        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
    }

    public static class JwtConfig {
        private String secret;
        private long expirationMs = 86400000;

        public String getSecret() { return secret; }
        public void setSecret(String secret) { this.secret = secret; }
        public long getExpirationMs() { return expirationMs; }
        public void setExpirationMs(long expirationMs) { this.expirationMs = expirationMs; }
    }

    public static class ApiKeyConfig {
        private String headerName = "X-API-Key";
        private int cacheTtlMinutes = 10;

        public String getHeaderName() { return headerName; }
        public void setHeaderName(String headerName) { this.headerName = headerName; }
        public int getCacheTtlMinutes() { return cacheTtlMinutes; }
        public void setCacheTtlMinutes(int cacheTtlMinutes) { this.cacheTtlMinutes = cacheTtlMinutes; }
    }

    public static class FirewallConfig {
        private boolean ipFilteringEnabled = true;

        public boolean isIpFilteringEnabled() { return ipFilteringEnabled; }
        public void setIpFilteringEnabled(boolean ipFilteringEnabled) { this.ipFilteringEnabled = ipFilteringEnabled; }
    }

    public static class TelemetryConfig {
        private boolean enabled = true;
        private String exchangeName = "smartproxy.telemetry.exchange";
        private String accessLogQueue = "smartproxy.access.logs";
        private String alertsQueue = "smartproxy.security.alerts";
        private String metricsQueue = "smartproxy.metrics.stream";
        private double sampleRate = 1.0;

        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public String getExchangeName() { return exchangeName; }
        public void setExchangeName(String exchangeName) { this.exchangeName = exchangeName; }
        public String getAccessLogQueue() { return accessLogQueue; }
        public void setAccessLogQueue(String accessLogQueue) { this.accessLogQueue = accessLogQueue; }
        public String getAlertsQueue() { return alertsQueue; }
        public void setAlertsQueue(String alertsQueue) { this.alertsQueue = alertsQueue; }
        public String getMetricsQueue() { return metricsQueue; }
        public void setMetricsQueue(String metricsQueue) { this.metricsQueue = metricsQueue; }
        public double getSampleRate() { return sampleRate; }
        public void setSampleRate(double sampleRate) { this.sampleRate = sampleRate; }
    }
}
