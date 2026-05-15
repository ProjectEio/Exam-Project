package com.exam.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
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
 * 策略：
 *   - 通过标志文件 exam.db.initialized 判断是否已初始化过
 *   - 只有当标志文件不存在时，才执行 schema.sql + data.sql
 *   - 执行完成后创建标志文件，防止重复初始化
 *
 * 好处：
 *   - 删掉标志文件即可触发重新初始化（无需修改代码）
 *   - 生产环境不会因为重启而意外清空数据
 */
@Configuration
public class DatabaseInitializer {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitializer.class);

    /** 数据库目录 */
    private static final String DATA_DIR    = "data";
    /** 标志文件路径：放在 data/ 目录下 */
    private static final String MARKER_FILE = DATA_DIR + "/exam.db.initialized";

    private final DataSource dataSource;

    @Value("${shard.base-path:./}")
    private String basePath;

    public DatabaseInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @PostConstruct
    public void init() throws Exception {
        Path marker = Paths.get(MARKER_FILE);

        if (Files.exists(marker)) {
            log.info("[DB Init] 标志文件已存在，跳过初始化（如需重置请删除 {}）", marker.toAbsolutePath());
            return;
        }

        log.info("[DB Init] 首次启动，开始初始化数据库...");

        // ── 确保 data/ 目录存在 ────────────────────────────────
        java.nio.file.Files.createDirectories(Paths.get(DATA_DIR));

        // ── 删除旧数据库文件（保证干净起点）──────────────────────
        deleteIfExists(DATA_DIR + "/exam.db");
        deleteIfExists(DATA_DIR + "/exam.db-wal");
        deleteIfExists(DATA_DIR + "/exam.db-shm");
        // 分片文件（8 分片 × 2 表 = 16 个 .db）
        for (int i = 0; i < 8; i++) {
            deleteIfExists(basePath + "exam_score_"  + i + ".db");
            deleteIfExists(basePath + "exam_score_"  + i + ".db-wal");
            deleteIfExists(basePath + "exam_score_"  + i + ".db-shm");
            deleteIfExists(basePath + "exam_reg_"    + i + ".db");
            deleteIfExists(basePath + "exam_reg_"    + i + ".db-wal");
            deleteIfExists(basePath + "exam_reg_"    + i + ".db-shm");
        }

        // ── 执行 schema + data ────────────────────────────────────
        try (Connection conn = dataSource.getConnection()) {
            log.info("[DB Init] 执行 schema.sql ...");
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/schema.sql"));

            log.info("[DB Init] 执行 data.sql ...");
            ScriptUtils.executeSqlScript(conn,
                    new ClassPathResource("db/data.sql"));
        }

        // ── 写标志文件 ─────────────────────────────────────────────
        Files.writeString(marker,
                "initialized at " + java.time.LocalDateTime.now());
        log.info("[DB Init] 初始化完成，标志文件已写入: {}", marker.toAbsolutePath());
    }

    private void deleteIfExists(String path) {
        File f = new File(path);
        if (f.exists()) {
            boolean ok = f.delete();
            log.info("[DB Init] 删除旧文件: {} → {}", f.getAbsolutePath(), ok ? "成功" : "失败");
        }
    }
}
