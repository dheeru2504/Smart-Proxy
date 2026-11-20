package com.smartproxy.loadbalancer.health;

import com.smartproxy.loadbalancer.UpstreamInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;

import java.time.Duration;

/**
 * Non-blocking HTTP health prober for upstream backend targets.
 */
@Component
public class ActiveHealthProber {

    private static final Logger log = LoggerFactory.getLogger(ActiveHealthProber.class);

    private final WebClient webClient;

    public ActiveHealthProber() {
        HttpClient httpClient = HttpClient.create()
                .responseTimeout(Duration.ofSeconds(3));

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public ActiveHealthProber(WebClient webClient) {
        this.webClient = webClient;
    }

    /**
     * Executes a non-blocking health probe to the given instance.
     *
     * @param instance   target upstream instance
     * @param healthPath path to probe (e.g. "/health" or "/healthz")
     * @param timeoutMs  maximum wait time in milliseconds
     * @return Mono emitting true if probe succeeds with 2xx/3xx, false otherwise
     */
    public Mono<Boolean> probe(UpstreamInstance instance, String healthPath, int timeoutMs) {
        if (instance == null) {
            return Mono.just(false);
        }

        String path = (healthPath != null && !healthPath.isBlank()) ? healthPath : "/health";
        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        String targetUrl = instance.getUri().toString() + path;
        Duration timeout = Duration.ofMillis(Math.max(200, timeoutMs));

        return webClient.get()
                .uri(targetUrl)
                .exchangeToMono(response -> {
                    HttpStatusCode status = response.statusCode();
                    boolean isHealthy = status.is2xxSuccessful() || status.is3xxRedirection();
                    return response.releaseBody().thenReturn(isHealthy);
                })
                .timeout(timeout)
                .onErrorResume(throwable -> {
                    log.debug("Health probe failed for {}: {}", targetUrl, throwable.getMessage());
                    return Mono.just(false);
                });
    }
}
