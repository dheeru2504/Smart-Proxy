export interface OverviewData {
  activeVersion: number;
  propagationLagMs: number;
  checksum: string;
  routeCount: number;
  serviceCount: number;
  totalInstances: number;
  healthyInstances: number;
  uptimeSeconds: number;
}

export interface GatewayNode {
  nodeId: string;
  host: string;
  port: number;
  status: string;
  version: number;
  uptimeSeconds: number;
  heapUsedMb: number;
  heapMaxMb: number;
  activeThreads: number;
}

export interface ServiceEntity {
  id: string;
  serviceId: string;
  name: string;
  description: string;
  loadBalancingStrategy: string;
  healthCheckIntervalSec: number;
  healthCheckTimeoutMs: number;
  healthyThreshold: number;
  unhealthyThreshold: number;
  instances?: ServiceInstance[];
}

export interface ServiceInstance {
  id: string;
  serviceId: string;
  host: string;
  port: number;
  weight: number;
  status: string;
  activeConnections?: number;
  totalRequests?: number;
  totalFailures?: number;
  consecutiveSuccesses?: number;
  consecutiveFailures?: number;
}

export interface RouteEntity {
  id: string;
  routeId: string;
  pathPattern: string;
  methods: string;
  service?: ServiceEntity;
  stripPrefixCount: number;
  authRequired: boolean;
  connectTimeoutMs: number;
  responseTimeoutMs: number;
  priority: number;
  enabled: boolean;
  version: number;
}

export interface RateLimitPolicy {
  id: string;
  policyName: string;
  algorithmType: string;
  capacity: number;
  refillRate: number;
  windowSizeSec: number;
  limitDimension: string;
  enabled: boolean;
}

export interface CircuitBreakerItem {
  name: string;
  state: 'CLOSED' | 'OPEN' | 'HALF_OPEN';
  config: {
    slidingWindowSize: number;
    minimumNumberOfCalls: number;
    failureRateThresholdPercentage: number;
    slowCallRateThresholdPercentage: number;
    waitDurationInOpenStateSec: number;
    permittedCallsInHalfOpenState: number;
  };
}

export interface ConfigStatus {
  activeVersion: number;
  publishedAt: string;
  appliedAt: string;
  propagationLagMs: number;
  routeCount: number;
  serviceCount: number;
  checksum: string;
  status: string;
}

export interface AuditLog {
  id: string;
  timestamp: string;
  traceId: string;
  method: string;
  path: string;
  status: number;
  durationMs: number;
  clientIp: string;
  serviceId: string;
}

const API_BASE = '';

const ADMIN_HEADERS = {
  'Content-Type': 'application/json',
  'X-Admin-Key': 'admin-super-secret-key-change-in-prod'
};

export async function fetchOverview(): Promise<OverviewData> {
  const res = await fetch(`${API_BASE}/api/admin/telemetry/overview`, { headers: ADMIN_HEADERS });
  if (!res.ok) throw new Error('Failed to fetch telemetry overview');
  return res.json();
}

export async function fetchNodes(): Promise<GatewayNode[]> {
  const res = await fetch(`${API_BASE}/api/admin/telemetry/nodes`, { headers: ADMIN_HEADERS });
  if (!res.ok) throw new Error('Failed to fetch gateway nodes');
  return res.json();
}

export async function fetchServices(): Promise<ServiceEntity[]> {
  const res = await fetch(`${API_BASE}/api/admin/services`, { headers: ADMIN_HEADERS });
  if (!res.ok) return [];
  return res.json();
}

export async function fetchRoutes(): Promise<RouteEntity[]> {
  const res = await fetch(`${API_BASE}/api/admin/routes`, { headers: ADMIN_HEADERS });
  if (!res.ok) return [];
  return res.json();
}

export async function createRoute(route: Partial<RouteEntity>): Promise<RouteEntity> {
  const res = await fetch(`${API_BASE}/api/admin/routes`, {
    method: 'POST',
    headers: ADMIN_HEADERS,
    body: JSON.stringify(route)
  });
  if (!res.ok) throw new Error('Failed to create route');
  return res.json();
}

export async function deleteRoute(id: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/routes/${id}`, {
    method: 'DELETE',
    headers: ADMIN_HEADERS
  });
  if (!res.ok) throw new Error('Failed to delete route');
}

export async function fetchRatePolicies(): Promise<RateLimitPolicy[]> {
  const res = await fetch(`${API_BASE}/api/admin/rate-limits`, { headers: ADMIN_HEADERS });
  if (!res.ok) return [];
  return res.json();
}

export async function fetchCircuitBreakers(): Promise<CircuitBreakerItem[]> {
  const res = await fetch(`${API_BASE}/api/admin/telemetry/circuit-breakers`, { headers: ADMIN_HEADERS });
  if (!res.ok) return [];
  return res.json();
}

export async function transitionCircuitBreaker(name: string, state: string): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/telemetry/circuit-breakers/${name}/transition?state=${state}`, {
    method: 'POST',
    headers: ADMIN_HEADERS
  });
  if (!res.ok) throw new Error('Failed to transition circuit breaker');
}

export async function fetchHealthInstances(): Promise<ServiceInstance[]> {
  const res = await fetch(`${API_BASE}/api/admin/telemetry/health/instances`, { headers: ADMIN_HEADERS });
  if (!res.ok) return [];
  return res.json();
}

export async function fetchConfigStatus(): Promise<ConfigStatus> {
  const res = await fetch(`${API_BASE}/api/admin/config/snapshot-status`, { headers: ADMIN_HEADERS });
  if (!res.ok) throw new Error('Failed to fetch config snapshot status');
  return res.json();
}

export async function triggerForceSync(): Promise<void> {
  const res = await fetch(`${API_BASE}/api/admin/config/sync?reason=Dashboard+Manual+Trigger`, {
    method: 'POST',
    headers: ADMIN_HEADERS
  });
  if (!res.ok) throw new Error('Failed to trigger config sync');
}
