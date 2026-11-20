-- Schema Initialization for Smart-Proxy Database
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- 1. Users and Admins Table
CREATE TABLE IF NOT EXISTS admin_users (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    username VARCHAR(100) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'ROLE_ADMIN',
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Upstream Clusters (Target Services)
CREATE TABLE IF NOT EXISTS upstream_clusters (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cluster_name VARCHAR(100) UNIQUE NOT NULL,
    load_balancer_strategy VARCHAR(50) NOT NULL DEFAULT 'ROUND_ROBIN', -- ROUND_ROBIN, WEIGHTED_ROUND_ROBIN, LEAST_CONNECTIONS, IP_HASH
    connect_timeout_ms INT DEFAULT 3000,
    read_timeout_ms INT DEFAULT 5000,
    health_check_path VARCHAR(255) DEFAULT '/health',
    health_check_interval_sec INT DEFAULT 10,
    unhealthy_threshold INT DEFAULT 3,
    healthy_threshold INT DEFAULT 2,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 3. Upstream Server Instances
CREATE TABLE IF NOT EXISTS upstream_instances (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cluster_id UUID NOT NULL REFERENCES upstream_clusters(id) ON DELETE CASCADE,
    host VARCHAR(255) NOT NULL,
    port INT NOT NULL,
    weight INT DEFAULT 1,
    status VARCHAR(50) NOT NULL DEFAULT 'HEALTHY', -- HEALTHY, UNHEALTHY, SUSPENDED, DRAINING
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(cluster_id, host, port)
);

-- 4. Circuit Breaker Configurations
CREATE TABLE IF NOT EXISTS circuit_breaker_configs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    sliding_window_size INT DEFAULT 20,
    minimum_number_of_calls INT DEFAULT 10,
    failure_rate_threshold FLOAT DEFAULT 50.0,
    slow_call_rate_threshold FLOAT DEFAULT 50.0,
    slow_call_duration_threshold_ms INT DEFAULT 2000,
    wait_duration_in_open_state_sec INT DEFAULT 15,
    permitted_calls_in_half_open_state INT DEFAULT 5,
    fallback_uri VARCHAR(255),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Rate Limit Policies
CREATE TABLE IF NOT EXISTS rate_limit_policies (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name VARCHAR(100) UNIQUE NOT NULL,
    algorithm VARCHAR(50) NOT NULL DEFAULT 'TOKEN_BUCKET', -- TOKEN_BUCKET, SLIDING_WINDOW_LOG, LEAKY_BUCKET
    replenish_rate INT NOT NULL DEFAULT 100, -- tokens per second or requests per window
    burst_capacity INT NOT NULL DEFAULT 200, -- max bucket capacity
    window_duration_sec INT NOT NULL DEFAULT 1,
    limit_by VARCHAR(50) NOT NULL DEFAULT 'IP', -- IP, API_KEY, USER, ROUTE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Dynamic Route Definitions
CREATE TABLE IF NOT EXISTS gateway_routes (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    route_id VARCHAR(100) UNIQUE NOT NULL,
    path_pattern VARCHAR(255) NOT NULL, -- e.g. /api/v1/users/**
    methods VARCHAR(100) DEFAULT 'GET,POST,PUT,DELETE,PATCH,OPTIONS',
    strip_prefix_count INT DEFAULT 0,
    cluster_id UUID REFERENCES upstream_clusters(id) ON DELETE SET NULL,
    rate_limit_policy_id UUID REFERENCES rate_limit_policies(id) ON DELETE SET NULL,
    circuit_breaker_config_id UUID REFERENCES circuit_breaker_configs(id) ON DELETE SET NULL,
    auth_required BOOLEAN DEFAULT FALSE,
    cors_enabled BOOLEAN DEFAULT TRUE,
    priority INT DEFAULT 0,
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. API Keys for Consumer Authentication
CREATE TABLE IF NOT EXISTS api_keys (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    key_hash VARCHAR(255) UNIQUE NOT NULL,
    key_prefix VARCHAR(16) NOT NULL, -- e.g. sp_live_...
    consumer_name VARCHAR(100) NOT NULL,
    role VARCHAR(50) DEFAULT 'STANDARD_CONSUMER',
    rate_limit_policy_id UUID REFERENCES rate_limit_policies(id) ON DELETE SET NULL,
    enabled BOOLEAN DEFAULT TRUE,
    expires_at TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 8. IP Access Control (Firewall Whitelist/Blacklist)
CREATE TABLE IF NOT EXISTS ip_rules (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    cidr_block VARCHAR(64) NOT NULL,
    rule_type VARCHAR(20) NOT NULL, -- ALLOW, DENY
    description VARCHAR(255),
    enabled BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 9. Audit and Access Logs (Aggregated from RabbitMQ)
CREATE TABLE IF NOT EXISTS gateway_access_logs (
    id UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    trace_id VARCHAR(64) NOT NULL,
    client_ip VARCHAR(64) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    request_path VARCHAR(1024) NOT NULL,
    status_code INT NOT NULL,
    duration_ms INT NOT NULL,
    route_id VARCHAR(100),
    upstream_instance VARCHAR(255),
    error_message TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_access_logs_created_at ON gateway_access_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_access_logs_route_id ON gateway_access_logs(route_id);
CREATE INDEX IF NOT EXISTS idx_access_logs_status_code ON gateway_access_logs(status_code);

-- Seed Initial Data for Local Development
INSERT INTO admin_users (username, password_hash, role)
VALUES ('admin', '$2a$12$e8Y43vjCq0o6rQ3f3G3V2uM6Q6/u5r4Wb0p0kYqP9fXyQyP9fXyQy', 'ROLE_ADMIN')
ON CONFLICT (username) DO NOTHING;

-- Seed Default Rate Limit Policies
INSERT INTO rate_limit_policies (name, algorithm, replenish_rate, burst_capacity, window_duration_sec, limit_by)
VALUES 
('DEFAULT_IP_LIMIT', 'TOKEN_BUCKET', 50, 100, 1, 'IP'),
('PREMIUM_API_KEY_LIMIT', 'SLIDING_WINDOW_LOG', 1000, 2000, 60, 'API_KEY')
ON CONFLICT (name) DO NOTHING;

-- Seed Default Circuit Breaker Config
INSERT INTO circuit_breaker_configs (name, sliding_window_size, minimum_number_of_calls, failure_rate_threshold, slow_call_rate_threshold, slow_call_duration_threshold_ms, wait_duration_in_open_state_sec, permitted_calls_in_half_open_state)
VALUES ('DEFAULT_CB', 20, 10, 50.0, 50.0, 2000, 15, 5)
ON CONFLICT (name) DO NOTHING;

-- Seed Default Upstream Cluster for Mock Backends
INSERT INTO upstream_clusters (id, cluster_name, load_balancer_strategy, connect_timeout_ms, read_timeout_ms, health_check_path)
VALUES ('11111111-1111-1111-1111-111111111111', 'mock-service-cluster', 'ROUND_ROBIN', 3000, 5000, '/health')
ON CONFLICT (cluster_name) DO NOTHING;

INSERT INTO upstream_instances (cluster_id, host, port, weight, status)
VALUES 
('11111111-1111-1111-1111-111111111111', 'mock-upstream-1', 8091, 1, 'HEALTHY'),
('11111111-1111-1111-1111-111111111111', 'mock-upstream-2', 8092, 2, 'HEALTHY'),
('11111111-1111-1111-1111-111111111111', 'mock-upstream-3', 8093, 1, 'HEALTHY')
ON CONFLICT DO NOTHING;

-- Seed Route for Mock Service
INSERT INTO gateway_routes (route_id, path_pattern, cluster_id, rate_limit_policy_id, circuit_breaker_config_id, auth_required)
VALUES 
('mock-service-route', '/api/mock/**', '11111111-1111-1111-1111-111111111111', 
 (SELECT id FROM rate_limit_policies WHERE name = 'DEFAULT_IP_LIMIT'),
 (SELECT id FROM circuit_breaker_configs WHERE name = 'DEFAULT_CB'),
 FALSE)
ON CONFLICT (route_id) DO NOTHING;
