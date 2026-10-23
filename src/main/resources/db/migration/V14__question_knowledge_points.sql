CREATE TABLE knowledge_points (
  code VARCHAR(100) NOT NULL,
  name VARCHAR(160) NOT NULL,
  topic VARCHAR(80) NOT NULL,
  PRIMARY KEY (code)
) ENGINE=InnoDB;

CREATE TABLE question_knowledge_points (
  id BIGINT NOT NULL AUTO_INCREMENT,
  question_id BIGINT NOT NULL,
  knowledge_point_code VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_question_knowledge_point UNIQUE (question_id, knowledge_point_code),
  CONSTRAINT fk_question_knowledge_point_question FOREIGN KEY (question_id) REFERENCES questions(id),
  CONSTRAINT fk_question_knowledge_point_code FOREIGN KEY (knowledge_point_code) REFERENCES knowledge_points(code),
  INDEX idx_question_knowledge_point_code (knowledge_point_code)
) ENGINE=InnoDB;

INSERT INTO knowledge_points (code, name, topic) VALUES
('mechanics.newton-laws', '牛顿运动定律', '力学'),
('mechanics.resultant-force', '合力与受力分析', '力学'),
('mechanics.inertia', '惯性与质量', '力学'),
('mechanics.friction', '摩擦力', '力学'),
('mechanics.normal-force', '弹力与支持力', '力学'),
('mechanics.tension', '绳的拉力', '力学'),
('mechanics.acceleration', '加速度与运动状态', '力学'),
('mechanics.action-reaction', '作用力与反作用力', '力学'),
('mechanics.connected-bodies', '连接体问题', '力学'),
('mechanics.inclined-plane', '斜面受力', '力学'),
('mechanics.free-body-diagram', '受力示意图', '力学'),
('mechanics.units', '物理量与单位', '力学');
