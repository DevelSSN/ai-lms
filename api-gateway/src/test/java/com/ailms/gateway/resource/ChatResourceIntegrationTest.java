package com.ailms.gateway.resource;

import com.ailms.common.dto.ChatHistory;
import com.ailms.common.dto.ChatRequest;
import com.ailms.common.dto.ChatResponse;
import com.ailms.gateway.service.OrchestratorClient;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.security.TestSecurity;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.CoreMatchers.is;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@QuarkusTest
@TestSecurity(user = "user-1", roles = {"student"})
class ChatResourceIntegrationTest {

  @Inject @RestClient
  OrchestratorClient orchestratorClient;

  @Test
  void sendMessage() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Hello!", "sess-1", "CONVERSATION"));

    given()
        .contentType("application/json")
        .body("{\"message\":\"hi\",\"sessionId\":\"sess-1\"}")
        .when().post("/api/v1/chat")
        .then().statusCode(200)
        .body("message", is("Hello!"))
        .body("sessionId", is("sess-1"))
        .body("agentType", is("CONVERSATION"));
  }
}

@QuarkusTest
@TestSecurity(user = "user-1", roles = {"student"})
class ContentResourceIntegrationTest {

  @Inject @RestClient
  OrchestratorClient orchestratorClient;

  @Test
  void getInsights() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Your progress is great!", "insight-user-1", "INSIGHT"));

    given()
        .when().get("/api/v1/content/insights")
        .then().statusCode(200)
        .body("message", is("Your progress is great!"))
        .body("agentType", is("INSIGHT"));
  }

  @Test
  void requestAssessment() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Assessment generated", "assess-user-1", "ASSESSMENT"));

    given()
        .contentType("application/json")
        .body("{\"contentId\":\"content-1\",\"userId\":\"user-1\",\"questionCount\":5,\"difficulty\":\"medium\"}")
        .when().post("/api/v1/content/assess")
        .then().statusCode(200)
        .body("message", is("Assessment generated"));
  }
}

@QuarkusTest
@TestSecurity(user = "user-1", roles = {"student"})
class ProfileResourceIntegrationTest {

  @Inject @RestClient
  OrchestratorClient orchestratorClient;

  @Test
  void getProfile() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Profile data", "profile-user-1", "PROFILE"));

    given()
        .when().get("/api/v1/profile")
        .then().statusCode(200)
        .body("message", is("Profile data"));
  }

  @Test
  void updateProfile() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Profile updated", "profile-user-1", "PROFILE"));

    given()
        .contentType("application/json")
        .body("{\"name\":\"Updated Name\"}")
        .when().put("/api/v1/profile")
        .then().statusCode(200)
        .body("message", is("Profile updated"));
  }
}

@QuarkusTest
@TestSecurity(user = "user-1", roles = {"student"})
class InteractResourceIntegrationTest {

  @Inject @RestClient
  OrchestratorClient orchestratorClient;

  @Test
  void interact() {
    when(orchestratorClient.processMessage(any(ChatRequest.class)))
        .thenReturn(new ChatResponse("Response", "thread-1", "CONVERSATION"));

    given()
        .contentType("application/json")
        .body("{\"message\":\"hello\",\"thread_id\":\"thread-1\"}")
        .when().post("/api/interact")
        .then().statusCode(200)
        .body("message", is("Response"));
  }
}
