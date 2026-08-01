package com.ailms.gateway.resource;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

@QuarkusTest
@Disabled("Requires JWT token for JsonWebToken injection")
class ChatResourceIntegrationTest {

  @Test
  void sendMessage() {
    given()
        .contentType("application/json")
        .body("{\"message\":\"hi\",\"sessionId\":\"sess-1\"}")
        .when()
        .post("/api/v1/chat")
        .then()
        .statusCode(200)
        .body("message", is("Hello!"))
        .body("sessionId", is("sess-1"))
        .body("agentType", is("CONVERSATION"));
  }
}

@QuarkusTest
@Disabled("Requires JWT token for JsonWebToken injection")
class ContentResourceIntegrationTest {

  @Test
  void getInsights() {
    given()
        .when()
        .get("/api/v1/content/insights")
        .then()
        .statusCode(200)
        .body("message", is("Your progress is great!"))
        .body("agentType", is("INSIGHT"));
  }

  @Test
  void requestAssessment() {
    given()
        .contentType("application/json")
        .body(
            "{\"contentId\":\"content-1\",\"userId\":\"user-1\",\"questionCount\":5,\"difficulty\":\"medium\"}")
        .when()
        .post("/api/v1/content/assess")
        .then()
        .statusCode(200)
        .body("message", is("Assessment generated"));
  }
}

@QuarkusTest
@Disabled("Requires JWT token for JsonWebToken injection")
class ProfileResourceIntegrationTest {

  @Test
  void getProfile() {
    given()
        .when()
        .get("/api/v1/profile")
        .then()
        .statusCode(200)
        .body("message", is("Profile data"));
  }

  @Test
  void updateProfile() {
    given()
        .contentType("application/json")
        .body("{\"name\":\"Updated Name\"}")
        .when()
        .put("/api/v1/profile")
        .then()
        .statusCode(200)
        .body("message", is("Profile updated"));
  }
}

@QuarkusTest
@Disabled("Requires JWT token for JsonWebToken injection")
class InteractResourceIntegrationTest {

  @Test
  void interact() {
    given()
        .contentType("application/json")
        .body("{\"message\":\"hello\",\"thread_id\":\"thread-1\"}")
        .when()
        .post("/api/interact")
        .then()
        .statusCode(200)
        .body("message", is("Response"));
  }
}
