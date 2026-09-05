package com.ailms.orchestrator.service;

import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.UserProfileRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@ApplicationScoped
public class ProfilingService {

  private static final String NO_UPDATE = "No update.";

  @Inject UserProfileRepository userProfileRepository;

  @Transactional
  public void ensureProfile(String userId) {
    if (userId == null) return;
    userProfileRepository.findOrCreate(userId);
    log.debug("Ensured profile exists for user={}", userId);
  }

  @Transactional
  public void applyProfileUpdate(String userId, String profileUpdate) {
    if (userId == null) return;
    String trimmed = profileUpdate == null ? "" : profileUpdate.trim();
    if (trimmed.isBlank() || trimmed.equalsIgnoreCase(NO_UPDATE)) {
      log.debug("No actionable profile update for user={}", userId);
      return;
    }
    UserProfile profile = userProfileRepository.findOrCreate(userId);
    String existing = profile.behavioralTraits;
    String separator = (existing == null || existing.isBlank()) ? "" : "\n";
    profile.behavioralTraits =
        (existing == null ? "" : existing) + separator + "[" + Instant.now() + "] " + trimmed;
    profile.updatedAt = Instant.now();
    log.info("Applied profile update for user={} chars={}", userId, trimmed.length());
  }
}
