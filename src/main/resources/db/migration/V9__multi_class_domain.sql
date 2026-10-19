-- Multi-class/multi-teacher domain (V9). Existing data joins one default class.
CREATE TABLE classes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(80) NOT NULL,
    grade VARCHAR(30) NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    CONSTRAINT uk_classes_name UNIQUE (name),
    INDEX idx_classes_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_students (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_class_student UNIQUE (user_id),
    CONSTRAINT fk_class_student_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_student_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_class_students_class (class_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE class_teachers (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    class_id BIGINT NOT NULL,
    teacher_id BIGINT NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'CO_TEACHER',
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_class_teacher UNIQUE (class_id, teacher_id),
    CONSTRAINT fk_class_teacher_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT,
    CONSTRAINT fk_class_teacher_user FOREIGN KEY (teacher_id) REFERENCES users(id) ON DELETE CASCADE,
    INDEX idx_class_teachers_teacher (teacher_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE teacher_events
    ADD COLUMN class_id BIGINT NULL AFTER teacher_name,
    ADD INDEX idx_teacher_events_class_status (class_id, status),
    ADD CONSTRAINT fk_teacher_event_class FOREIGN KEY (class_id) REFERENCES classes(id) ON DELETE RESTRICT;

INSERT INTO classes (name, status)
SELECT '默认班级', 'ACTIVE'
WHERE NOT EXISTS (SELECT 1 FROM classes WHERE name = '默认班级');

INSERT INTO class_students (class_id, user_id)
SELECT c.id, u.id
FROM classes c JOIN users u
WHERE c.name = '默认班级' AND u.user_type = 'STUDENT'
  AND NOT EXISTS (SELECT 1 FROM class_students cs WHERE cs.user_id = u.id);

INSERT INTO class_teachers (class_id, teacher_id, role)
SELECT c.id, u.id,
       CASE WHEN u.id = (SELECT MIN(t.id) FROM users t WHERE t.user_type = 'TEACHER')
            THEN 'OWNER' ELSE 'CO_TEACHER' END
FROM classes c JOIN users u
WHERE c.name = '默认班级' AND u.user_type = 'TEACHER'
  AND NOT EXISTS (SELECT 1 FROM class_teachers ct WHERE ct.class_id = c.id AND ct.teacher_id = u.id);

UPDATE teacher_events
SET class_id = (SELECT id FROM classes WHERE name = '默认班级' LIMIT 1)
WHERE class_id IS NULL;
