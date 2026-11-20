package com.smartproxy.core.proxy;

import com.smartproxy.core.foundation.error.GatewayException;
import org.junit.jupiter.api.Test;

import java.net.URI;

import static org.junit.jupiter.api.Assertions.*;

class SsrfProtectionValidatorTest {

    @Test
    void shouldAllowValidHttpTarget() {
        URI validUri = URI.create("http://payment-service:8080/api/v1/charge");
        assertDoesNotThrow(() -> SsrfProtectionValidator.validateTargetUri(validUri));
    }

    @Test
    void shouldBlockCloudMetadataIp() {
        URI metadataUri = URI.create("http://169.254.169.254/latest/meta-data/");
        assertThrows(GatewayException.class, () -> SsrfProtectionValidator.validateTargetUri(metadataUri));
    }

    @Test
    void shouldBlockLinkLocalAddresses() {
        URI linkLocalUri = URI.create("http://169.254.10.5:8080/status");
        assertThrows(GatewayException.class, () -> SsrfProtectionValidator.validateTargetUri(linkLocalUri));
    }

    @Test
    void shouldBlockProhibitedProtocols() {
        URI fileUri = URI.create("file:///etc/passwd");
        assertThrows(GatewayException.class, () -> SsrfProtectionValidator.validateTargetUri(fileUri));
    }
}
