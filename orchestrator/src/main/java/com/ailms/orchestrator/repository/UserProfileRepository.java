package com.ailms.orchestrator.repository;

import com.ailms.common.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepository<UserProfile> {

  @Inject ConversationRepository conversationRepository;

  public UserProfile findByExternalId(String externalId) {
    return find("externalId", externalId).firstResult();
  }

  public UserProfile findOrCreate(String externalId) {
    UserProfile profile = findByExternalId(externalId);
    if (profile == null) {
      profile = new UserProfile();
      profile.externalId = externalId;
      persist(profile);
    }
    return profile;
  }

  /**
   * Marks the profile as pinged, returning whether a follow-up should be sent. A follow-up is only
   * allowed when the user has either never been pinged before, or has sent new messages (if only
   * user-initiated messages count as activity) since the previous ping. This ensures a user who
   * stays logged out or unresponsive receives at most one follow-up per inactive period, instead of
   * a nagging repeat every scheduler cycle.
   *
   * @return true if the profile was marked (a follow-up should be sent), false otherwise.
   */
  @Transactional
  public boolean markProactiveSentIfNotRecent(String externalId, Instant at) {
    UserProfile profile = findByExternalId(externalId);
    if (profile == null) return false;
    Instant lastSent = profile.lastProactiveSentAt;
    if (lastSent != null && !shouldResend(lastSent, conversationRepository.lastUserActivityAt(externalId))) {
      return false;
    }
    profile.lastProactiveSentAt = at;
    persist(profile);
    return true;
  }

  static boolean shouldResend(Instant lastSent, Instant lastActivity) {
    if (lastSent == null) return true;
    return lastActivity != null && lastActivity.isAfter(lastSent);
  }
}