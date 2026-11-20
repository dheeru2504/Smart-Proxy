-- Flyway Database Migration V1: Core Schema for Smart-Proxy

CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Configuration Versions (Tracks global and per-component version numbers)
CREATE TABLE IF NOT EXISTS configuration_versions (
    component VARCHAR(50) PRIMARY KEY,
    current_version BIGINT NOT NULL DEFAULT 1,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    comment VARCHAR(255)
);

-- Initialize version records
INSERT INTO configuration_versions (component, current_version, comment) VALUES
('ROUTES', 1, 'Initial route configuration snapshot'),
('SERVICES', 1, 'Initial upstream services snapshot'),
('RATE_LIMIT_POLICIES', 1, 'Initial rate limiting policies snapshot'),
('API_CLIENTS', 1, 'Initial API client credentials snapshot')
ON CONFLICT (component) DO NOTHING;

-- 2. Upstream Services
CREATE TABLE IF NOT EXISTS services (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_id VARCHAR(100) UNIQUE NOT NULL,
    name VARCHAR(100) NOT NULL,
    load_balancer_strategy VARCHAR(50) NOT NULL DEFAULT 'ROUND_ROBIN',
    connect_timeout_ms INT DEFAULT 3000,
    read_timeout_ms INT DEFAULT 5000,
    health_check_path VARCHAR(255) DEFAULT '/health',
    health_check_interval_sec INT DEFAULT 10,
    unhealthy_threshold INT DEFAULT 3,
    healthy_threshold INT DEFAULT 2,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Service Instances
CREATE TABLE IF NOT EXISTS service_instances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    service_id UUID NOT NULL REFERENCES services(id) ON DELETE CASCADE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    weight INT DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'HEALTHY',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(service_id, host, port)
);

-- 4. Rate Limit Policies
CREATE TABLE IF NOT EXISTS rate_limit_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    policy_name VARCHAR(100) UNIQUE NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'TOKEN_BUCKET',
    replenish_rate INT NOT NULL DEFAULT 100,
    burst_capacity INT NOT NULL DEFAULT 200,
    window_duration_sec INT NOT NULL DEFAULT 1,
    limit_by VARCHAR(50) NOT NULL DEFAULT 'IP',
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Gateway Routes
CREATE TABLE IF NOT EXISTS routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_id VARCHAR(100) UNIQUE NOT NULL,
    path_pattern VARCHAR(255) NOT NULL,
    methods VARCHAR(100) DEFAULT '*',
    service_id UUID REFERENCES services(id) ON DELETE SET NULL,
    strip_prefix_count INT DEFAULT 0,
    auth_required BOOLEAN DEFAULT FALSE,
    connect_timeout_ms INT DEFAULT 3000,
    response_timeout_ms INT DEFAULT 10000,
    rate_limit_policy_id UUID REFERENCES rate_limit_policies(id) ON DELETE SET NULL,
    priority INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. API Clients
CREATE TABLE IF NOT EXISTS api_clients (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    client_id VARCHAR(100) UNIQUE NOT NULL,
    client_name VARCHAR(100) NOT NULL,
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(16) NOT NULL,
    role VARCHAR(50) DEFAULT 'STANDARD_CONSUMER',
    rate_limit_policy_id UUID REFERENCES rate_limit_policies(id) ON DELETE SET NULL,
    enabled BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Seed Default Rate Limit Policy
INSERT INTO rate_limit_policies (id, policy_name, algorithm, replenish_rate, burst_capacity, window_duration_sec, limit_by, version)
VALUES ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'DEFAULT_IP_LIMIT', 'TOKEN_BUCKET', 100, 200, 1, 'IP', 0)
ON CONFLICT (policy_name) DO NOTHING;

-- Seed Default Mock Upstream Service
INSERT INTO services (id, service_id, name, load_balancer_strategy, connect_timeout_ms, read_timeout_ms, health_check_path, version)
VALUES ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'mock-service', 'Mock Backend Upstream Service', 'ROUND_ROBIN', 3000, 5000, '/health', 0)
ON CONFLICT (service_id) DO NOTHING;

-- Seed Default Route for Mock Service
INSERT INTO routes (route_id, path_pattern, methods, service_id, strip_prefix_count, auth_required, rate_limit_policy_id, priority, enabled, version)
VALUES 
('mock-service-route', '/api/mock/**', '*', 'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 0, FALSE, 'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 0, TRUE, 0)
ON CONFLICT (route_id) DO NOTHING;
