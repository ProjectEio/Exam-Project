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
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * 分库分表数据源配置
 * ─────────────────────────────────────────────────────────
 * 分片设计：
 *   sys_score        → 8 个 SQLite 文件（exam_score_0~7.db）
 *   sys_registration → 8 个 SQLite 文件（exam_reg_0~7.db）
 *
 * 路由键：student_id Hash 扰动后对 8 取模
 *
 * 8 分片好处：
 *   - 每个 .db 文件约 250 万行，WAL 写入锁竞争更少
 *   - HikariCP 连接池独立，8 路并行 IO
 *   - 单分片 B 树深度更浅，索引查找更快
 *
 * SQLite 性能 PRAGMA（每条连接建立后自动执行）：
 *   journal_mode=WAL   → 读写并发，单写多读
 *   synchronous=NORMAL → 平衡安全性与速度
 *   cache_size=10000   → 10000 页 = 约 40MB per shard
 *   temp_store=MEMORY  → 临时表存内存
 *   mmap_size=256MB    → 内存映射 IO，降低系统调用开销
 */
@Configuration
public class ShardDataSourceConfig {

    private static final Logger log = LoggerFactory.getLogger(ShardDataSourceConfig.class);

    /** 分片数（必须是 2 的幂；8 片 × 2 表 = 16 个 .db 文件） */
    public static final int NUM_SHARDS = 8;

    @Value("${shard.base-path:./}")
    private String basePath;

    // ═══════════════════════════════════════════════════
    //  Score 分片数据源
    // ═══════════════════════════════════════════════════

    @Bean("scoreShardDataSources")
    public DataSource[] scoreShardDataSources() {
        // 确保 data/ 目录存在（分片文件初始化早于 DatabaseInitializer）
        try { java.nio.file.Files.createDirectories(java.nio.file.Paths.get(basePath)); }
        catch (Exception e) { log.warn("创建分片目录失败: {}", e.getMessage()); }
        DataSource[] sources = new DataSource[NUM_SHARDS];
        for (int i = 0; i < NUM_SHARDS; i++) {
            String dbFile = basePath + "exam_score_" + i + ".db";
            log.info("初始化 Score 分片数据源 [{}]: {}", i, dbFile);
            sources[i] = createSQLiteDataSource(dbFile, "score_shard_" + i, 8);
            initScoreSchema(sources[i], i);
        }
        return sources;
    }

    // ═══════════════════════════════════════════════════
    //  Registration 分片数据源
    // ═══════════════════════════════════════════════════

    @Bean("regShardDataSources")
    public DataSource[] regShardDataSources() {
        DataSource[] sources = new DataSource[NUM_SHARDS];
        for (int i = 0; i < NUM_SHARDS; i++) {
            String dbFile = basePath + "exam_reg_" + i + ".db";
            log.info("初始化 Registration 分片数据源 [{}]: {}", i, dbFile);
            sources[i] = createSQLiteDataSource(dbFile, "reg_shard_" + i, 8);
            initRegSchema(sources[i], i);
        }
        return sources;
    }

    // ═══════════════════════════════════════════════════
    //  工厂方法
    // ═══════════════════════════════════════════════════

    private DataSource createSQLiteDataSource(String dbFile, String poolName, int poolSize) {
        // 拼接 SQLite PRAGMA 参数到 URL（SQLite JDBC 支持 URL 参数形式）
        String url = "jdbc:sqlite:" + dbFile
                + "?journal_mode=WAL"
                + "&synchronous=NORMAL"
                + "&cache_size=10000"
                + "&temp_store=MEMORY"
                + "&mmap_size=268435456"   // 256 MB
                + "&busy_timeout=10000";   // 等待锁最多 10s

        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl(url);
        cfg.setDriverClassName("org.sqlite.JDBC");
        cfg.setPoolName(poolName);
        cfg.setMaximumPoolSize(poolSize);
        cfg.setMinimumIdle(2);
        cfg.setConnectionTestQuery("SELECT 1");
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        return new HikariDataSource(cfg);
    }

    // ═══════════════════════════════════════════════════
    //  Schema 初始化
    // ═══════════════════════════════════════════════════

