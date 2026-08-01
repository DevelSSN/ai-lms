package com.ailms.gateway.resource;

import static io.restassured.RestAssured.given;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Disabled("Requires OIDC/JWT security setup")
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

  @Test
  void updatesEndpoint_shouldAllowUnauthenticated() {
    given().when().get("/api/updates").then().statusCode(200);
  }
}
