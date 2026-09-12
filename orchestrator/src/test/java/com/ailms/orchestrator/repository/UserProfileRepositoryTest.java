package com.ailms.orchestrator.repository;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class UserProfileRepositoryTest {

  @Test
  void shouldResend_neverSent_alwaysResends() {
    assertTrue(UserProfileRepository.shouldResend(null, Instant.now()));
    assertTrue(UserProfileRepository.shouldResend(null, null));
  }

  @Test
  void shouldResend_noUserActivityAfterPing_blocksRepeat() {
    Instant sent = Instant.parse("2026-09-12T10:00:00Z");
    assertFalse(UserProfileRepository.shouldResend(sent, Instant.parse("2026-09-12T09:00:00Z")));
    assertFalse(UserProfileRepository.shouldResend(sent, sent));
    assertFalse(UserProfileRepository.shouldResend(sent, null));
  }

  @Test
  void shouldResend_userActivityAfterPing_permitsRepeat() {
    Instant sent = Instant.parse("2026-09-12T10:00:00Z");
    assertTrue(UserProfileRepository.shouldResend(sent, Instant.parse("2026-09-12T10:30:00Z")));
  }
}