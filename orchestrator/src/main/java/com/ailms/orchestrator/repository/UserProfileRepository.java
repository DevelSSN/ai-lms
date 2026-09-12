package com.ailms.orchestrator.repository;

import com.ailms.common.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.time.Duration;
import java.time.Instant;

@ApplicationScoped
public class UserProfileRepository implements PanacheRepository<UserProfile> {

  private static final Duration ACTIVITY_THROTTLE = Duration.ofSeconds(60);

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
   * allowed when the user has either never been pinged before, or has had activity (chat message or
   * video watching) since the previous ping. This ensures a user who stays logged out or
   * unresponsive receives at most one follow-up per inactive period, instead of a nagging repeat
   * every scheduler cycle.
   *
   * @return true if the profile was marked (a follow-up should be sent), false otherwise.
   */
  @Transactional
  public boolean markProactiveSentIfNotRecent(String externalId, Instant at) {
    UserProfile profile = findByExternalId(externalId);
    if (profile == null) return false;
    Instant lastSent = profile.lastProactiveSentAt;
    if (lastSent != null
        && !shouldResend(lastSent, effectiveLastActivityAt(externalId))) {
      return false;
    }
    profile.lastProactiveSentAt = at;
    persist(profile);
    return true;
  }

  /**
   * Records that the user was recently active (e.g. a heartbeat from a playing YouTube video).
   * The write is throttled so a stream of 30s heartbeats does not hammer the database.
   */
  @Transactional
  public void recordActivity(String externalId) {
    if (externalId == null || externalId.isBlank()) return;
    UserProfile profile = findOrCreate(externalId);
    Instant now = Instant.now();
    if (profile.lastActivityAt != null
        && now.minus(ACTIVITY_THROTTLE).isBefore(profile.lastActivityAt)) {
      return;
    }
    profile.lastActivityAt = now;
    persist(profile);
  }

  /**
   * Latest moment the user was active, considering both chat messages and watched-video
   * heartbeats. Used to decide whether a user is still "engaged" for proactive follow-ups.
   */
  public Instant effectiveLastActivityAt(String externalId) {
    Instant chat = conversationRepository.lastUserActivityAt(externalId);
    UserProfile profile = findByExternalId(externalId);
    Instant video = profile == null ? null : profile.lastActivityAt;
    if (chat == null) return video;
    if (video == null) return chat;
    return chat.isAfter(video) ? chat : video;
  }

  /** Returns true when the user has had any activity at or after {@code since}. */
  public boolean isActiveSince(String externalId, Instant since) {
    if (since == null || externalId == null) return false;
    Instant last = effectiveLastActivityAt(externalId);
    return last != null && !last.isBefore(since);
  }

  static boolean shouldResend(Instant lastSent, Instant lastActivity) {
    if (lastSent == null) return true;
    return lastActivity != null && lastActivity.isAfter(lastSent);
  }
}