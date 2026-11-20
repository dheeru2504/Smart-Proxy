package com.smartproxy.core.foundation;

import com.smartproxy.core.foundation.health.GatewayHealthController;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.reactive.server.WebTestClient;

class GatewayHealthControllerTest {

    private final WebTestClient client = WebTestClient
            .bindToController(new GatewayHealthController())
            .build();

    @Test
    void shouldReturnHealthStatusUp() {
        client.get()
                .uri("/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP")
                .jsonPath("$.service").isEqualTo("smart-proxy")
                .jsonPath("$.version").isEqualTo("1.0.0")
                .jsonPath("$.jvm.totalMemoryMb").isNumber();
    }

    @Test
    void shouldReturnReadyStatus() {
        client.get()
                .uri("/readyz")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("READY")
                .jsonPath("$.service").isEqualTo("smart-proxy");
    }
}
