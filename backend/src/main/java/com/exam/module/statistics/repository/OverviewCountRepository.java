package com.exam.module.statistics.repository;

import com.exam.module.statistics.dto.OverviewVO;
import jakarta.annotation.PostConstruct;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class OverviewCountRepository {

    public static final String KEY_USER_TOTAL = "user_total";
    public static final String KEY_USER_STUDENT = "user_student";
    public static final String KEY_MAJOR_TOTAL = "major_total";
    public static final String KEY_COURSE_TOTAL = "course_total";
    public static final String KEY_PLAN_TOTAL = "plan_total";
    public static final String KEY_PLAN_PUBLISHED = "plan_published";
    public static final String KEY_REG_TOTAL = "reg_total";
    public static final String KEY_REG_APPROVED = "reg_approved";
    public static final String KEY_SCORE_TOTAL = "score_total";
    public static final String KEY_SCORE_PASS = "score_pass";

    private final JdbcTemplate jdbcTemplate;

    public OverviewCountRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @PostConstruct
    public void init() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS overview_count_cache (
                key   TEXT PRIMARY KEY,
                value INTEGER NOT NULL DEFAULT 0
            )
            """);
        ensureKey(KEY_USER_TOTAL);
        ensureKey(KEY_USER_STUDENT);
        ensureKey(KEY_MAJOR_TOTAL);
        ensureKey(KEY_COURSE_TOTAL);
        ensureKey(KEY_PLAN_TOTAL);
        ensureKey(KEY_PLAN_PUBLISHED);
        ensureKey(KEY_REG_TOTAL);
        ensureKey(KEY_REG_APPROVED);
        ensureKey(KEY_SCORE_TOTAL);
        ensureKey(KEY_SCORE_PASS);
    }

    public void ensureMetadataSupport() {
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_major_ins
            AFTER INSERT ON sys_major
            WHEN NEW.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='major_total';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_major_delete
            AFTER UPDATE OF deleted ON sys_major
            WHEN NEW.deleted=1 AND OLD.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=MAX(0,value-1) WHERE key='major_total';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_major_restore
            AFTER UPDATE OF deleted ON sys_major
            WHEN NEW.deleted=0 AND OLD.deleted=1
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='major_total';
            END
            """);

        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_course_ins
            AFTER INSERT ON sys_course
            WHEN NEW.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='course_total';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_course_delete
            AFTER UPDATE OF deleted ON sys_course
            WHEN NEW.deleted=1 AND OLD.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=MAX(0,value-1) WHERE key='course_total';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_course_restore
            AFTER UPDATE OF deleted ON sys_course
            WHEN NEW.deleted=0 AND OLD.deleted=1
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='course_total';
            END
            """);

        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_plan_ins
            AFTER INSERT ON sys_exam_plan
            WHEN NEW.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='plan_total';
              UPDATE overview_count_cache SET value=value+1 WHERE key='plan_published' AND NEW.status='PUBLISHED';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_plan_delete
            AFTER UPDATE OF deleted ON sys_exam_plan
            WHEN NEW.deleted=1 AND OLD.deleted=0
            BEGIN
              UPDATE overview_count_cache SET value=MAX(0,value-1) WHERE key='plan_total';
              UPDATE overview_count_cache SET value=MAX(0,value-1) WHERE key='plan_published' AND OLD.status='PUBLISHED';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_plan_restore
            AFTER UPDATE OF deleted ON sys_exam_plan
            WHEN NEW.deleted=0 AND OLD.deleted=1
            BEGIN
              UPDATE overview_count_cache SET value=value+1 WHERE key='plan_total';
              UPDATE overview_count_cache SET value=value+1 WHERE key='plan_published' AND NEW.status='PUBLISHED';
            END
            """);
        jdbcTemplate.execute("""
            CREATE TRIGGER IF NOT EXISTS trg_overview_plan_status
            AFTER UPDATE OF status ON sys_exam_plan
            WHEN NEW.deleted=0 AND OLD.deleted=0 AND NEW.status<>OLD.status
            BEGIN
              UPDATE overview_count_cache SET value=MAX(0,value-1) WHERE key='plan_published' AND OLD.status='PUBLISHED';
              UPDATE overview_count_cache SET value=value+1 WHERE key='plan_published' AND NEW.status='PUBLISHED';
            END
            """);

        refreshMetadataCounts();
    }

    public void refreshMetadataCounts() {
        put(KEY_MAJOR_TOTAL, queryCount("SELECT COUNT(*) FROM sys_major WHERE deleted=0"));
        put(KEY_COURSE_TOTAL, queryCount("SELECT COUNT(*) FROM sys_course WHERE deleted=0"));
        put(KEY_PLAN_TOTAL, queryCount("SELECT COUNT(*) FROM sys_exam_plan WHERE deleted=0"));
        put(KEY_PLAN_PUBLISHED, queryCount("SELECT COUNT(*) FROM sys_exam_plan WHERE deleted=0 AND status='PUBLISHED'"));
    }

    public void refreshUserCounts(long total, long studentTotal) {
        put(KEY_USER_TOTAL, total);
        put(KEY_USER_STUDENT, studentTotal);
    }

    public void refreshScoreCounts(long total, long passTotal) {
        put(KEY_SCORE_TOTAL, total);
        put(KEY_SCORE_PASS, passTotal);
    }

    public void refreshRegistrationCounts(long total, long approvedTotal) {
        put(KEY_REG_TOTAL, total);
        put(KEY_REG_APPROVED, approvedTotal);
    }

    public OverviewVO loadOverview() {
        Map<String, Long> counts = loadAll();
        long scoreTotal = counts.getOrDefault(KEY_SCORE_TOTAL, 0L);
        long scorePass = counts.getOrDefault(KEY_SCORE_PASS, 0L);

        OverviewVO vo = new OverviewVO();
        vo.setUserCount(counts.getOrDefault(KEY_USER_TOTAL, 0L));
        vo.setStudentCount(counts.getOrDefault(KEY_USER_STUDENT, 0L));
        vo.setMajorCount(counts.getOrDefault(KEY_MAJOR_TOTAL, 0L));
        vo.setCourseCount(counts.getOrDefault(KEY_COURSE_TOTAL, 0L));
        vo.setPlanCount(counts.getOrDefault(KEY_PLAN_TOTAL, 0L));
        vo.setPublishedPlanCount(counts.getOrDefault(KEY_PLAN_PUBLISHED, 0L));
        vo.setRegistrationCount(counts.getOrDefault(KEY_REG_TOTAL, 0L));
        vo.setApprovedCount(counts.getOrDefault(KEY_REG_APPROVED, 0L));
        vo.setScoreCount(scoreTotal);
        vo.setPassRate(scoreTotal == 0 ? 0.0 : Math.round(scorePass * 1000.0 / scoreTotal) / 10.0);
        return vo;
    }

    private Map<String, Long> loadAll() {
        Map<String, Long> result = new LinkedHashMap<>();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList("SELECT key,value FROM overview_count_cache");
        for (Map<String, Object> row : rows) {
            Object key = row.get("key");
            Object value = row.get("value");
            if (key != null) {
                result.put(key.toString(), value instanceof Number n ? n.longValue() : 0L);
            }
        }
        return result;
    }

    private void ensureKey(String key) {
        jdbcTemplate.update("INSERT OR IGNORE INTO overview_count_cache(key,value) VALUES(?,0)", key);
    }

    private void put(String key, long value) {
        jdbcTemplate.update("INSERT INTO overview_count_cache(key,value) VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value",
                key, value);
    }

    private long queryCount(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }
}
