package com.ailms.gateway.resource;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import org.junit.jupiter.api.Test;

/**
 * Executable evidence that the gateway's {@code @RolesAllowed} gate is enforced, not merely declared.
 *
 * <p>The test profile disables OIDC ({@code src/test/resources/application.properties}), so no request
 * can carry an identity unless {@link TestSecurity} synthesises one. Unauthenticated callers must
 * therefore be rejected with 401, and a caller holding only the STUDENT role must be refused the
 * TEACHER/ADMIN endpoints with 403.
 *
 * <p>Only the denial paths are covered. The matching allow paths need a real bearer token, because the
 * resources read their identity from an injected {@code JsonWebToken} that the test profile cannot
 * supply; see the still-disabled {@code ChatResourceIntegrationTest} for the same reason.
 *
 * <p>Scope: gateway-level authorisation only. The orchestrator applies no role checks and trusts the
 * gateway-derived {@code X-User-Id} header, so orchestrator-side authorisation is out of scope here and
 * is not claimed.
 */
@QuarkusTest
class AuthEnforcementTest {

  @Test
  void chatEndpoint_shouldRejectUnauthenticated() {
    given()
        .contentType("application/json")
        .body("{\"message\":\"hello\",\"sessionId\":\"test\"}")
        .when()
        .post("/api/v1/chat")
        .then()
        .statusCode(401);
  }

  @Test
  void contentEndpoint_shouldRejectUnauthenticated() {
    given().when().get("/api/v1/content/insights").then().statusCode(401);
  }

  @Test
  void profileEndpoint_shouldRejectUnauthenticated() {
    given().when().get("/api/v1/profile").then().statusCode(401);
  }

  @Test
  void interactEndpoint_shouldRejectUnauthenticated() {
    given()
        .contentType("application/json")
        .body("{\"message\":\"hello\",\"thread_id\":\"test\"}")
        .when()
        .post("/api/interact")
        .then()
        .statusCode(401);
  }

  /** The SSE endpoint carries the same gate as the rest of InteractResource. */
  @Test
  void updatesEndpoint_shouldRejectUnauthenticated() {
    given().when().get("/api/updates").then().statusCode(401);
  }

  @Test
  @TestSecurity(user = "student-1", roles = "STUDENT")
  void teacherRoster_shouldRejectStudent() {
    given().when().get("/api/v1/teacher/students").then().statusCode(403);
  }

  @Test
  @TestSecurity(user = "student-1", roles = "STUDENT")
  void classAnalytics_shouldRejectStudent() {
    given().when().get("/api/v1/analytics/class").then().statusCode(403);
  }
}