package com.ailms.orchestrator.service;

import com.ailms.common.entity.UserProfile;
import com.ailms.orchestrator.repository.UserProfileRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProfilingServiceTest {

  @Mock UserProfileRepository repo;

  @Test
  void ensureProfile_createsIfMissing() {
    when(repo.findByExternalId("user-1")).thenReturn(null);

    ProfilingService svc = new ProfilingService();
    svc.userProfileRepository = repo;

    svc.ensureProfile("user-1");
    verify(repo).findOrCreate("user-1");
  }

  @Test
  void ensureProfile_skipsIfExists() {
    when(repo.findByExternalId("user-1")).thenReturn(new UserProfile());

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
}
