package com.ailms.orchestrator.repository;

import com.ailms.common.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.time.Instant;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepository<UserProfile> {

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
   * Atomically marks the profile as pinged, but only when it was not pinged within the last
   * {@code cutoff} window. The check and update happen in one statement, so concurrent scheduler
   * runs cannot double-ping the same user.
   *
   * @return true if the profile was marked (i.e. a follow-up should be sent), false if the user was
   *     already pinged recently or has no profile.
   */
  @Transactional
  public boolean markProactiveSentIfNotRecent(String externalId, Instant at, Instant cutoff) {
    int updated =
        update(
            "set lastProactiveSentAt = ?1 where externalId = ?2 and"
                + " (lastProactiveSentAt is null or lastProactiveSentAt < ?3)",
            at,
            externalId,
            cutoff);
    return updated > 0;
  }
}
