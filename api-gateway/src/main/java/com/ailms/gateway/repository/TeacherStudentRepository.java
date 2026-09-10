package com.ailms.gateway.repository;

import com.ailms.common.entity.TeacherStudent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class TeacherStudentRepository implements PanacheRepository<TeacherStudent> {

  public boolean existsAssociation(String teacherId, String studentId) {
    return count("teacherId = ?1 and studentId = ?2", teacherId, studentId) > 0;
  }

  public List<TeacherStudent> findByTeacher(String teacherId) {
    return list("teacherId = ?1 order by createdAt", teacherId);
  }

  public Optional<TeacherStudent> findAssociation(String teacherId, String studentId) {
    return find("teacherId = ?1 and studentId = ?2", teacherId, studentId).firstResultOptional();
  }

  @Transactional
  public void add(TeacherStudent ts) {
    persist(ts);
  }

  @Transactional
  public void remove(TeacherStudent ts) {
    delete(ts);
  }
}