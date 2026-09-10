package com.ailms.gateway.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

import com.ailms.common.entity.TeacherStudent;
import com.ailms.gateway.repository.TeacherStudentRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TeacherStudentServiceTest {

  @Mock TeacherStudentRepository repo;

  @InjectMocks TeacherStudentService service;

  @Test
  void getStudents_mapsEntitiesToResponses() {
    TeacherStudent ts = new TeacherStudent();
    ts.teacherId = "t1";
    ts.studentId = "s1";
    when(repo.findByTeacher("t1")).thenReturn(List.of(ts));

    var result = service.getStudents("t1");

    assertEquals(1, result.size());
    assertEquals("t1", result.get(0).teacherId());
    assertEquals("s1", result.get(0).studentId());
  }

  @Test
  void addAssociation_persistsNewLink() {
    when(repo.existsAssociation("t1", "s1")).thenReturn(false);

    service.addAssociation("t1", "s1");

    org.mockito.ArgumentCaptor<TeacherStudent> captor =
        org.mockito.ArgumentCaptor.forClass(TeacherStudent.class);
    verify(repo).add(captor.capture());
    assertEquals("t1", captor.getValue().teacherId);
    assertEquals("s1", captor.getValue().studentId);
  }

  @Test
  void addAssociation_noops_whenAlreadyLinked() {
    when(repo.existsAssociation("t1", "s1")).thenReturn(true);

    service.addAssociation("t1", "s1");

    verify(repo, never()).add(any(TeacherStudent.class));
  }

  @Test
  void addAssociation_rejectsBlankStudent() {
    assertThrows(IllegalArgumentException.class, () -> service.addAssociation("t1", "   "));
    verify(repo, never()).add(any(TeacherStudent.class));
  }

  @Test
  void removeAssociation_deletesWhenPresent() {
    TeacherStudent ts = new TeacherStudent();
    ts.teacherId = "t1";
    ts.studentId = "s1";
    when(repo.findAssociation("t1", "s1")).thenReturn(Optional.of(ts));

    service.removeAssociation("t1", "s1");

    verify(repo).remove(ts);
  }

  @Test
  void removeAssociation_noopsWhenAbsent() {
    when(repo.findAssociation(anyString(), anyString())).thenReturn(Optional.empty());

    service.removeAssociation("t1", "s1");

    verify(repo, never()).remove(any(TeacherStudent.class));
  }
}