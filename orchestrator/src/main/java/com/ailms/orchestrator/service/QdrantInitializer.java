package com.ailms.orchestrator.service;

import io.quarkus.runtime.Startup;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Slf4j
@ApplicationScoped
@Startup
public class QdrantInitializer {

  @Inject
  @ConfigProperty(name = "qdrant.rest.host", defaultValue = "localhost")
  String host;

  @Inject
  @ConfigProperty(name = "qdrant.rest.port", defaultValue = "10633")
  int port;

  @Inject
  @ConfigProperty(name = "qdrant.api.key", defaultValue = "qdrant")
  String apiKey;

  @Inject
  @ConfigProperty(name = "qdrant.collection", defaultValue = "ailms-content")
  String collection;

  @Inject
  @ConfigProperty(name = "qdrant.vector.size", defaultValue = "768")
  int vectorSize;

  @PostConstruct
  void ensureCollection() {
    try {
      if (collectionExists()) {
        log.info("Qdrant collection {} already exists", collection);
        return;
      }
      createCollection();
      log.info("Created Qdrant collection {} (size={})", collection, vectorSize);
    } catch (Exception e) {
      log.warn("Failed to initialize Qdrant collection {}: {}", collection, e.getMessage());
    }
  }

  private boolean collectionExists() throws Exception {
    HttpResponse<String> response = send("GET", "/collections/" + collection, null);
    return response.statusCode() == 200;
  }

  private void createCollection() throws Exception {
    String body = "{ \"vectors\": { \"size\": " + vectorSize + ", \"distance\": \"Cosine\" } }";
    HttpResponse<String> response = send("PUT", "/collections/" + collection, body);
    if (response.statusCode() != 200) {
      throw new IllegalStateException("Qdrant create collection returned " + response.statusCode() + ": " + response.body());
    }
  }

  private HttpResponse<String> send(String method, String path, String body) throws Exception {
    HttpRequest.Builder builder = HttpRequest.newBuilder()
        .uri(URI.create("http://" + host + ":" + port + path))
        .timeout(Duration.ofSeconds(10))
        .header("api-key", apiKey)
        .header("Content-Type", "application/json");

    HttpRequest request = switch (method) {
      case "GET" -> builder.GET().build();
      case "PUT" -> builder.method("PUT", HttpRequest.BodyPublishers.ofString(body == null ? "" : body)).build();
      default -> throw new IllegalArgumentException("Unsupported method " + method);
    };

    return HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .build()
        .send(request, HttpResponse.BodyHandlers.ofString());
  }
}
