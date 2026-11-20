package com.smartproxy.storage.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

@Entity
@Table(name = "configuration_versions")
public class ConfigurationVersionEntity {

    @Id
    @Column(length = 50, nullable = false)
    private String component;

    @Column(name = "current_version", nullable = false)
    private Long currentVersion = 1L;

    @Column(name = "updated_at")
    private Instant updatedAt = Instant.now();

    @Column(length = 255)
    private String comment;

    public ConfigurationVersionEntity() {}

    public ConfigurationVersionEntity(String component, Long currentVersion, String comment) {
        this.component = component;
        this.currentVersion = currentVersion;
        this.updatedAt = Instant.now();
        this.comment = comment;
    }

    public String getComponent() { return component; }
    public void setComponent(String component) { this.component = component; }

    public Long getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Long currentVersion) { this.currentVersion = currentVersion; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getComment() { return comment; }
    public void setComment(String comment) { this.comment = comment; }
}
