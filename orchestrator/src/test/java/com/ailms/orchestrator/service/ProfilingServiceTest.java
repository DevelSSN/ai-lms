package com.ailms.orchestrator.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ProfilingServiceTest {

  @Mock UserProfileRepository repo;

  @Test
  void ensureProfile_createsIfMissing() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.ensureProfile("user-1");
    verify(repo).findOrCreate("user-1");
  }

  @Test
  void ensureProfile_skipsIfExists() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.ensureProfile("user-1");
    verify(repo).findOrCreate("user-1");
  }

  @Test
  void ensureProfile_nullUserId_noop() {
    ProfilingService svc = new ProfilingService();
    svc.ensureProfile(null);
    verifyNoInteractions(repo);
  }

  @Test
  void applyProfileUpdate_appendsTraits() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;
    UserProfile profile = new UserProfile();
    profile.behavioralTraits = "prefers worked examples";
    when(repo.findOrCreate("user-1")).thenReturn(profile);

    svc.applyProfileUpdate("user-1", "Interested in ML, is a beginner");

    verify(repo).findOrCreate("user-1");
    assertNotNull(profile.behavioralTraits);
    assertTrue(profile.behavioralTraits.startsWith("prefers worked examples\n["));
    assertTrue(profile.behavioralTraits.endsWith("Interested in ML, is a beginner"));
    assertNotNull(profile.updatedAt);
  }

  @Test
  void applyProfileUpdate_firstUpdate_noLeadingNewline() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;
    UserProfile profile = new UserProfile();
    when(repo.findOrCreate("user-1")).thenReturn(profile);

    svc.applyProfileUpdate("user-1", "Interested in ML");

    assertFalse(profile.behavioralTraits.startsWith("\n"));
  }

  @Test
  void applyProfileUpdate_noUpdate_ignored() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.applyProfileUpdate("user-1", "No update.");

    verifyNoInteractions(repo);
  }

  @Test
  void applyProfileUpdate_blankOrNull_ignored() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.applyProfileUpdate("user-1", "  ");

    verifyNoInteractions(repo);
  }

  @Test
  void applyProfileUpdate_nullUserId_noop() {
    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.applyProfileUpdate(null, "Interested in ML");

    verifyNoInteractions(repo);
  }
}
