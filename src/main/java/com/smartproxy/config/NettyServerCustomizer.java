package com.smartproxy.config;

import io.netty.channel.ChannelOption;
import org.springframework.boot.web.embedded.netty.NettyReactiveWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * Customizes the embedded Netty HTTP server for high-throughput reactive gateway workloads.
 */
@Configuration
public class NettyServerCustomizer implements WebServerFactoryCustomizer<NettyReactiveWebServerFactory> {

    private final SmartProxyProperties properties;

    public NettyServerCustomizer(SmartProxyProperties properties) {
        this.properties = properties;
    }

    @Override
    public void customize(NettyReactiveWebServerFactory factory) {
        factory.addServerCustomizers(httpServer -> httpServer
                // Enable TCP keep-alive and disable Nagle's algorithm (TCP_NODELAY) for minimum latency
                .tcpConfiguration(tcpServer -> tcpServer
                        .option(ChannelOption.SO_KEEPALIVE, true)
                        .option(ChannelOption.TCP_NODELAY, true)
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, properties.getGateway().getConnectTimeoutMs())
                )
                // HTTP idle and connection lifecycle tuning
                .idleTimeout(Duration.ofMillis(properties.getGateway().getPool().getMaxIdleTimeMs()))
        );
    }
}
