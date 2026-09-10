package com.ailms.orchestrator.service;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.context.control.ActivateRequestContext;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class AsyncJobRunner {

  @Transactional
  @ActivateRequestContext
  public void run(Runnable task) {
    task.run();
  }
}