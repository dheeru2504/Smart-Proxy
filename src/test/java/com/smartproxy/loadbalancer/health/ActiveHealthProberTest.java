package com.smartproxy.loadbalancer.health;

import com.smartproxy.loadbalancer.UpstreamInstance;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.ClientResponse;
import org.springframework.web.reactive.function.client.ExchangeFunction;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.http.HttpStatus;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ActiveHealthProberTest {

    @Test
    void shouldReturnTrueWhenUpstreamReturns200() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.OK)
                        .header("Content-Type", "application/json")
                        .body("{\"status\":\"UP\"}")
                        .build()
        );

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ActiveHealthProber prober = new ActiveHealthProber(webClient);
        UpstreamInstance instance = new UpstreamInstance(UUID.randomUUID(), "srv", "127.0.0.1", 8080, 1);

        StepVerifier.create(prober.probe(instance, "/health", 1000))
                .expectNext(true)
                .verifyComplete();
    }

    @Test
    void shouldReturnFalseWhenUpstreamReturns500() {
        ExchangeFunction exchangeFunction = request -> Mono.just(
                ClientResponse.create(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body("{\"error\":\"crash\"}")
                        .build()
        );

        WebClient webClient = WebClient.builder().exchangeFunction(exchangeFunction).build();
        ActiveHealthProber prober = new ActiveHealthProber(webClient);
        UpstreamInstance instance = new UpstreamInstance(UUID.randomUUID(), "srv", "127.0.0.1", 8080, 1);

        StepVerifier.create(prober.probe(instance, "/health", 1000))
                .expectNext(false)
                .verifyComplete();
    }
}
