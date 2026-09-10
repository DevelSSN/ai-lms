package com.ailms.gateway.service;

import jakarta.enterprise.context.ApplicationScoped;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.microprofile.reactive.messaging.Incoming;

/** Logs messages routed to the dead-letter topics for manual inspection. */
@Slf4j
@ApplicationScoped
public class KafkaDlqConsumer {

  @Incoming("dlq-proactive-events")
  public void onProactiveDlq(String payload) {
    log.error("Dead-lettered proactive-events payload: {}", payload);
  }

  @Incoming("dlq-content-analysis-complete")
  public void onContentAnalysisDlq(String payload) {
    log.error("Dead-lettered content-analysis-complete payload: {}", payload);
  }

  @Incoming("dlq-profile-updated")
  public void onProfileUpdatedDlq(String payload) {
    log.error("Dead-lettered profile-updated payload: {}", payload);
  }

  @Incoming("dlq-insight-generated")
  public void onInsightGeneratedDlq(String payload) {
    log.error("Dead-lettered insight-generated payload: {}", payload);
  }
}