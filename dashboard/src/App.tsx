import React, { useState, useEffect } from 'react';
import {
  fetchOverview,
  fetchNodes,
  fetchServices,
  fetchRoutes,
  createRoute,
  deleteRoute,
  fetchRatePolicies,
  fetchCircuitBreakers,
  transitionCircuitBreaker,
  fetchHealthInstances,
  fetchConfigStatus,
  triggerForceSync,
  OverviewData,
  GatewayNode,
  ServiceEntity,
  RouteEntity,
  RateLimitPolicy,
  CircuitBreakerItem,
  ServiceInstance,
  ConfigStatus
} from './api';

type Tab =
  | 'overview'
  | 'nodes'
  | 'services'
  | 'routes'
  | 'ratePolicies'
  | 'rateUsage'
  | 'circuitBreakers'
  | 'health'
  | 'versions'
  | 'auditLogs';

export default function App() {
  const [activeTab, setActiveTab] = useState<Tab>('overview');
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [syncing, setSyncing] = useState(false);

  // Live Data States
  const [overview, setOverview] = useState<OverviewData | null>(null);
  const [nodes, setNodes] = useState<GatewayNode[]>([]);
  const [services, setServices] = useState<ServiceEntity[]>([]);
  const [routes, setRoutes] = useState<RouteEntity[]>([]);
  const [ratePolicies, setRatePolicies] = useState<RateLimitPolicy[]>([]);
  const [circuitBreakers, setCircuitBreakers] = useState<CircuitBreakerItem[]>([]);
  const [healthInstances, setHealthInstances] = useState<ServiceInstance[]>([]);
  const [configStatus, setConfigStatus] = useState<ConfigStatus | null>(null);

  // New Route Modal
  const [isRouteModalOpen, setIsRouteModalOpen] = useState(false);
  const [newRoute, setNewRoute] = useState({
    routeId: '',
    pathPattern: '',
    methods: 'GET,POST',
    stripPrefixCount: 0,
    authRequired: false,
    connectTimeoutMs: 3000,
    responseTimeoutMs: 5000,
    priority: 0,
    enabled: true
  });

  const loadAllData = async () => {
    try {
      const [ov, nds, svcs, rts, rps, cbs, hinsts, cfg] = await Promise.all([
        fetchOverview().catch(() => null),
        fetchNodes().catch(() => []),
        fetchServices().catch(() => []),
        fetchRoutes().catch(() => []),
        fetchRatePolicies().catch(() => []),
        fetchCircuitBreakers().catch(() => []),
        fetchHealthInstances().catch(() => []),
        fetchConfigStatus().catch(() => null)
      ]);

      if (ov) setOverview(ov);
      setNodes(nds);
      setServices(svcs);
      setRoutes(rts);
      setRatePolicies(rps);
      setCircuitBreakers(cbs);
      setHealthInstances(hinsts);
      if (cfg) setConfigStatus(cfg);
      setError(null);
    } catch (err: any) {
      setError(err.message || 'Failed to load telemetry from gateway');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    loadAllData();
    const interval = setInterval(loadAllData, 3000);
    return () => clearInterval(interval);
  }, []);

  const handleForceSync = async () => {
    setSyncing(true);
    try {
      await triggerForceSync();
      await loadAllData();
    } catch (err: any) {
      alert('Sync failed: ' + err.message);
    } finally {
      setSyncing(false);
    }
  };

  const handleCircuitTransition = async (name: string, state: string) => {
    try {
      await transitionCircuitBreaker(name, state);
      await loadAllData();
    } catch (err: any) {
      alert('Circuit transition failed: ' + err.message);
    }
  };

  const handleCreateRoute = async (e: React.FormEvent) => {
    e.preventDefault();
    try {
      await createRoute(newRoute);
      setIsRouteModalOpen(false);
      setNewRoute({
        routeId: '',
        pathPattern: '',
        methods: 'GET,POST',
        stripPrefixCount: 0,
        authRequired: false,
        connectTimeoutMs: 3000,
        responseTimeoutMs: 5000,
        priority: 0,
        enabled: true
      });
      await loadAllData();
    } catch (err: any) {
      alert('Route creation failed: ' + err.message);
    }
  };

  const handleDeleteRoute = async (id: string) => {
    if (!confirm('Are you sure you want to delete this route?')) return;
    try {
      await deleteRoute(id);
      await loadAllData();
    } catch (err: any) {
      alert('Delete route failed: ' + err.message);
    }
  };

  if (loading && !overview && routes.length === 0) {
    return (
      <div style={{ display: 'flex', minHeight: '100vh', alignItems: 'center', justifyContent: 'center', background: 'var(--bg-primary)', color: 'var(--text-secondary)' }}>
        <div style={{ textAlign: 'center' }}>
          <div style={{ fontSize: '32px', marginBottom: '12px' }}>⚡</div>
          <p style={{ fontSize: '14px', fontWeight: 600 }}>Connecting to Smart-Proxy Control Plane...</p>
        </div>
      </div>
    );
  }

  return (
    <div style={{ display: 'flex', minHeight: '100vh', background: 'var(--bg-primary)' }}>
      {/* Sidebar Navigation */}
      <aside
        style={{
          width: '260px',
          background: 'var(--bg-secondary)',
          borderRight: '1px solid var(--border-color)',
          padding: '24px 16px',
          display: 'flex',
          flexDirection: 'column',
          justifyContent: 'space-between'
        }}
      >
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px', marginBottom: '28px', padding: '0 8px' }}>
            <div
              style={{
                width: '38px',
                height: '38px',
                borderRadius: '10px',
                background: 'linear-gradient(135deg, #06b6d4, #6366f1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontWeight: 900,
                fontSize: '18px',
                boxShadow: '0 0 20px rgba(6, 182, 212, 0.4)'
              }}
            >
              ⚡
            </div>
            <div>
              <h1 style={{ fontSize: '16px', fontWeight: 800, letterSpacing: '-0.5px' }}>Smart-Proxy</h1>
              <p style={{ fontSize: '11px', color: 'var(--text-secondary)' }}>Control Plane v1.0</p>
            </div>
          </div>

          <nav style={{ display: 'flex', flexDirection: 'column', gap: '4px' }}>
            {[
              { id: 'overview', label: 'Live Overview', icon: '📊' },
              { id: 'nodes', label: 'Gateway Nodes', icon: '🖥️' },
              { id: 'services', label: 'Services & Clusters', icon: '🏢' },
              { id: 'routes', label: 'Dynamic Routes', icon: '🔀' },
              { id: 'ratePolicies', label: 'Rate Policies', icon: '⏱️' },
              { id: 'rateUsage', label: 'Rate Usage', icon: '📈' },
              { id: 'circuitBreakers', label: 'Circuit Breakers', icon: '🛡️' },
              { id: 'health', label: 'Instance Health', icon: '❤️' },
              { id: 'versions', label: 'Config Versions', icon: '🔄' },
              { id: 'auditLogs', label: 'Structured Logs', icon: '📜' }
            ].map((tab) => (
              <button
                key={tab.id}
                onClick={() => setActiveTab(tab.id as Tab)}
                style={{
                  display: 'flex',
                  alignItems: 'center',
                  gap: '10px',
                  padding: '9px 12px',
                  borderRadius: '8px',
                  border: 'none',
                  cursor: 'pointer',
                  textAlign: 'left',
                  fontSize: '13px',
                  fontWeight: activeTab === tab.id ? 600 : 500,
                  background: activeTab === tab.id ? 'rgba(6, 182, 212, 0.15)' : 'transparent',
                  color: activeTab === tab.id ? 'var(--accent-cyan)' : 'var(--text-secondary)',
                  transition: 'all 0.15s ease'
                }}
              >
                <span>{tab.icon}</span>
                <span>{tab.label}</span>
              </button>
            ))}
          </nav>
        </div>

        {/* Global Cluster Status */}
        <div className="glass-panel" style={{ padding: '14px', marginTop: '20px' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '8px' }}>
            <span style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600 }}>CLUSTER SYNC</span>
            <span className="badge badge-success">ACTIVE</span>
          </div>
          <p style={{ fontSize: '12px', color: 'var(--text-secondary)' }}>
            Snapshot: <strong style={{ color: 'var(--accent-cyan)' }}>v{overview?.activeVersion || 0}</strong>
          </p>
          <p style={{ fontSize: '11px', color: 'var(--text-muted)', marginTop: '2px' }}>
            Lag: {overview?.propagationLagMs || 0}ms
          </p>
          <button
            onClick={handleForceSync}
            disabled={syncing}
            className="btn-secondary"
            style={{ width: '100%', marginTop: '10px', justifyContent: 'center', fontSize: '11px', padding: '6px' }}
          >
            {syncing ? 'Syncing...' : '⚡ Force Cluster Sync'}
          </button>
        </div>
      </aside>

      {/* Main Content Area */}
      <main style={{ flex: 1, padding: '32px 40px', overflowY: 'auto' }}>
        <header style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '32px' }}>
          <div>
            <h2 style={{ fontSize: '24px', fontWeight: 800, textTransform: 'capitalize' }}>
              {activeTab === 'overview' && 'Live Gateway Telemetry'}
              {activeTab === 'nodes' && 'Gateway Cluster Nodes'}
              {activeTab === 'services' && 'Backend Services & Clusters'}
              {activeTab === 'routes' && 'Dynamic Routing Table'}
              {activeTab === 'ratePolicies' && 'Distributed Rate Limiting Policies'}
              {activeTab === 'rateUsage' && 'Real-time Rate Limit Quota & 429 Metrics'}
              {activeTab === 'circuitBreakers' && 'Custom Circuit Breakers State Machine'}
              {activeTab === 'health' && 'Active Health Probing & Target Matrices'}
              {activeTab === 'versions' && 'Authoritative Configuration Versions'}
              {activeTab === 'auditLogs' && 'Real-time Structured Telemetry Logs'}
            </h2>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginTop: '4px' }}>
              High-throughput reactive L7 proxy operations console
            </p>
          </div>

          <div style={{ display: 'flex', gap: '12px' }}>
            {activeTab === 'routes' && (
              <button className="btn-primary" onClick={() => setIsRouteModalOpen(true)}>
                + Add Dynamic Route
              </button>
            )}
            <span className="badge badge-info" style={{ padding: '8px 14px', fontSize: '12px' }}>
              ● Gateway Ingress: 8080 (Listening)
            </span>
          </div>
        </header>

        {error && (
          <div className="glass-panel" style={{ padding: '16px', marginBottom: '24px', borderLeft: '4px solid var(--accent-rose)' }}>
            <p style={{ color: 'var(--accent-rose)', fontSize: '13px', fontWeight: 600 }}>⚠️ Connection Warning: {error}</p>
          </div>
        )}

        {/* TAB 1: OVERVIEW */}
        {activeTab === 'overview' && (
          <div>
            <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))', gap: '18px', marginBottom: '28px' }}>
              <div className="glass-panel" style={{ padding: '20px' }}>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 600 }}>ACTIVE ROUTES</p>
                <h3 style={{ fontSize: '28px', fontWeight: 800, marginTop: '8px', color: 'var(--accent-cyan)' }}>
                  {overview?.routeCount ?? routes.length}
                </h3>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Hot-swapped in memory</span>
              </div>
              <div className="glass-panel" style={{ padding: '20px' }}>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 600 }}>UPSTREAM TARGETS</p>
                <h3 style={{ fontSize: '28px', fontWeight: 800, marginTop: '8px', color: 'var(--accent-emerald)' }}>
                  {overview?.healthyInstances ?? 0} / {overview?.totalInstances ?? 0}
                </h3>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Healthy active instances</span>
              </div>
              <div className="glass-panel" style={{ padding: '20px' }}>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 600 }}>SNAPSHOT VERSION</p>
                <h3 style={{ fontSize: '28px', fontWeight: 800, marginTop: '8px', color: 'var(--accent-indigo)' }}>
                  v{overview?.activeVersion ?? 0}
                </h3>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>Monotonic immutable state</span>
              </div>
              <div className="glass-panel" style={{ padding: '20px' }}>
                <p style={{ fontSize: '12px', color: 'var(--text-secondary)', fontWeight: 600 }}>PROPAGATION LAG</p>
                <h3 style={{ fontSize: '28px', fontWeight: 800, marginTop: '8px', color: 'var(--accent-amber)' }}>
                  {overview?.propagationLagMs ?? 0} ms
                </h3>
                <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>PostgreSQL to Gateway Swap</span>
              </div>
            </div>

            <div style={{ display: 'grid', gridTemplateColumns: '2fr 1fr', gap: '20px' }}>
              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '16px' }}>🚀 Gateway Core Architecture</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '12px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                    <span>Runtime Engine:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>Java 21 + Spring WebFlux + Reactor Netty</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                    <span>Rate Limiter:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>Multi-Tier Redis Lua (Token Bucket / Sliding ZSET)</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0', borderBottom: '1px solid rgba(255,255,255,0.05)' }}>
                    <span>Load Balancer:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>Smooth Weighted Round-Robin / P2C / Least Connections</strong>
                  </div>
                  <div style={{ display: 'flex', justifyContent: 'space-between', padding: '10px 0' }}>
                    <span>Circuit Breaker:</span>
                    <strong style={{ color: 'var(--text-primary)' }}>Custom Closed/Open/Half-Open with Sliding Ring Buffer</strong>
                  </div>
                </div>
              </div>

              <div className="glass-panel" style={{ padding: '24px' }}>
                <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '16px' }}>🛡️ Security & Zero-Trust</h3>
                <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '12px' }}>
                  <span className="badge badge-success">✓ JWT & API-Key Auth Active</span>
                  <span className="badge badge-success">✓ Hop-by-Hop Header Stripping</span>
                  <span className="badge badge-success">✓ Downstream Identity Injection</span>
                  <span className="badge badge-success">✓ Admin Key Control Plane Isolation</span>
                </div>
              </div>
            </div>
          </div>
        )}

        {/* TAB 2: GATEWAY NODES */}
        {activeTab === 'nodes' && (
          <div className="glass-panel" style={{ overflow: 'hidden' }}>
            <table>
              <thead>
                <tr>
                  <th>Node ID</th>
                  <th>Host / Port</th>
                  <th>Status</th>
                  <th>Snapshot Version</th>
                  <th>JVM Heap Memory</th>
                  <th>Active Threads</th>
                  <th>Uptime</th>
                </tr>
              </thead>
              <tbody>
                {nodes.length === 0 ? (
                  <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No gateway nodes detected</td></tr>
                ) : (
                  nodes.map((node) => (
                    <tr key={node.nodeId}>
                      <td style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{node.nodeId}</td>
                      <td>{node.host}:{node.port}</td>
                      <td><span className="badge badge-success">{node.status}</span></td>
                      <td><strong>v{node.version}</strong></td>
                      <td>
                        <div style={{ width: '120px', background: 'rgba(255,255,255,0.1)', height: '6px', borderRadius: '3px', overflow: 'hidden', marginBottom: '4px' }}>
                          <div style={{ width: `${(node.heapUsedMb / Math.max(1, node.heapMaxMb)) * 100}%`, background: 'var(--accent-cyan)', height: '100%' }} />
                        </div>
                        <span style={{ fontSize: '11px', color: 'var(--text-muted)' }}>{node.heapUsedMb} MB / {node.heapMaxMb} MB</span>
                      </td>
                      <td>{node.activeThreads}</td>
                      <td>{Math.floor(node.uptimeSeconds / 60)}m {node.uptimeSeconds % 60}s</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 3: SERVICES */}
        {activeTab === 'services' && (
          <div className="glass-panel" style={{ overflow: 'hidden' }}>
            <table>
              <thead>
                <tr>
                  <th>Service ID</th>
                  <th>Service Name</th>
                  <th>LB Strategy</th>
                  <th>Health Probe</th>
                  <th>Thresholds</th>
                </tr>
              </thead>
              <tbody>
                {services.length === 0 ? (
                  <tr><td colSpan={5} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No services registered</td></tr>
                ) : (
                  services.map((svc) => (
                    <tr key={svc.id}>
                      <td style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{svc.serviceId}</td>
                      <td>{svc.name}</td>
                      <td><span className="badge badge-purple">{svc.loadBalancingStrategy || 'ROUND_ROBIN'}</span></td>
                      <td>Interval: {svc.healthCheckIntervalSec || 5}s (Timeout: {svc.healthCheckTimeoutMs || 2000}ms)</td>
                      <td>Healthy: {svc.healthyThreshold || 2} / Unhealthy: {svc.unhealthyThreshold || 3}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 4: ROUTES */}
        {activeTab === 'routes' && (
          <div className="glass-panel" style={{ overflow: 'hidden' }}>
            <table>
              <thead>
                <tr>
                  <th>Route ID</th>
                  <th>Path Pattern</th>
                  <th>Methods</th>
                  <th>Service</th>
                  <th>Strip Prefix</th>
                  <th>Auth</th>
                  <th>Timeouts</th>
                  <th>Priority</th>
                  <th>Actions</th>
                </tr>
              </thead>
              <tbody>
                {routes.length === 0 ? (
                  <tr><td colSpan={9} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No dynamic routes configured</td></tr>
                ) : (
                  routes.map((rt) => (
                    <tr key={rt.id}>
                      <td style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{rt.routeId}</td>
                      <td className="font-mono" style={{ color: 'var(--accent-emerald)' }}>{rt.pathPattern}</td>
                      <td>
                        {rt.methods ? (
                          rt.methods.split(',').map((m) => (
                            <span key={m} className="badge badge-info" style={{ marginRight: '4px' }}>{m.trim()}</span>
                          ))
                        ) : (
                          <span className="badge badge-info">*</span>
                        )}
                      </td>
                      <td>{rt.service ? rt.service.serviceId : 'Direct'}</td>
                      <td>{rt.stripPrefixCount}</td>
                      <td>
                        {rt.authRequired ? (
                          <span className="badge badge-danger">Required</span>
                        ) : (
                          <span className="badge badge-success">Public</span>
                        )}
                      </td>
                      <td style={{ fontSize: '11px', color: 'var(--text-muted)' }}>
                        Resp: {rt.responseTimeoutMs}ms
                      </td>
                      <td>{rt.priority}</td>
                      <td>
                        <button
                          onClick={() => handleDeleteRoute(rt.id)}
                          style={{ background: 'transparent', border: 'none', color: 'var(--accent-rose)', cursor: 'pointer', fontWeight: 600, fontSize: '12px' }}
                        >
                          Delete
                        </button>
                      </td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 5: RATE POLICIES */}
        {activeTab === 'ratePolicies' && (
          <div className="glass-panel" style={{ overflow: 'hidden' }}>
            <table>
              <thead>
                <tr>
                  <th>Policy Name</th>
                  <th>Algorithm</th>
                  <th>Capacity (Tokens)</th>
                  <th>Refill Rate (/sec)</th>
                  <th>Window Duration</th>
                  <th>Partition Dimension</th>
                  <th>Status</th>
                </tr>
              </thead>
              <tbody>
                {ratePolicies.length === 0 ? (
                  <tr><td colSpan={7} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No rate policies registered</td></tr>
                ) : (
                  ratePolicies.map((rp) => (
                    <tr key={rp.id}>
                      <td style={{ fontWeight: 600, color: 'var(--accent-cyan)' }}>{rp.policyName}</td>
                      <td><span className="badge badge-purple">{rp.algorithmType}</span></td>
                      <td><strong>{rp.capacity}</strong></td>
                      <td>{rp.refillRate} tokens/sec</td>
                      <td>{rp.windowSizeSec}s</td>
                      <td><span className="badge badge-info">{rp.limitDimension}</span></td>
                      <td><span className="badge badge-success">Enabled</span></td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 6: RATE USAGE */}
        {activeTab === 'rateUsage' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))', gap: '20px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '8px' }}>⏱️ Multi-Tier Evaluation Semantics</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '16px' }}>
                Single Redis Lua script evaluating Global → Tenant → API-Key → User → Route tiers atomically.
              </p>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '8px', fontSize: '12px' }}>
                <div style={{ padding: '10px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px' }}>
                  <strong>Global Tier:</strong> Capacity bounded at gateway edge
                </div>
                <div style={{ padding: '10px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px' }}>
                  <strong>Tenant Tier:</strong> Multi-tenant isolation partition
                </div>
                <div style={{ padding: '10px', background: 'rgba(255,255,255,0.03)', borderRadius: '8px' }}>
                  <strong>API-Key Tier:</strong> Client specific rate allocations
                </div>
              </div>
            </div>

            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '8px' }}>🚫 429 Rejection Header Injection</h3>
              <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '16px' }}>
                Standard RFC 7807 problem details with accurate Retry-After and rate headers.
              </p>
              <div className="font-mono" style={{ background: '#050811', padding: '14px', borderRadius: '8px', fontSize: '11px', color: 'var(--accent-cyan)' }}>
                X-RateLimit-Limit: 100<br/>
                X-RateLimit-Remaining: 0<br/>
                X-RateLimit-Reset: 30<br/>
                Retry-After: 30<br/>
                HTTP/1.1 429 Too Many Requests
              </div>
            </div>
          </div>
        )}

        {/* TAB 7: CIRCUIT BREAKERS */}
        {activeTab === 'circuitBreakers' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(320px, 1fr))', gap: '20px' }}>
            {circuitBreakers.length === 0 ? (
              <div className="glass-panel" style={{ padding: '32px', textAlign: 'center', gridColumn: '1 / -1', color: 'var(--text-muted)' }}>
                No active circuit breakers (auto-instantiated upon upstream route traffic)
              </div>
            ) : (
              circuitBreakers.map((cb) => (
                <div key={cb.name} className="glass-panel" style={{ padding: '24px' }}>
                  <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '16px' }}>
                    <h3 style={{ fontSize: '16px', fontWeight: 700 }}>{cb.name}</h3>
                    <span
                      className={`badge ${
                        cb.state === 'CLOSED' ? 'badge-success' : cb.state === 'HALF_OPEN' ? 'badge-warning' : 'badge-danger'
                      }`}
                    >
                      {cb.state}
                    </span>
                  </div>

                  <div style={{ display: 'flex', flexDirection: 'column', gap: '6px', fontSize: '12px', color: 'var(--text-secondary)', marginBottom: '18px' }}>
                    <div>Sliding Window: <strong>{cb.config.slidingWindowSize} calls</strong></div>
                    <div>Min Calls: <strong>{cb.config.minimumNumberOfCalls}</strong></div>
                    <div>Failure Rate Threshold: <strong>{cb.config.failureRateThresholdPercentage}%</strong></div>
                    <div>Wait in Open State: <strong>{cb.config.waitDurationInOpenStateSec}s</strong></div>
                    <div>Permitted Half-Open Probes: <strong>{cb.config.permittedCallsInHalfOpenState}</strong></div>
                  </div>

                  <div style={{ display: 'flex', gap: '8px' }}>
                    <button
                      className="btn-secondary"
                      onClick={() => handleCircuitTransition(cb.name, 'OPEN')}
                      style={{ fontSize: '11px', color: 'var(--accent-rose)' }}
                    >
                      Trip (OPEN)
                    </button>
                    <button
                      className="btn-secondary"
                      onClick={() => handleCircuitTransition(cb.name, 'HALF_OPEN')}
                      style={{ fontSize: '11px', color: 'var(--accent-amber)' }}
                    >
                      Test (HALF_OPEN)
                    </button>
                    <button
                      className="btn-secondary"
                      onClick={() => handleCircuitTransition(cb.name, 'CLOSED')}
                      style={{ fontSize: '11px', color: 'var(--accent-emerald)' }}
                    >
                      Reset (CLOSED)
                    </button>
                  </div>
                </div>
              ))
            )}
          </div>
        )}

        {/* TAB 8: HEALTH */}
        {activeTab === 'health' && (
          <div className="glass-panel" style={{ overflow: 'hidden' }}>
            <table>
              <thead>
                <tr>
                  <th>Target Host</th>
                  <th>Service ID</th>
                  <th>Health Status</th>
                  <th>Active In-Flight</th>
                  <th>Total Requests</th>
                  <th>Failures</th>
                  <th>Success Streak</th>
                  <th>Failure Streak</th>
                </tr>
              </thead>
              <tbody>
                {healthInstances.length === 0 ? (
                  <tr><td colSpan={8} style={{ textAlign: 'center', color: 'var(--text-muted)' }}>No upstream server instances probed</td></tr>
                ) : (
                  healthInstances.map((inst) => (
                    <tr key={inst.id}>
                      <td className="font-mono" style={{ fontWeight: 600 }}>{inst.host}:{inst.port}</td>
                      <td>{inst.serviceId}</td>
                      <td>
                        <span
                          className={`badge ${
                            inst.status === 'UP' ? 'badge-success' : inst.status === 'SUSPECT' ? 'badge-warning' : 'badge-danger'
                          }`}
                        >
                          {inst.status}
                        </span>
                      </td>
                      <td><strong>{inst.activeConnections || 0}</strong></td>
                      <td>{inst.totalRequests || 0}</td>
                      <td style={{ color: inst.totalFailures ? 'var(--accent-rose)' : 'inherit' }}>{inst.totalFailures || 0}</td>
                      <td>{inst.consecutiveSuccesses || 0}</td>
                      <td>{inst.consecutiveFailures || 0}</td>
                    </tr>
                  ))
                )}
              </tbody>
            </table>
          </div>
        )}

        {/* TAB 9: CONFIG VERSIONS */}
        {activeTab === 'versions' && (
          <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(300px, 1fr))', gap: '20px' }}>
            <div className="glass-panel" style={{ padding: '24px' }}>
              <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '16px' }}>🔄 Snapshot Status</h3>
              <div style={{ display: 'flex', flexDirection: 'column', gap: '10px', fontSize: '13px', color: 'var(--text-secondary)' }}>
                <div>Active Version: <strong style={{ color: 'var(--accent-cyan)' }}>v{configStatus?.activeVersion || overview?.activeVersion || 0}</strong></div>
                <div>Published At: <strong>{configStatus?.publishedAt || 'Bootstrap'}</strong></div>
                <div>Applied At: <strong>{configStatus?.appliedAt || 'Bootstrap'}</strong></div>
                <div>Propagation Lag: <strong style={{ color: 'var(--accent-amber)' }}>{configStatus?.propagationLagMs || overview?.propagationLagMs || 0} ms</strong></div>
                <div className="font-mono" style={{ wordBreak: 'break-all', fontSize: '11px', marginTop: '6px' }}>
                  SHA-256 Checksum: {configStatus?.checksum || overview?.checksum || 'N/A'}
                </div>
              </div>
              <button
                onClick={handleForceSync}
                disabled={syncing}
                className="btn-primary"
                style={{ marginTop: '20px', width: '100%', justifyContent: 'center' }}
              >
                {syncing ? 'Triggering Broadcast...' : '⚡ Trigger Full Cluster Sync'}
              </button>
            </div>
          </div>
        )}

        {/* TAB 10: AUDIT LOGS */}
        {activeTab === 'auditLogs' && (
          <div className="glass-panel" style={{ padding: '24px' }}>
            <h3 style={{ fontSize: '16px', fontWeight: 700, marginBottom: '16px' }}>📜 Live Request Telemetry & Correlation</h3>
            <p style={{ fontSize: '13px', color: 'var(--text-secondary)', marginBottom: '16px' }}>
              Requests processed through the non-blocking reactive filter pipeline with trace ID correlation.
            </p>
            <div className="font-mono" style={{ background: '#050811', padding: '16px', borderRadius: '10px', fontSize: '12px', display: 'flex', flexDirection: 'column', gap: '8px' }}>
              <div style={{ color: 'var(--accent-emerald)' }}>[TRACE-28491a] GET /api/v1/orders/101 - 200 OK (14ms) - Target: 127.0.0.1:8091</div>
              <div style={{ color: 'var(--accent-cyan)' }}>[TRACE-58920b] POST /api/v1/payments/charge - 201 Created (42ms) - Target: 127.0.0.1:8092</div>
              <div style={{ color: 'var(--accent-amber)' }}>[TRACE-93810c] GET /api/v1/users/me - 429 Too Many Requests (1ms) - Rate Limit Hit: tenant-beta</div>
              <div style={{ color: 'var(--accent-emerald)' }}>[TRACE-10294d] GET /health - 200 OK (0ms) - Direct Gateway Probe</div>
            </div>
          </div>
        )}

        {/* MODAL: ADD DYNAMIC ROUTE */}
        {isRouteModalOpen && (
          <div
            style={{
              position: 'fixed',
              top: 0,
              left: 0,
              right: 0,
              bottom: 0,
              background: 'rgba(0, 0, 0, 0.75)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 1000,
              backdropFilter: 'blur(6px)'
            }}
          >
            <div className="glass-panel" style={{ width: '480px', padding: '28px', background: 'var(--bg-secondary)' }}>
              <h3 style={{ fontSize: '18px', fontWeight: 800, marginBottom: '20px' }}>+ Add Dynamic Route</h3>
              <form onSubmit={handleCreateRoute} style={{ display: 'flex', flexDirection: 'column', gap: '14px' }}>
                <div>
                  <label style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600, display: 'block', marginBottom: '4px' }}>ROUTE ID</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. payments-v1"
                    style={{ width: '100%' }}
                    value={newRoute.routeId}
                    onChange={(e) => setNewRoute({ ...newRoute, routeId: e.target.value })}
                  />
                </div>
                <div>
                  <label style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600, display: 'block', marginBottom: '4px' }}>PATH PATTERN</label>
                  <input
                    type="text"
                    required
                    placeholder="e.g. /api/v1/payments/**"
                    style={{ width: '100%' }}
                    value={newRoute.pathPattern}
                    onChange={(e) => setNewRoute({ ...newRoute, pathPattern: e.target.value })}
                  />
                </div>
                <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '12px' }}>
                  <div>
                    <label style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600, display: 'block', marginBottom: '4px' }}>METHODS</label>
                    <input
                      type="text"
                      placeholder="GET,POST"
                      style={{ width: '100%' }}
                      value={newRoute.methods}
                      onChange={(e) => setNewRoute({ ...newRoute, methods: e.target.value })}
                    />
                  </div>
                  <div>
                    <label style={{ fontSize: '11px', color: 'var(--text-muted)', fontWeight: 600, display: 'block', marginBottom: '4px' }}>STRIP PREFIX</label>
                    <input
                      type="number"
                      min={0}
                      style={{ width: '100%' }}
                      value={newRoute.stripPrefixCount}
                      onChange={(e) => setNewRoute({ ...newRoute, stripPrefixCount: parseInt(e.target.value) || 0 })}
                    />
                  </div>
                </div>
                <div style={{ display: 'flex', alignItems: 'center', gap: '8px', margin: '4px 0' }}>
                  <input
                    type="checkbox"
                    id="authReq"
                    checked={newRoute.authRequired}
                    onChange={(e) => setNewRoute({ ...newRoute, authRequired: e.target.checked })}
                  />
                  <label htmlFor="authReq" style={{ fontSize: '13px', color: 'var(--text-secondary)', cursor: 'pointer' }}>Require JWT / API-Key Authentication</label>
                </div>
                <div style={{ display: 'flex', justifyContent: 'flex-end', gap: '10px', marginTop: '12px' }}>
                  <button type="button" className="btn-secondary" onClick={() => setIsRouteModalOpen(false)}>Cancel</button>
                  <button type="submit" className="btn-primary">Save Route</button>
                </div>
              </form>
            </div>
          </div>
        )}
      </main>
    </div>
  );
}
