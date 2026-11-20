package com.smartproxy.core.proxy;

import com.smartproxy.config.SmartProxyProperties;
import io.netty.channel.ChannelOption;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.BodyExtractors;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

import java.net.URI;
import java.time.Duration;

/**
 * High-performance non-blocking Netty HTTP client for forwarding requests to upstream backend instances.
 */
@Component
public class NettyHttpProxyClient implements HttpProxyClient {

    private static final Logger log = LoggerFactory.getLogger(NettyHttpProxyClient.class);

    private final WebClient webClient;
    private final SmartProxyProperties properties;

    @Autowired
    public NettyHttpProxyClient(SmartProxyProperties properties) {
        this.properties = properties;
        var poolConfig = properties.getGateway().getPool();

        ConnectionProvider provider = ConnectionProvider.builder("smartproxy-upstream-pool")
                .maxConnections(poolConfig.getMaxConnections())
                .maxIdleTime(Duration.ofMillis(poolConfig.getMaxIdleTimeMs()))
                .maxLifeTime(Duration.ofMillis(poolConfig.getMaxLifeTimeMs()))
                .pendingAcquireTimeout(Duration.ofMillis(poolConfig.getAcquireTimeoutMs()))
                .evictInBackground(Duration.ofSeconds(30))
                .build();

        HttpClient httpClient = HttpClient.create(provider)
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getGateway().getConnectTimeoutMs())
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true)
                .responseTimeout(Duration.ofMillis(properties.getGateway().getResponseTimeoutMs()));

        this.webClient = WebClient.builder()
                .clientConnector(new ReactorClientHttpConnector(httpClient))
                .build();
    }

    public NettyHttpProxyClient(WebClient webClient, SmartProxyProperties properties) {
        this.webClient = webClient;
        this.properties = properties;
    }

    @Override
    public Mono<ProxyResponse> forward(HttpMethod method, URI targetUri, HttpHeaders headers,
                                       Flux<DataBuffer> bodyFlux, int timeoutMs) {
        Duration timeout = Duration.ofMillis(timeoutMs > 0 ? timeoutMs : properties.getGateway().getResponseTimeoutMs());

        WebClient.RequestBodySpec requestSpec = webClient.method(method)
                .uri(targetUri)
                .headers(httpHeaders -> httpHeaders.putAll(headers));

        WebClient.RequestHeadersSpec<?> finalSpec;
        if (bodyFlux != null && hasBody(method)) {
            finalSpec = requestSpec.body(BodyInserters.fromDataBuffers(bodyFlux));
        } else {
            finalSpec = requestSpec;
        }

        return finalSpec.exchangeToMono(clientResponse -> {
            HttpStatusCode status = clientResponse.statusCode();
            HttpHeaders cleanResponseHeaders = ProxyHeaderUtils.filterResponseHeaders(clientResponse.headers().asHttpHeaders());
            return clientResponse.body(BodyExtractors.toDataBuffers())
                    .collectList()
                    .map(buffers -> new ProxyResponse(status, cleanResponseHeaders, Flux.fromIterable(buffers)));
        }).timeout(timeout);
    }

    private boolean hasBody(HttpMethod method) {
        return method == HttpMethod.POST || method == HttpMethod.PUT || method == HttpMethod.PATCH;
    }
}
