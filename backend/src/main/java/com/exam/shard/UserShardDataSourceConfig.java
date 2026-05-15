package com.exam.shard;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 用户分库配置
 * ─────────────────────────────────────────────────────────
 * sys_user 完全迁移到 8 个独立 SQLite 分片（exam_user_0~7.db）
 * 路由键：user_id  Thomas Wang Hash & 7
 *
 * 主库（exam.db）仅保留：sys_major / sys_course / sys_major_course /
 *                         sys_exam_plan 等小型元数据表，全部走内存缓存。
 *
 * 种子用户（admin/teacher/student1-3）在各自分片初始化时写入，
 * data.sql 里的 sys_user INSERT 已移除，避免重复。
 */
@Configuration
public class UserShardDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(UserShardDataSourceConfig.class);

    public static final int NUM_SHARDS = ShardDataSourceConfig.NUM_SHARDS; // 8
    private static final int MASK      = NUM_SHARDS - 1;

    private static final String BCRYPT_123456 =
            "$2a$10$DZyY1hz9uXKjUp4LSXx8UOchNHXGdGutWFtDuGZ5AGXlpx/yi9y6i";

    /** 种子用户 [id, username, role, realName, idCard, phone, gender] */
    private static final Object[][] SEED_USERS = {
        {1L, "admin",    "ADMIN",   "系统管理员", "110101199001011234", "13800000001", "男"},
        {2L, "teacher",  "TEACHER", "王老师",     "110101198501012345", "13800000002", "女"},
        {3L, "student1", "STUDENT", "张三",       "110101200001011001", "13900000001", "男"},
        {4L, "student2", "STUDENT", "李四",       "110101200001012002", "13900000002", "女"},
        {5L, "student3", "STUDENT", "王五",       "110101200001013003", "13900000003", "男"},
    };

    @Value("${shard.base-path:./}")
    private String basePath;

    @Bean("userShardDataSources")
    public DataSource[] userShardDataSources() {
        try { java.nio.file.Files.createDirectories(java.nio.file.Paths.get(basePath)); }
        catch (Exception e) { log.warn("创建分片目录失败: {}", e.getMessage()); }

        DataSource[] sources = new DataSource[NUM_SHARDS];
        for (int i = 0; i < NUM_SHARDS; i++) {
            String dbFile = basePath + "exam_user_" + i + ".db";
            log.info("初始化 User 分片数据源 [{}]: {}", i, dbFile);
            sources[i] = createDataSource(dbFile, "user_shard_" + i);
            initSchema(sources[i], i);
        }
        return sources;
    }

    // ──────────────────────────────────────────────────────
    //  数据源工厂
    // ──────────────────────────────────────────────────────

    private DataSource createDataSource(String dbFile, String poolName) {
        String url = "jdbc:sqlite:" + dbFile
                + "?journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&cache_size=10000"
                + "&temp_store=MEMORY"
                + "&mmap_size=268435456"
                + "&busy_timeout=10000";

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setPoolName(poolName);
        cfg.setMaximumPoolSize(8);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        return new HikariDataSource(cfg);
    }

    // ──────────────────────────────────────────────────────
    //  Schema + 种子数据初始化
    // ──────────────────────────────────────────────────────

    private void initSchema(DataSource ds, int shardIdx) {
        try (Connection conn = ds.getConnection();
             Statement  stmt = conn.createStatement()) {

            // sys_user（无 AUTOINCREMENT，ID 由应用层全局分配）
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sys_user (
                    id          INTEGER PRIMARY KEY,
                    username    TEXT NOT NULL,
                    password    TEXT NOT NULL,
                    role        TEXT NOT NULL DEFAULT 'STUDENT',
                    real_name   TEXT,
                    id_card     TEXT,
                    phone       TEXT,
                    email       TEXT,
                    gender      TEXT,
                    avatar      TEXT,
                    status      INTEGER NOT NULL DEFAULT 1,
                    deleted     INTEGER NOT NULL DEFAULT 0,
                    create_time TEXT    DEFAULT (datetime('now')),
                    update_time TEXT    DEFAULT (datetime('now'))
                )
                """);

            // 索引：username 唯一（未删除），role/status 过滤，real_name 关键字
            stmt.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_usr_uname  ON sys_user(username) WHERE deleted=0");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_usr_role   ON sys_user(role,    deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_usr_status ON sys_user(status,  deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_usr_name   ON sys_user(real_name, deleted)");
                        stmt.execute("""
                                CREATE TABLE IF NOT EXISTS user_count_cache (
                                        key   TEXT PRIMARY KEY,
                                        value INTEGER NOT NULL DEFAULT 0
                                )
                                """);
                        stmt.execute("INSERT OR IGNORE INTO user_count_cache(key,value) VALUES('total_user',0)");
                        stmt.execute("INSERT OR IGNORE INTO user_count_cache(key,value) VALUES('role_admin',0)");
                        stmt.execute("INSERT OR IGNORE INTO user_count_cache(key,value) VALUES('role_teacher',0)");
                        stmt.execute("INSERT OR IGNORE INTO user_count_cache(key,value) VALUES('role_student',0)");
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_user_ins
                                AFTER INSERT ON sys_user
                                WHEN NEW.deleted=0
                                BEGIN
                                    UPDATE user_count_cache SET value=value+1 WHERE key='total_user';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_admin'   AND NEW.role='ADMIN';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_teacher' AND NEW.role='TEACHER';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_student' AND NEW.role='STUDENT';
                                END
                                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_user_soft_delete
                                AFTER UPDATE OF deleted ON sys_user
                                WHEN NEW.deleted=1 AND OLD.deleted=0
                                BEGIN
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='total_user';
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_admin'   AND OLD.role='ADMIN';
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_teacher' AND OLD.role='TEACHER';
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_student' AND OLD.role='STUDENT';
                                END
                                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_user_restore
                                AFTER UPDATE OF deleted ON sys_user
                                WHEN NEW.deleted=0 AND OLD.deleted=1
                                BEGIN
                                    UPDATE user_count_cache SET value=value+1 WHERE key='total_user';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_admin'   AND NEW.role='ADMIN';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_teacher' AND NEW.role='TEACHER';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_student' AND NEW.role='STUDENT';
                                END
                                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_user_role_change
                                AFTER UPDATE OF role ON sys_user
                                WHEN NEW.deleted=0 AND OLD.deleted=0 AND NEW.role<>OLD.role
                                BEGIN
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_admin'   AND OLD.role='ADMIN';
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_teacher' AND OLD.role='TEACHER';
                                    UPDATE user_count_cache SET value=MAX(0,value-1) WHERE key='role_student' AND OLD.role='STUDENT';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_admin'   AND NEW.role='ADMIN';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_teacher' AND NEW.role='TEACHER';
                                    UPDATE user_count_cache SET value=value+1 WHERE key='role_student' AND NEW.role='STUDENT';
                                END
                                """);

            // 插入属于本分片的种子用户
            String sql = "INSERT OR IGNORE INTO sys_user"
                    + "(id,username,password,role,real_name,id_card,phone,gender,status)"
                    + " VALUES(?,?,?,?,?,?,?,?,1)";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                for (Object[] u : SEED_USERS) {
                    long uid = (Long) u[0];
                    if (routeId(uid) == shardIdx) {
                        ps.setLong(1, uid);
                        ps.setString(2, (String) u[1]);
                        ps.setString(3, BCRYPT_123456);
                        ps.setString(4, (String) u[2]);
                        ps.setString(5, (String) u[3]);
                        ps.setString(6, (String) u[4]);
                        ps.setString(7, (String) u[5]);
                        ps.setString(8, (String) u[6]);
                        ps.executeUpdate();
                    }
                }
            }
            stmt.execute("UPDATE user_count_cache SET value=(SELECT COUNT(*) FROM sys_user WHERE deleted=0) WHERE key='total_user'");
            stmt.execute("UPDATE user_count_cache SET value=(SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND role='ADMIN') WHERE key='role_admin'");
            stmt.execute("UPDATE user_count_cache SET value=(SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND role='TEACHER') WHERE key='role_teacher'");
            stmt.execute("UPDATE user_count_cache SET value=(SELECT COUNT(*) FROM sys_user WHERE deleted=0 AND role='STUDENT') WHERE key='role_student'");
            log.info("User 分片 [{}] schema 就绪", shardIdx);
        } catch (SQLException e) {
            throw new RuntimeException("User shard[" + shardIdx + "] 初始化失败", e);
        }
    }

    /** Thomas Wang hash（与 ShardRouter 保持一致） */
    static int routeId(long id) {
        long h = id;
        h = (~h) + (h << 21);
        h =   h  ^ (h >>> 24);
        h = (h + (h << 3)) + (h << 8);
        h =   h  ^ (h >>> 14);
        h = (h + (h << 2)) + (h << 4);
        h =   h  ^ (h >>> 28);
        h =   h  + (h << 31);
        return (int)(Math.abs(h) & MASK);
    }
}
