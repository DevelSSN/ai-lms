package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.entity.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ProfilingAgent {

  @Inject EntityManager em;

  @Transactional
  public void analyze(ChatRequest request, String userId) {
    if (userId == null) return;

    UserProfile profile =
        em.createQuery("SELECT p FROM UserProfile p WHERE p.externalId = :extId", UserProfile.class)
            .setParameter("extId", userId)
            .getResultStream()
            .findFirst()
            .orElse(null);
    if (profile == null) {
      profile = new UserProfile();
      profile.externalId = userId;
      em.persist(profile);
      log.debug("Created new profile for user={}", userId);
    }
  }
}
