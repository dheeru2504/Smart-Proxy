package com.smartproxy.core.proxy;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;

/**
 * Contract for HTTP reverse proxy forwarding clients.
 */
public interface HttpProxyClient {

    record ProxyResponse(HttpStatusCode statusCode, HttpHeaders headers, Flux<DataBuffer> body) {}

    /**
     * Forwards an HTTP request reactively with body streaming and timeout enforcement.
     */
    Mono<ProxyResponse> forward(HttpMethod method, URI targetUri, HttpHeaders headers,
                               Flux<DataBuffer> bodyFlux, int timeoutMs);
}
