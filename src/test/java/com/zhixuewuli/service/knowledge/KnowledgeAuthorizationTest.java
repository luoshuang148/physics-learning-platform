package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import org.junit.jupiter.api.*;
import java.util.Optional;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;
@Tag("unit")
class KnowledgeAuthorizationTest {
    UserRepository users=mock(UserRepository.class);
    ClassStudentRepository students=mock(ClassStudentRepository.class);
    ClassTeacherRepository teachers=mock(ClassTeacherRepository.class);
    TeachingClassRepository classes=mock(TeachingClassRepository.class);
    KnowledgeAccessPolicy policy=new KnowledgeAccessPolicy(users,students,teachers,classes);
    @BeforeEach void seed() {
        var classroom=new TeachingClass();classroom.setId(10L);classroom.setName("fixture");
        when(classes.findById(10L)).thenReturn(Optional.of(classroom));
        var teacher=new User();teacher.setId(1L);teacher.setUserType(User.UserType.TEACHER);
        var student=new User();student.setId(2L);student.setUserType(User.UserType.STUDENT);
        when(users.findById(1L)).thenReturn(Optional.of(teacher));when(users.findById(2L)).thenReturn(Optional.of(student));
        when(teachers.existsByClassIdAndTeacherId(10L,1L)).thenReturn(true);
        when(students.existsByClassIdAndUserId(10L,2L)).thenReturn(true);
    }
    @Test void classTeacherCanManageAndRead() {
        assertDoesNotThrow(()->policy.requireTeacher(1L,10L));
        assertDoesNotThrow(()->policy.requireReader(1L,10L));
    }
    @Test void studentCanReadButCannotUpload() {
        assertDoesNotThrow(()->policy.requireReader(2L,10L));
        assertThrows(InsufficientPermissionException.class,()->policy.requireTeacher(2L,10L));
    }
    @Test void missingMembershipDeniesBothRolesWithoutGrantingDefaultClass() {
        assertThrows(InsufficientPermissionException.class,()->policy.requireReader(2L,20L));
        assertThrows(InsufficientPermissionException.class,()->policy.requireTeacher(1L,20L));
    }
    @Test void revokedMembershipTakesEffectOnNextRead() {
        assertDoesNotThrow(()->policy.requireReader(2L,10L));
        when(students.existsByClassIdAndUserId(10L,2L)).thenReturn(false);
        assertThrows(InsufficientPermissionException.class,()->policy.requireReader(2L,10L));
    }
}
