package com.smartproxy.loadbalancer.health;

import com.smartproxy.config.SmartProxyProperties;
import com.smartproxy.loadbalancer.UpstreamInstance;
import com.smartproxy.loadbalancer.UpstreamPoolRegistry;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Background scheduler periodically executing active HTTP health probes across all upstream pools.
 */
@Component
public class ActiveHealthCheckScheduler {

    private static final Logger log = LoggerFactory.getLogger(ActiveHealthCheckScheduler.class);

    private final UpstreamPoolRegistry poolRegistry;
    private final ActiveHealthProber prober;
    private final HealthStateMachine stateMachine;
    private final SmartProxyProperties properties;

    private Disposable subscription;

    public ActiveHealthCheckScheduler(UpstreamPoolRegistry poolRegistry,
                                      ActiveHealthProber prober,
                                      SmartProxyProperties properties) {
        this.poolRegistry = poolRegistry;
        this.prober = prober;
        this.properties = properties;

        var hcConfig = properties.getLoadbalancer().getHealthCheck();
        this.stateMachine = new HealthStateMachine(
                hcConfig.getUnhealthyThreshold(),
                hcConfig.getHealthyThreshold()
        );
    }

    @PostConstruct
    public void start() {
        var hcConfig = properties.getLoadbalancer().getHealthCheck();
        if (!hcConfig.isEnabled()) {
            log.info("Active health checking is disabled in configuration.");
            return;
        }

        int interval = Math.max(2, hcConfig.getIntervalSeconds());
        log.info("Starting active health check scheduler with {}s interval...", interval);

        this.subscription = Flux.interval(Duration.ZERO, Duration.ofSeconds(interval))
                .flatMap(tick -> runProbeCycle())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        null,
                        error -> log.error("Error in active health check cycle", error)
                );
    }

    @PreDestroy
    public void stop() {
        if (subscription != null && !subscription.isDisposed()) {
            subscription.dispose();
            log.info("Active health check scheduler stopped.");
        }
    }

    /**
     * Executes one complete non-blocking probe cycle across all registered instances.
     */
    public Mono<Void> runProbeCycle() {
        Map<String, List<UpstreamInstance>> pools = poolRegistry.getAllPools();
        if (pools.isEmpty()) {
            return Mono.empty();
        }

        var hcConfig = properties.getLoadbalancer().getHealthCheck();
        int timeoutMs = hcConfig.getTimeoutMs();

        List<UpstreamInstance> allInstances = pools.values().stream()
                .flatMap(Collection::stream)
                .toList();

        return Flux.fromIterable(allInstances)
                .flatMap(instance -> prober.probe(instance, "/health", timeoutMs)
                        .doOnNext(success -> stateMachine.evaluate(instance, success))
                        .onErrorResume(err -> Mono.empty())
                )
                .then();
    }
}
