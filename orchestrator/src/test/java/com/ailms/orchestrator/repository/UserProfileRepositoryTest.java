package com.ailms.orchestrator.repository;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.ailms.common.entity.UserProfile;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@Disabled("Needs Quarkus Arc for Panache")
class UserProfileRepositoryTest {

  @Test
  void findByExternalId_delegatesToFind() {
    UserProfileRepository repo = spy(new UserProfileRepository());
    UserProfile expected = new UserProfile();
    expected.externalId = "user-1";

    doReturn(expected).when(repo).find("externalId", "user-1");
    UserProfile result = repo.findByExternalId("user-1");
    assertEquals("user-1", result.externalId);
  }

  @Test
  void findOrCreate_returnsExisting() {
    UserProfileRepository repo = spy(new UserProfileRepository());
    UserProfile existing = new UserProfile();
    existing.externalId = "user-1";

    doReturn(existing).when(repo).find("externalId", "user-1");
    UserProfile result = repo.findOrCreate("user-1");
    assertEquals("user-1", result.externalId);
  }

  @Test
  void findOrCreate_createsNew() {
    UserProfileRepository repo = spy(new UserProfileRepository());
    doReturn(null).when(repo).find("externalId", "new-user");
    doNothing().when(repo).persist(any(UserProfile.class));

    UserProfile result = repo.findOrCreate("new-user");
    assertNotNull(result);
    assertEquals("new-user", result.externalId);
    verify(repo).persist(any(UserProfile.class));
  }
}
