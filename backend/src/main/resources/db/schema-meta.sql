-- 元数据初始化脚本（挂载到分片文件 exam_user_0.db）

DROP TABLE IF EXISTS sys_major_course;
DROP TABLE IF EXISTS sys_exam_plan;
DROP TABLE IF EXISTS sys_graduation;
DROP TABLE IF EXISTS sys_course;
DROP TABLE IF EXISTS sys_major;
DROP TABLE IF EXISTS sys_registration;
DROP TABLE IF EXISTS sys_score;

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

CREATE TABLE sys_major_course (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    major_id    INTEGER NOT NULL,
    course_id   INTEGER NOT NULL,
    is_required INTEGER NOT NULL DEFAULT 1,
    UNIQUE(major_id, course_id)
);
CREATE INDEX idx_mc_major  ON sys_major_course(major_id);
CREATE INDEX idx_mc_course ON sys_major_course(course_id);

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
CREATE INDEX idx_plan_year_term   ON sys_exam_plan(exam_year, exam_term);
CREATE INDEX idx_plan_status      ON sys_exam_plan(status, deleted);
CREATE INDEX idx_plan_course      ON sys_exam_plan(course_id, deleted);
CREATE INDEX idx_plan_date_status ON sys_exam_plan(exam_date, status, deleted);

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
