package com.ailms.orchestrator.repository;

import com.ailms.common.entity.UserProfile;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;

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
}
