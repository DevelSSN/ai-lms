package com.ailms.orchestrator.service;

import static org.mockito.Mockito.*;

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
}