    private void initScoreSchema(DataSource ds, int idx) {
        try (Connection conn = ds.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sys_score (
                    id          INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id  INTEGER     NOT NULL,
                    course_id   INTEGER     NOT NULL,
                    plan_id     INTEGER,
                    exam_year   INTEGER     NOT NULL,
                    exam_term   VARCHAR(10) NOT NULL,
                    score       REAL        NOT NULL DEFAULT 0,
                    status      VARCHAR(20) NOT NULL DEFAULT 'PASS',
                    exam_date   VARCHAR(20),
                    student_name TEXT,
                    course_code  TEXT,
                    course_name  TEXT,
                    deleted     INTEGER     NOT NULL DEFAULT 0,
                    create_time TEXT        DEFAULT (datetime('now')),
                    update_time TEXT        DEFAULT (datetime('now'))
                )
                """);
            ensureColumn(stmt, "sys_score", "student_name", "TEXT");
            ensureColumn(stmt, "sys_score", "course_code",  "TEXT");
            ensureColumn(stmt, "sys_score", "course_name",  "TEXT");
            // 覆盖业务查询的复合索引
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_stu     ON sys_score(student_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_stu_del ON sys_score(student_id, deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_cid     ON sys_score(course_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_yr_tm   ON sys_score(exam_year, exam_term)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_status  ON sys_score(status, deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_page     ON sys_score(deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_page_stu ON sys_score(student_id, deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_page_cid ON sys_score(course_id, deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_sc_page_sts ON sys_score(status, deleted, id DESC)");
            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_sc_unique
                ON sys_score(student_id, course_id, exam_year, exam_term)
                """);

            // ── 计数缓存表（避免全量 COUNT(*) 扫描）───────────────────
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shard_count_cache (
                    key   TEXT PRIMARY KEY,
                    value INTEGER NOT NULL DEFAULT 0
                )
                """);
            // 初始化两个计数器
            stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_score',0)");
            stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_pass',0)");

            // 写入时自动 +1（未删除）
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_score_ins
                AFTER INSERT ON sys_score
                BEGIN
                  UPDATE shard_count_cache SET value=value+1 WHERE key='total_score';
                  UPDATE shard_count_cache SET value=value+1 WHERE key='total_pass'
                    AND NEW.status='PASS';
                END
                """);
            // 软删除时自动 -1
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_score_del
                AFTER UPDATE OF deleted ON sys_score
                WHEN NEW.deleted=1 AND OLD.deleted=0
                BEGIN
                  UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_score';
                  UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_pass'
                    AND OLD.status='PASS';
                END
                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_score_status
                                AFTER UPDATE OF status ON sys_score
                                WHEN NEW.deleted=0 AND OLD.deleted=0 AND NEW.status<>OLD.status
                                BEGIN
                                    UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_pass'
                                        AND OLD.status='PASS';
                                    UPDATE shard_count_cache SET value=value+1 WHERE key='total_pass'
                                        AND NEW.status='PASS';
                                END
                                """);
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_score WHERE deleted=0) WHERE key='total_score'");
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_score WHERE deleted=0 AND status='PASS') WHERE key='total_pass'");
            log.info("Score 分片 [{}] schema 就绪", idx);
        } catch (SQLException e) {
            throw new RuntimeException("Score shard[" + idx + "] 初始化失败", e);
        }
    }

    private void initRegSchema(DataSource ds, int idx) {
        try (Connection conn = ds.getConnection();
             Statement  stmt = conn.createStatement()) {
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS sys_registration (
                    id                  INTEGER PRIMARY KEY AUTOINCREMENT,
                    student_id          INTEGER     NOT NULL,
                    plan_id             INTEGER     NOT NULL,
                    registration_no     VARCHAR(50) NOT NULL,
                    admission_ticket_no VARCHAR(50),
                    payment_status      VARCHAR(20) NOT NULL DEFAULT 'UNPAID',
                    status              VARCHAR(20) NOT NULL DEFAULT 'PENDING',
                    audit_remark        VARCHAR(500),
                    student_name        TEXT,
                    student_id_card     TEXT,
                    plan_code           TEXT,
                    plan_name           TEXT,
                    course_id           INTEGER,
                    course_code         TEXT,
                    course_name         TEXT,
                    exam_year           INTEGER,
                    exam_term           TEXT,
                    exam_date           TEXT,
                    exam_location       TEXT,
                    start_time          TEXT,
                    end_time            TEXT,
                    register_time       TEXT        DEFAULT (datetime('now')),
                    deleted             INTEGER     NOT NULL DEFAULT 0,
                    create_time         TEXT        DEFAULT (datetime('now')),
                    update_time         TEXT        DEFAULT (datetime('now'))
                )
                """);
            ensureColumn(stmt, "sys_registration", "student_name",    "TEXT");
            ensureColumn(stmt, "sys_registration", "student_id_card", "TEXT");
            ensureColumn(stmt, "sys_registration", "plan_code",       "TEXT");
            ensureColumn(stmt, "sys_registration", "plan_name",       "TEXT");
            ensureColumn(stmt, "sys_registration", "course_id",       "INTEGER");
            ensureColumn(stmt, "sys_registration", "course_code",     "TEXT");
            ensureColumn(stmt, "sys_registration", "course_name",     "TEXT");
            ensureColumn(stmt, "sys_registration", "exam_year",       "INTEGER");
            ensureColumn(stmt, "sys_registration", "exam_term",       "TEXT");
            ensureColumn(stmt, "sys_registration", "exam_date",       "TEXT");
            ensureColumn(stmt, "sys_registration", "exam_location",   "TEXT");
            ensureColumn(stmt, "sys_registration", "start_time",      "TEXT");
            ensureColumn(stmt, "sys_registration", "end_time",        "TEXT");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_stu     ON sys_registration(student_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_stu_del ON sys_registration(student_id, deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_plan    ON sys_registration(plan_id)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_status  ON sys_registration(status, deleted)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_page      ON sys_registration(deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_page_stu  ON sys_registration(student_id, deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_page_plan ON sys_registration(plan_id, deleted, id DESC)");
            stmt.execute("CREATE INDEX IF NOT EXISTS idx_rg_page_sts  ON sys_registration(status, payment_status, deleted, id DESC)");
            stmt.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_rg_no
                ON sys_registration(registration_no)
                """);
            stmt.execute("""
                CREATE TABLE IF NOT EXISTS shard_count_cache (
                    key   TEXT PRIMARY KEY,
                    value INTEGER NOT NULL DEFAULT 0
                )
                """);
            stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_reg',0)");
            stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_reg_approved',0)");
                        stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_reg_pending',0)");
            stmt.execute("INSERT OR IGNORE INTO shard_count_cache(key,value) VALUES('total_reg_paid',0)");
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_reg_ins
                AFTER INSERT ON sys_registration
                BEGIN
                  UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg';
                  UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_approved'
                    AND NEW.status='APPROVED';
                                    UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_pending'
                                        AND NEW.status='PENDING';
                  UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_paid'
                    AND NEW.payment_status='PAID';
                END
                """);
            stmt.execute("""
                CREATE TRIGGER IF NOT EXISTS trg_reg_del
                AFTER UPDATE OF deleted ON sys_registration
                WHEN NEW.deleted=1 AND OLD.deleted=0
                BEGIN
                  UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg';
                  UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_approved'
                    AND OLD.status='APPROVED';
                                    UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_pending'
                                        AND OLD.status='PENDING';
                  UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_paid'
                    AND OLD.payment_status='PAID';
                END
                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_reg_status_upd
                                AFTER UPDATE OF status ON sys_registration
                                WHEN NEW.deleted=0 AND OLD.deleted=0 AND NEW.status<>OLD.status
                                BEGIN
                                    UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_approved'
                                        AND OLD.status='APPROVED';
                                    UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_pending'
                                        AND OLD.status='PENDING';
                                    UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_approved'
                                        AND NEW.status='APPROVED';
                                    UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_pending'
                                        AND NEW.status='PENDING';
                                END
                                """);
                        stmt.execute("""
                                CREATE TRIGGER IF NOT EXISTS trg_reg_payment_upd
                                AFTER UPDATE OF payment_status ON sys_registration
                                WHEN NEW.deleted=0 AND OLD.deleted=0 AND NEW.payment_status<>OLD.payment_status
                                BEGIN
                                    UPDATE shard_count_cache SET value=MAX(0,value-1) WHERE key='total_reg_paid'
                                        AND OLD.payment_status='PAID';
                                    UPDATE shard_count_cache SET value=value+1 WHERE key='total_reg_paid'
                                        AND NEW.payment_status='PAID';
                                END
                                """);
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_registration WHERE deleted=0) WHERE key='total_reg'");
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_registration WHERE deleted=0 AND status='APPROVED') WHERE key='total_reg_approved'");
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_registration WHERE deleted=0 AND status='PENDING') WHERE key='total_reg_pending'");
                        stmt.execute("UPDATE shard_count_cache SET value=(SELECT COUNT(*) FROM sys_registration WHERE deleted=0 AND payment_status='PAID') WHERE key='total_reg_paid'");
            log.info("Registration 分片 [{}] schema 就绪", idx);
        } catch (SQLException e) {
            throw new RuntimeException("Registration shard[" + idx + "] 初始化失败", e);
        }
    }

    private void ensureColumn(Statement stmt, String tableName, String columnName, String ddl) throws SQLException {
        if (hasColumn(stmt, tableName, columnName)) return;
        stmt.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + ddl);
    }

    private boolean hasColumn(Statement stmt, String tableName, String columnName) throws SQLException {
        try (ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
