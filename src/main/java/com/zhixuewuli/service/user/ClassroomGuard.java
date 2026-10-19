package com.zhixuewuli.service.user;

import com.zhixuewuli.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.*;
import java.util.List;

/** Existing active teacher rows serialize the infrequent classroom membership changes. */
@Component
@RequiredArgsConstructor
public class ClassroomGuard {
    private final UserRepository users;
    @Transactional(propagation=Propagation.MANDATORY)
    public List<Long> lock() { return users.lockActiveTeacherIds(); }
}
