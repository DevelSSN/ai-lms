package com.ailms.orchestrator.service;

public class SessionOwnershipException extends RuntimeException {

  public SessionOwnershipException(String message) {
    super(message);
  }
}