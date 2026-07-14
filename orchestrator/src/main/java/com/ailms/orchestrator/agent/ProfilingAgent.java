package com.ailms.orchestrator.agent;

import com.ailms.common.dto.ChatRequest;
import com.ailms.common.entity.UserProfile;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class ProfilingAgent {

  @Inject EntityManager em;

  @Transactional
  public void analyze(ChatRequest request) {
    if (request.userId() == null) return;

    UserProfile profile = em.createQuery("SELECT p FROM UserProfile p WHERE p.externalId = :extId", UserProfile.class)
            .setParameter("extId", request.userId())
            .getResultStream().findFirst().orElse(null);
    if (profile == null) {
      profile = new UserProfile();
      profile.externalId = request.userId();
      em.persist(profile);
    }
  }
}
