package com.ecommerce.agent.repository;

import com.ecommerce.agent.model.JCClawChannel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface JCClawChannelRepository extends JpaRepository<JCClawChannel, Long> {

    List<JCClawChannel> findByEnabledTrueOrderByCreatedAtDesc();

    Optional<JCClawChannel> findByBindingKey(String bindingKey);

    Optional<JCClawChannel> findByTypeAndEnabledTrue(String type);

    long countByEnabledTrue();
}
