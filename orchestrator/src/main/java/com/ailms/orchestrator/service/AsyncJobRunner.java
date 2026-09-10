package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;

@ApplicationScoped
public class AsyncJobRunner {

  @ActivateRequestContext
  public void run(Runnable task) {
    task.run();
  }
}