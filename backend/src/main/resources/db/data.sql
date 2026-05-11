-- 演示数据 (BCrypt 密码统一为 123456)
-- 哈希值为 $2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i

INSERT INTO sys_user (username, password, role, real_name, id_card, phone, email, gender, status) VALUES
('admin',    '$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i', 'ADMIN',   '系统管理员', '110101199001011234', '13800000001', 'admin@exam.gov.cn',   '男', 1),
('teacher',  '$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i', 'TEACHER', '王老师',     '110101198501012345', '13800000002', 'teacher@exam.gov.cn', '女', 1),
('student1', '$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i', 'STUDENT', '张三',       '110101200001011001', '13900000001', 'zhangsan@example.com','男', 1),
('student2', '$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i', 'STUDENT', '李四',       '110101200001012002', '13900000002', 'lisi@example.com',    '女', 1),
('student3', '$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i', 'STUDENT', '王五',       '110101200001013003', '13900000003', 'wangwu@example.com',  '男', 1);

INSERT INTO sys_major (major_code, major_name, level, total_credits, description, status) VALUES
('080901', '计算机科学与技术', '本科', 70, '培养计算机软硬件方向的高级人才', 1),
('120203', '会计学',         '本科', 65, '培养财务会计、管理会计专业人才',   1),
('050101', '汉语言文学',     '本科', 68, '培养汉语言、文学方向专业人才',     1),
('030101', '法学',           '本科', 70, '培养法律实务及理论人才',           1);

INSERT INTO sys_course (course_code, course_name, credit, course_type, description) VALUES
('03706', '思想道德修养与法律基础', 2, '公共课', '公共必修课'),
('03707', '毛泽东思想和中国特色社会主义理论体系概论', 4, '公共课', '公共必修课'),
('00015', '英语(二)',          14, '公共课', '公共选考'),
('02197', '概率论与数理统计',   3, '专业课', '计算机专业'),
('02326', '操作系统',           4, '专业课', '计算机专业'),
('04737', 'C++程序设计',        4, '专业课', '计算机专业'),
('00067', '财务管理学',         6, '专业课', '会计学专业'),
('00150', '金融理论与实务',     6, '专业课', '会计学专业'),
('00529', '文学概论(一)',       7, '专业课', '汉语言文学专业'),
('00540', '外国文学史',         6, '专业课', '汉语言文学专业'),
('00227', '公司法',             4, '专业课', '法学专业'),
('00228', '环境与资源保护法学', 4, '专业课', '法学专业');

-- 专业-课程关联
INSERT INTO sys_major_course (major_id, course_id, is_required) VALUES
(1,1,1),(1,2,1),(1,3,0),(1,4,1),(1,5,1),(1,6,1),
(2,1,1),(2,2,1),(2,3,0),(2,7,1),(2,8,1),
(3,1,1),(3,2,1),(3,3,0),(3,9,1),(3,10,1),
(4,1,1),(4,2,1),(4,3,0),(4,11,1),(4,12,1);

INSERT INTO sys_exam_plan (plan_code, plan_name, exam_year, exam_term, course_id, major_id, exam_date, start_time, end_time, location, capacity, registered_count, register_start, register_end, status, remark) VALUES
('PLAN202501-001', '2025年上半年-思想道德修养与法律基础', 2025, '上', 1, NULL, '2025-04-12', '09:00', '11:30', '省考试院第一考场', 200, 3, '2025-02-01', '2025-03-15', 'FINISHED', '已结束'),
('PLAN202501-002', '2025年上半年-毛中特概论',           2025, '上', 2, NULL, '2025-04-12', '14:30', '17:00', '省考试院第一考场', 200, 2, '2025-02-01', '2025-03-15', 'FINISHED', '已结束'),
('PLAN202502-001', '2025年下半年-操作系统',             2025, '下', 5, 1,    '2025-10-25', '09:00', '11:30', '省考试院第二考场', 150, 2, '2025-08-01', '2025-09-15', 'PUBLISHED', '计算机专业'),
('PLAN202502-002', '2025年下半年-C++程序设计',          2025, '下', 6, 1,    '2025-10-25', '14:30', '17:00', '省考试院第二考场', 150, 1, '2025-08-01', '2025-09-15', 'PUBLISHED', '计算机专业'),
('PLAN202601-001', '2026年上半年-财务管理学',           2026, '上', 7, 2,    '2026-04-11', '09:00', '11:30', '省考试院第三考场', 180, 2, '2026-02-01', '2026-03-15', 'PUBLISHED', '会计学专业'),
('PLAN202601-002', '2026年上半年-公司法',               2026, '上',11, 4,    '2026-04-11', '14:30', '17:00', '省考试院第三考场', 180, 0, '2026-02-01', '2026-03-15', 'DRAFT', '法学专业-草稿');

INSERT INTO sys_registration (student_id, plan_id, registration_no, admission_ticket_no, payment_status, status) VALUES
(3, 1, 'REG2025010001', 'AT2025010301001', 'PAID',   'APPROVED'),
(3, 2, 'REG2025010002', 'AT2025010301002', 'PAID',   'APPROVED'),
(4, 1, 'REG2025010003', 'AT2025010301003', 'PAID',   'APPROVED'),
(5, 1, 'REG2025010004', 'AT2025010301004', 'PAID',   'APPROVED'),
(4, 2, 'REG2025010005', 'AT2025010301005', 'PAID',   'APPROVED'),
(3, 3, 'REG2025020001', NULL,              'PAID',   'APPROVED'),
(4, 3, 'REG2025020002', NULL,              'UNPAID', 'PENDING'),
(5, 4, 'REG2025020003', NULL,              'PAID',   'APPROVED'),
(3, 5, 'REG2026010001', NULL,              'UNPAID', 'PENDING'),
(4, 5, 'REG2026010002', NULL,              'PAID',   'APPROVED');

INSERT INTO sys_score (student_id, course_id, plan_id, exam_year, exam_term, score, status, exam_date) VALUES
(3, 1, 1, 2025, '上', 85.0, 'PASS',   '2025-04-12'),
(3, 2, 2, 2025, '上', 78.5, 'PASS',   '2025-04-12'),
(4, 1, 1, 2025, '上', 72.0, 'PASS',   '2025-04-12'),
(4, 2, 2, 2025, '上', 55.0, 'FAIL',   '2025-04-12'),
(5, 1, 1, 2025, '上', 91.5, 'PASS',   '2025-04-12'),
(5, 2, 2, 2025, '上', 68.0, 'PASS',   '2025-04-12'),
(3, 4, NULL, 2024, '下', 80.0, 'PASS', '2024-10-26'),
(4, 4, NULL, 2024, '下',  0.0, 'ABSENT','2024-10-26');
