-- 省考试院自学考试计划管理系统 - 数据库初始化脚本 (SQLite)
-- 启用 WAL 模式（在 JDBC URL 中设置，此处仅注释说明）

DROP TABLE IF EXISTS sys_user;
CREATE TABLE sys_user (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    username     VARCHAR(50)  NOT NULL UNIQUE,
    password     VARCHAR(255) NOT NULL,
    role         VARCHAR(20)  NOT NULL DEFAULT 'STUDENT',
    real_name    VARCHAR(50),
    id_card      VARCHAR(20),
    phone        VARCHAR(20),
    email        VARCHAR(100),
    gender       VARCHAR(10),
    avatar       VARCHAR(255),
    status       INTEGER      NOT NULL DEFAULT 1,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP
);
-- 覆盖常见查询的复合索引
CREATE INDEX idx_user_role        ON sys_user(role, deleted);
CREATE INDEX idx_user_status      ON sys_user(status, deleted);
CREATE INDEX idx_user_keyword     ON sys_user(real_name, deleted);
CREATE INDEX idx_user_id_card     ON sys_user(id_card);

DROP TABLE IF EXISTS sys_major;
CREATE TABLE sys_major (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    major_code    VARCHAR(50)  NOT NULL UNIQUE,
    major_name    VARCHAR(100) NOT NULL,
    level         VARCHAR(20)  NOT NULL DEFAULT '专科',
    total_credits INTEGER      NOT NULL DEFAULT 0,
    description   TEXT,
    status        INTEGER      NOT NULL DEFAULT 1,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_major_status ON sys_major(status, deleted);

DROP TABLE IF EXISTS sys_course;
CREATE TABLE sys_course (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    course_code  VARCHAR(50)  NOT NULL UNIQUE,
    course_name  VARCHAR(100) NOT NULL,
    credit       INTEGER      NOT NULL DEFAULT 0,
    course_type  VARCHAR(20)  NOT NULL DEFAULT '公共课',
    description  TEXT,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_course_deleted ON sys_course(deleted);

DROP TABLE IF EXISTS sys_major_course;
CREATE TABLE sys_major_course (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    major_id    INTEGER NOT NULL,
    course_id   INTEGER NOT NULL,
    is_required INTEGER NOT NULL DEFAULT 1,
    UNIQUE(major_id, course_id)
);
CREATE INDEX idx_mc_major  ON sys_major_course(major_id);
CREATE INDEX idx_mc_course ON sys_major_course(course_id);

DROP TABLE IF EXISTS sys_exam_plan;
CREATE TABLE sys_exam_plan (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_code        VARCHAR(50)  NOT NULL UNIQUE,
    plan_name        VARCHAR(200) NOT NULL,
    exam_year        INTEGER      NOT NULL,
    exam_term        VARCHAR(10)  NOT NULL,
    course_id        INTEGER      NOT NULL,
    major_id         INTEGER,
    exam_date        VARCHAR(20),
    start_time       VARCHAR(10),
    end_time         VARCHAR(10),
    location         VARCHAR(200),
    capacity         INTEGER      NOT NULL DEFAULT 100,
    registered_count INTEGER      NOT NULL DEFAULT 0,
    register_start   VARCHAR(20),
    register_end     VARCHAR(20),
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark           TEXT,
    deleted          INTEGER      NOT NULL DEFAULT 0,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plan_year_term    ON sys_exam_plan(exam_year, exam_term);
CREATE INDEX idx_plan_status       ON sys_exam_plan(status, deleted);
CREATE INDEX idx_plan_course       ON sys_exam_plan(course_id, deleted);
CREATE INDEX idx_plan_date_status  ON sys_exam_plan(exam_date, status, deleted);

DROP TABLE IF EXISTS sys_registration;
CREATE TABLE sys_registration (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id          INTEGER     NOT NULL,
    plan_id             INTEGER     NOT NULL,
    registration_no     VARCHAR(50) NOT NULL UNIQUE,
    admission_ticket_no VARCHAR(50),
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    audit_remark        VARCHAR(500),
    register_time       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER     NOT NULL DEFAULT 0,
    create_time         DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, plan_id)
);
CREATE INDEX idx_reg_student        ON sys_registration(student_id, deleted);
CREATE INDEX idx_reg_plan           ON sys_registration(plan_id, deleted);
CREATE INDEX idx_reg_status         ON sys_registration(status, deleted);
CREATE INDEX idx_reg_payment_status ON sys_registration(payment_status, deleted);

DROP TABLE IF EXISTS sys_score;
CREATE TABLE sys_score (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id  INTEGER     NOT NULL,
    course_id   INTEGER     NOT NULL,
    plan_id     INTEGER,
    exam_year   INTEGER     NOT NULL,
    exam_term   VARCHAR(10) NOT NULL,
    score       REAL        NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PASS',
    exam_date   VARCHAR(20),
    deleted     INTEGER     NOT NULL DEFAULT 0,
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, course_id, exam_year, exam_term)
);
CREATE INDEX idx_score_student     ON sys_score(student_id, deleted);
CREATE INDEX idx_score_course      ON sys_score(course_id, deleted);
CREATE INDEX idx_score_year_term   ON sys_score(exam_year, exam_term, deleted);
CREATE INDEX idx_score_status      ON sys_score(status, deleted);

DROP TABLE IF EXISTS sys_graduation;
CREATE TABLE sys_graduation (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id   INTEGER     NOT NULL,
    major_id     INTEGER     NOT NULL,
    apply_time   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    audit_remark VARCHAR(500),
    audit_time   DATETIME,
    deleted      INTEGER     NOT NULL DEFAULT 0,
    create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_grad_student ON sys_graduation(student_id, deleted);
CREATE INDEX idx_grad_status  ON sys_graduation(status, deleted);

DROP TABLE IF EXISTS sys_major;
CREATE TABLE sys_major (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    major_code    VARCHAR(50)  NOT NULL UNIQUE,
    major_name    VARCHAR(100) NOT NULL,
    level         VARCHAR(20)  NOT NULL DEFAULT '专科',
    total_credits INTEGER      NOT NULL DEFAULT 0,
    description   TEXT,
    status        INTEGER      NOT NULL DEFAULT 1,
    deleted       INTEGER      NOT NULL DEFAULT 0,
    create_time   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS sys_course;
CREATE TABLE sys_course (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    course_code  VARCHAR(50)  NOT NULL UNIQUE,
    course_name  VARCHAR(100) NOT NULL,
    credit       INTEGER      NOT NULL DEFAULT 0,
    course_type  VARCHAR(20)  NOT NULL DEFAULT '公共课',
    description  TEXT,
    deleted      INTEGER      NOT NULL DEFAULT 0,
    create_time  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME     DEFAULT CURRENT_TIMESTAMP
);

DROP TABLE IF EXISTS sys_major_course;
CREATE TABLE sys_major_course (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    major_id    INTEGER NOT NULL,
    course_id   INTEGER NOT NULL,
    is_required INTEGER NOT NULL DEFAULT 1,
    UNIQUE(major_id, course_id)
);

DROP TABLE IF EXISTS sys_exam_plan;
CREATE TABLE sys_exam_plan (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    plan_code        VARCHAR(50)  NOT NULL UNIQUE,
    plan_name        VARCHAR(200) NOT NULL,
    exam_year        INTEGER      NOT NULL,
    exam_term        VARCHAR(10)  NOT NULL,
    course_id        INTEGER      NOT NULL,
    major_id         INTEGER,
    exam_date        VARCHAR(20),
    start_time       VARCHAR(10),
    end_time         VARCHAR(10),
    location         VARCHAR(200),
    capacity         INTEGER      NOT NULL DEFAULT 100,
    registered_count INTEGER      NOT NULL DEFAULT 0,
    register_start   VARCHAR(20),
    register_end     VARCHAR(20),
    status           VARCHAR(20)  NOT NULL DEFAULT 'DRAFT',
    remark           TEXT,
    deleted          INTEGER      NOT NULL DEFAULT 0,
    create_time      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    update_time      DATETIME     DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_plan_year_term ON sys_exam_plan(exam_year, exam_term);
CREATE INDEX idx_plan_status ON sys_exam_plan(status);

DROP TABLE IF EXISTS sys_registration;
CREATE TABLE sys_registration (
    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id          INTEGER     NOT NULL,
    plan_id             INTEGER     NOT NULL,
    registration_no     VARCHAR(50) NOT NULL UNIQUE,
    admission_ticket_no VARCHAR(50),
    payment_status      VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    audit_remark        VARCHAR(500),
    register_time       DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted             INTEGER     NOT NULL DEFAULT 0,
    create_time         DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time         DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, plan_id)
);
CREATE INDEX idx_reg_student ON sys_registration(student_id);
CREATE INDEX idx_reg_plan ON sys_registration(plan_id);

DROP TABLE IF EXISTS sys_score;
CREATE TABLE sys_score (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id  INTEGER     NOT NULL,
    course_id   INTEGER     NOT NULL,
    plan_id     INTEGER,
    exam_year   INTEGER     NOT NULL,
    exam_term   VARCHAR(10) NOT NULL,
    score       REAL        NOT NULL DEFAULT 0,
    status      VARCHAR(20) NOT NULL DEFAULT 'PASS',
    exam_date   VARCHAR(20),
    deleted     INTEGER     NOT NULL DEFAULT 0,
    create_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(student_id, course_id, exam_year, exam_term)
);
CREATE INDEX idx_score_student ON sys_score(student_id);

DROP TABLE IF EXISTS sys_graduation;
CREATE TABLE sys_graduation (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    student_id   INTEGER     NOT NULL,
    major_id     INTEGER     NOT NULL,
    apply_time   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    audit_remark VARCHAR(500),
    audit_time   DATETIME,
    deleted      INTEGER     NOT NULL DEFAULT 0,
    create_time  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME    DEFAULT CURRENT_TIMESTAMP
);
