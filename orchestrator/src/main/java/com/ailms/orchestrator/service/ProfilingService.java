package com.ailms.orchestrator.service;

import com.ailms.orchestrator.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ProfilingService {

  @Inject UserProfileRepository userProfileRepository;

  @Transactional
  public void ensureProfile(String userId) {
    if (userId == null) return;
    userProfileRepository.findOrCreate(userId);
    log.debug("Ensured profile exists for user={}", userId);
  }
}
