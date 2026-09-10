package com.ailms.gateway.resource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;
import org.junit.jupiter.api.Test;

class AnalyticsResourceTest {

  @Test
  void student_canViewOwnDataOnly() {
    assertTrue(AnalyticsResource.canViewStudent(Set.of("STUDENT"), "s1", "s1"));
    assertFalse(AnalyticsResource.canViewStudent(Set.of("STUDENT"), "s1", "s2"));
  }

  @Test
  void teacherAndAdmin_canViewAnyStudent() {
    assertTrue(AnalyticsResource.canViewStudent(Set.of("TEACHER"), "t1", "s9"));
    assertTrue(AnalyticsResource.canViewStudent(Set.of("ADMIN"), "a1", "s9"));
    assertTrue(AnalyticsResource.canViewStudent(Set.of("STUDENT", "TEACHER"), "t1", "s9"));
  }

  @Test
  void missingIdentity_isDenied() {
    assertFalse(AnalyticsResource.canViewStudent(null, "s1", "s1"));
    assertFalse(AnalyticsResource.canViewStudent(Set.of("STUDENT"), null, "s1"));
    assertFalse(AnalyticsResource.canViewStudent(null, null, "s1"));
  }

  @Test
  void noRoles_butSameSubject_isSelfViewAllowed() {
    assertTrue(AnalyticsResource.canViewStudent(Set.of(), "s1", "s1"));
    assertFalse(AnalyticsResource.canViewStudent(Set.of(), "s1", "s2"));
  }
}