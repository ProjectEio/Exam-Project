package com.exam.config;

import com.exam.benchmark.DataGeneratorService;
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

/**
 * 数据库首次初始化器
 * ──────────────────────────────────────────────────────────
 * 策略（双标志文件）：
 *   exam.db.initialized   → schema + seed data 已执行
 *   exam.data.generated   → 2000 万分片数据已生成
 *
 * 删除对应标志文件可触发重新执行对应阶段。
 */
@Configuration
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    private static final String DATA_DIR       = "data";
    private static final String MARKER_SCHEMA  = DATA_DIR + "/exam.db.initialized";
    private static final String MARKER_DATA    = DATA_DIR + "/exam.data.generated";

    private final DataSource dataSource;

    @Lazy
    @Autowired
    private DataGeneratorService dataGeneratorService;

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

            // 删除旧 db 文件
            deleteIfExists(DATA_DIR + "/exam.db");
            deleteIfExists(DATA_DIR + "/exam.db-wal");
            deleteIfExists(DATA_DIR + "/exam.db-shm");
            for (int i = 0; i < 8; i++) {
                deleteIfExists(basePath + "exam_score_" + i + ".db");
                deleteIfExists(basePath + "exam_score_" + i + ".db-wal");
                deleteIfExists(basePath + "exam_score_" + i + ".db-shm");
                deleteIfExists(basePath + "exam_reg_"   + i + ".db");
                deleteIfExists(basePath + "exam_reg_"   + i + ".db-wal");
                deleteIfExists(basePath + "exam_reg_"   + i + ".db-shm");
            }
            // 同时清除数据标志（重建 schema 必须重新生成数据）
            Files.deleteIfExists(Paths.get(MARKER_DATA));

            try (Connection conn = dataSource.getConnection()) {
                log.info("[DB Init] 执行 schema.sql ...");
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/schema.sql"));
                log.info("[DB Init] 执行 data.sql ...");
                ScriptUtils.executeSqlScript(conn, new ClassPathResource("db/data.sql"));
            }

            Files.writeString(schemaMarker, "initialized at " + java.time.LocalDateTime.now());
            log.info("[DB Init] schema 初始化完成: {}", schemaMarker.toAbsolutePath());
        }

        // ── 阶段 2：2000 万分片数据（异步，后台生成）────────────
        Path dataMarker = Paths.get(MARKER_DATA);
        if (Files.exists(dataMarker)) {
            log.info("[DB Init] 2000万数据已存在，跳过生成（删除 {} 可重新生成）", dataMarker.toAbsolutePath());
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
                            + "\nusers="  + dataGeneratorService.userInserted.get()
                            + "\nscores=" + dataGeneratorService.scoreInserted.get()
                            + "\nregs="   + dataGeneratorService.regInserted.get());
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
}


