package com.ailms.gateway.service;

import com.ailms.common.entity.TeacherStudent;
import com.ailms.gateway.repository.TeacherStudentRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class TeacherStudentService {

  public record TeacherStudentResponse(String teacherId, String studentId) {}

  public record AddStudentRequest(String studentId) {}

  @Inject TeacherStudentRepository repo;

  public List<TeacherStudentResponse> getStudents(String teacherId) {
    return repo.findByTeacher(teacherId).stream()
        .map(ts -> new TeacherStudentResponse(ts.teacherId, ts.studentId))
        .toList();
  }

  public boolean exists(String teacherId, String studentId) {
    return repo.existsAssociation(teacherId, studentId);
  }

  public void addAssociation(String teacherId, String studentId) {
    if (studentId == null || studentId.isBlank()) {
      throw new IllegalArgumentException("studentId must not be blank");
    }
    if (repo.existsAssociation(teacherId, studentId)) return;
    TeacherStudent ts = new TeacherStudent();
    ts.teacherId = teacherId;
    ts.studentId = studentId;
    repo.add(ts);
  }

  public void removeAssociation(String teacherId, String studentId) {
    repo.findAssociation(teacherId, studentId).ifPresent(repo::remove);
  }
}