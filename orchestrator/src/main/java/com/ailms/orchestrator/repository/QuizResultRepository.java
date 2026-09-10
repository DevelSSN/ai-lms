package com.ailms.orchestrator.repository;

import com.ailms.common.entity.QuizResult;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;

@ApplicationScoped
public class QuizResultRepository implements PanacheRepository<QuizResult> {

  @Transactional
  public void save(QuizResult result) {
    persist(result);
  }
}