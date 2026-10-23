package com.zhixuewuli.service.knowledge;
import com.zhixuewuli.entity.*;
import com.zhixuewuli.repository.*;
import com.zhixuewuli.common.exception.InsufficientPermissionException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
@Component @RequiredArgsConstructor
public class KnowledgeAccessPolicy {
    private final UserRepository users;
    private final ClassStudentRepository students;
    private final ClassTeacherRepository teachers;
    private final TeachingClassRepository classes;
    public void requireTeacher(Long viewerId,Long classId) {
        User user=activeUser(viewerId,classId);
        if(user.getUserType()!=User.UserType.TEACHER || !teachers.existsByClassIdAndTeacherId(classId,viewerId))deny();
    }
    public void requireReader(Long viewerId,Long classId) {
        User user=activeUser(viewerId,classId);
        boolean allowed=user.getUserType()==User.UserType.TEACHER
                ? teachers.existsByClassIdAndTeacherId(classId,viewerId)
                : students.existsByClassIdAndUserId(classId,viewerId);
        if(!allowed)deny();
    }
    private User activeUser(Long viewerId,Long classId) {
        if(viewerId==null || classId==null)throw new InsufficientPermissionException("无权访问知识库");
        var user=users.findById(viewerId).filter(u->Boolean.TRUE.equals(u.getActive()))
                .orElseThrow(()->new InsufficientPermissionException("无权访问知识库"));
        if(classes.findById(classId).filter(c->c.getStatus()==TeachingClass.Status.ACTIVE).isEmpty())deny();
        return user;
    }
    private static void deny(){throw new InsufficientPermissionException("无权访问知识库");}
}
