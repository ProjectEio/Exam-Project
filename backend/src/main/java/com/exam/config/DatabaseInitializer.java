package com.exam.config;

import com.exam.benchmark.DataGeneratorService;
import com.exam.shard.RegistrationShardRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ScriptUtils;

import jakarta.annotation.PostConstruct;
import javax.sql.DataSource;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.Statement;

/**
 * 数据库首次初始化器
 * ──────────────────────────────────────────────────────────
 * 策略（双标志文件）：
 *   exam.meta.initialized → 元数据 schema + seed data 已执行
 *   exam.data.generated   → 2000 万分片数据已生成
 *
 * 删除对应标志文件可触发重新执行对应阶段。
 */
@Configuration
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String DATA_DIR       = "data";
    private static final String MARKER_SCHEMA  = DATA_DIR + "/exam.meta.initialized";
    private static final String MARKER_DATA    = DATA_DIR + "/exam.data.generated";
    private static final String MARKER_TICKET  = DATA_DIR + "/exam.ticket.backfilled";

    private final DataSource dataSource;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("userShardDataSources")
    private DataSource[] userShardDataSources;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("scoreShardDataSources")
    private DataSource[] scoreShardDataSources;

    @Autowired
    @org.springframework.beans.factory.annotation.Qualifier("regShardDataSources")
    private DataSource[] regShardDataSources;

    @Lazy
    @Autowired
    private DataGeneratorService dataGeneratorService;

    @Autowired
    private RegistrationShardRepository registrationShardRepository;

    @Value("${shard.base-path:./}")
    private String basePath;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() throws Exception {
        // ── 确保 data/ 目录存在 ────────────────────────────────
        Files.createDirectories(Paths.get(DATA_DIR));

        // ── 阶段 1：schema + seed data ─────────────────────────
        Path schemaMarker = Paths.get(MARKER_SCHEMA);
        if (Files.exists(schemaMarker)) {
            log.info("[DB Init] schema 已初始化，跳过（删除 {} 可重置）", schemaMarker.toAbsolutePath());
        } else {
            log.info("[DB Init] 首次启动，执行 schema + seed data...");

            // 清理旧非分片主库文件
            deleteIfExists(DATA_DIR + "/exam.db");
            deleteIfExists(DATA_DIR + "/exam.db-wal");
            deleteIfExists(DATA_DIR + "/exam.db-shm");
            resetUserShards();
            resetScoreShards();
            resetRegistrationShards();
            // 同时清除数据标志（重建 schema 必须重新生成数据）
            Files.deleteIfExists(Paths.get(MARKER_DATA));
            Files.deleteIfExists(Paths.get(MARKER_TICKET));

            try (Connection conn = dataSource.getConnection()) {
                log.info("[DB Init] 执行 schema-meta.sql ...");
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema-meta.sql"));
                log.info("[DB Init] 执行 data-meta.sql ...");
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/data-meta.sql"));
            }
            dataGeneratorService.ensureBenchmarkMetadata();

            Files.writeString(schemaMarker, "initialized at " + java.time.LocalDateTime.now());
            log.info("[DB Init] schema 初始化完成: {}", schemaMarker.toAbsolutePath());
        }

        // ── 阶段 2：2000 万分片数据（异步，后台生成）────────────
        Path dataMarker = Paths.get(MARKER_DATA);
        if (Files.exists(dataMarker)) {
            log.info("[DB Init] 2000万数据已存在，跳过生成（删除 {} 可重新生成）", dataMarker.toAbsolutePath());
            scheduleTicketBackfillIfNeeded();
        } else {
            log.info("[DB Init] 启动后台 2000万数据生成任务...");
            Thread genThread = new Thread(() -> {
                try {
                    log.info("===== [DataGen] 开始生成 10万学生账号 =====");
                    dataGeneratorService.generateUsers();
                    log.info("===== [DataGen] 开始生成 Score 2000万 =====");
                    dataGeneratorService.generateScores();
                    log.info("===== [DataGen] 开始生成 Registration 2000万 =====");
                    dataGeneratorService.generateRegistrations();
                    // 写数据标志文件
                    Files.writeString(Paths.get(MARKER_DATA),
                            "generated at " + java.time.LocalDateTime.now()
                            + "\nusers="  + dataGeneratorService.getUserInserted()
                            + "\nscores=" + dataGeneratorService.getScoreInserted()
                            + "\nregs="   + dataGeneratorService.getRegInserted());
                    Files.writeString(Paths.get(MARKER_TICKET),
                            "ticket source ok at " + java.time.LocalDateTime.now()
                            + "\nmode=generated_with_ticket");
                    log.info("===== [DataGen] 全量数据生成完成，标志已写入 =====");
                } catch (Exception e) {
                    log.error("[DataGen] 数据生成失败", e);
                }
            }, "init-data-gen");
            genThread.setDaemon(true);
            genThread.start();
        }
    }

    private void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists()) {
            boolean ok = f.delete();
            log.info("[DB Init] 删除旧文件: {} → {}", f.getAbsolutePath(), ok ? "成功" : "失败");
        }
    }

    private void resetUserShards() {
        for (DataSource ds : userShardDataSources) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sys_user WHERE id > 5");
            } catch (Exception e) {
                log.warn("[DB Init] 重置 user 分片失败: {}", e.getMessage());
            }
        }
    }

    private void resetScoreShards() {
        for (DataSource ds : scoreShardDataSources) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sys_score");
                stmt.execute("DELETE FROM sqlite_sequence WHERE name='sys_score'");
                stmt.execute("UPDATE shard_count_cache SET value=0 WHERE key IN ('total_score','total_pass')");
            } catch (Exception e) {
                log.warn("[DB Init] 重置 score 分片失败: {}", e.getMessage());
            }
        }
    }

    private void resetRegistrationShards() {
        for (DataSource ds : regShardDataSources) {
            try (Connection conn = ds.getConnection(); Statement stmt = conn.createStatement()) {
                stmt.execute("DELETE FROM sys_registration");
                stmt.execute("DELETE FROM sqlite_sequence WHERE name='sys_registration'");
                stmt.execute("UPDATE shard_count_cache SET value=0 WHERE key IN ('total_reg','total_reg_approved','total_reg_pending','total_reg_paid')");
            } catch (Exception e) {
                log.warn("[DB Init] 重置 reg 分片失败: {}", e.getMessage());
            }
        }
    }

    private void scheduleTicketBackfillIfNeeded() {
        Path ticketMarker = Paths.get(MARKER_TICKET);
        if (Files.exists(ticketMarker)) {
            log.info("[DB Init] 准考证号已回填，跳过（删除 {} 可重新执行）", ticketMarker.toAbsolutePath());
            return;
        }

        Thread ticketThread = new Thread(() -> {
            try {
                log.info("[DB Init] 开始回填历史准考证号...");
                long updated = registrationShardRepository.backfillMissingAdmissionTicketNos();
                Files.writeString(ticketMarker,
                        "backfilled at " + java.time.LocalDateTime.now()
                        + "\nupdated=" + updated);
                log.info("[DB Init] 历史准考证号回填完成，更新 {} 条", updated);
            } catch (Exception e) {
                log.error("[DB Init] 历史准考证号回填失败", e);
            }
        }, "ticket-backfill");
        ticketThread.setDaemon(true);
        ticketThread.start();
    }
}


