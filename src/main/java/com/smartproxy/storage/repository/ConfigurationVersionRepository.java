package com.smartproxy.storage.repository;

import com.smartproxy.storage.entity.ConfigurationVersionEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationVersionRepository extends JpaRepository<ConfigurationVersionEntity, String> {
}
