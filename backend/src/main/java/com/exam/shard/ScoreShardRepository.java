package com.exam.shard;

import com.exam.cache.MemoryCacheManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.*;
import java.util.concurrent.*;

/**
 * 分片 Score 仓库（8 分片 + 内存缓存）
 * ─────────────────────────────────────────────────────────
 * 缓存策略：
 *   findByStudentId           → shard:sc:stu:{studentId}
 *   findByStudentAndTerm      → shard:sc:stu:{sid}:y{year}:t{term}
 *   findByStudentCourseYT     → shard:sc:stu:{sid}:c{cid}:y{y}:t{t}
 *   countByStudentId          → shard:sc:cnt:{studentId}
 *   countAll                  → shard:sc:cnt:all（读计数缓存表）
 *   globalStats               → shard:sc:stats:global
 *   pageAcrossShards          → PAGE_CACHE shard:sc:page:{p}:{s}
 *
 * 写/删后精准失效：删除 stu:{sid}、cnt:{sid}，清全局统计和分页缓存
 */
@Repository
public class ScoreShardRepository {

    private static final Logger log = LoggerFactory.getLogger(ScoreShardRepository.class);
    private static final String CACHE = MemoryCacheManager.SCORE_LIST_CACHE;

    private final JdbcTemplate[]    templates;
    private final ShardRouter        router;
    private final ExecutorService    executor;
    private final MemoryCacheManager cache;

    @Autowired
    public ScoreShardRepository(
            @Qualifier("scoreShardDataSources") DataSource[] dataSources,
            MemoryCacheManager cache) {
        this.cache     = cache;
        this.templates = new JdbcTemplate[dataSources.length];
        for (int i = 0; i < dataSources.length; i++) {
            templates[i] = new JdbcTemplate(dataSources[i]);
            templates[i].setFetchSize(1000);
        }
        this.router   = new ShardRouter(ShardDataSourceConfig.NUM_SHARDS);
        this.executor = new ThreadPoolExecutor(
                dataSources.length,
                dataSources.length * 2,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(2000),
                r -> { Thread t = new Thread(r); t.setDaemon(true); t.setName("shard-score-" + t.getId()); return t; });
    }

    // ════════════════════════════════════════════════════
    //  单分片查询（缓存优先）
    // ════════════════════════════════════════════════════

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByStudentId(long studentId) {
        String key = "shard:sc:stu:" + studentId;
        List<Map<String, Object>> hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        List<Map<String, Object>> rows = templates[shard].queryForList(
                "SELECT * FROM sys_score WHERE student_id=? AND deleted=0 ORDER BY exam_year DESC, exam_term DESC",
                studentId);
        cache.put(CACHE, key, rows);
        return rows;
    }

    @SuppressWarnings("unchecked")
    public List<Map<String, Object>> findByStudentAndTerm(long studentId, int year, String term) {
        String key = "shard:sc:stu:" + studentId + ":y" + year + ":t" + term;
        List<Map<String, Object>> hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        List<Map<String, Object>> rows = templates[shard].queryForList(
                "SELECT * FROM sys_score WHERE student_id=? AND exam_year=? AND exam_term=? AND deleted=0",
                studentId, year, term);
        cache.put(CACHE, key, rows);
        return rows;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> findByStudentCourseYearTerm(long studentId, long courseId, int year, String term) {
        String key = "shard:sc:stu:" + studentId + ":c" + courseId + ":y" + year + ":t" + term;
        Map<String, Object> hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        List<Map<String, Object>> rows = templates[shard].queryForList(
                "SELECT * FROM sys_score WHERE student_id=? AND course_id=? AND exam_year=? AND exam_term=? AND deleted=0",
                studentId, courseId, year, term);
        Map<String, Object> result = rows.isEmpty() ? null : rows.get(0);
        if (result != null) cache.put(CACHE, key, result);
        return result;
    }

    /** 读计数缓存表（触发器维护，O(1) 主键查找），缓存结果 */
    public long countByStudentId(long studentId) {
        String key = "shard:sc:cnt:" + studentId;
        Long hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        int shard = router.route(studentId);
        Long n = templates[shard].queryForObject(
                "SELECT COUNT(*) FROM sys_score WHERE student_id=? AND deleted=0",
                Long.class, studentId);
        long val = n == null ? 0L : n;
        cache.put(CACHE, key, val);
        return val;
    }

    // ════════════════════════════════════════════════════
    //  跨分片聚合（并行 + 计数缓存表 + 缓存）
    // ════════════════════════════════════════════════════

    /** 全量总数 —— 读 shard_count_cache 表，不走全表 COUNT */
    public long countAll() {
        String key = "shard:sc:cnt:all";
        Long hit = cache.get(CACHE, key);
        if (hit != null) return hit;
        long total = parallelSumCacheTable("total_score");
        cache.put(CACHE, key, total);
        return total;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> globalStats() {
        String key = "shard:sc:stats:global";
        Map<String, Object> hit = cache.get(CACHE, key);
        if (hit != null) return hit;

        List<CompletableFuture<long[]>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(() -> {
                Long cnt  = tpl.queryForObject(
                        "SELECT COALESCE((SELECT value FROM shard_count_cache WHERE key='total_score'),0)", Long.class);
                Long pass = tpl.queryForObject(
                        "SELECT COALESCE((SELECT value FROM shard_count_cache WHERE key='total_pass'),0)", Long.class);
                Double sum = tpl.queryForObject(
                        "SELECT COALESCE(SUM(score),0.0) FROM sys_score WHERE deleted=0", Double.class);
                return new long[]{
                        cnt  == null ? 0L : cnt,
                        pass == null ? 0L : pass,
                        Double.doubleToRawLongBits(sum == null ? 0.0 : sum)};
            }, executor));
        }

        long totalCount = 0, totalPass = 0;
        double totalSum = 0;
        for (CompletableFuture<long[]> f : futures) {
            try {
                long[] r = f.get(30, TimeUnit.SECONDS);
                totalCount += r[0];
                totalPass  += r[1];
                totalSum   += Double.longBitsToDouble(r[2]);
            } catch (Exception e) { log.warn("跨分片统计出错: {}", e.getMessage()); }
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("totalCount", totalCount);
        result.put("passCount",  totalPass);
        result.put("failCount",  totalCount - totalPass);
        result.put("avgScore",   totalCount > 0 ? totalSum / totalCount : 0.0);
        result.put("passRate",   totalCount > 0 ? String.format("%.2f%%", 100.0 * totalPass / totalCount) : "0%");
        cache.put(CACHE, key, result);
        return result;
    }

    public List<Map<String, Object>> pageAcrossShards(int pageNum, int pageSize) {
        String key = "shard:sc:page:" + pageNum + ":" + pageSize;
        List<Map<String, Object>> hit = cache.get(MemoryCacheManager.PAGE_CACHE, key);
        if (hit != null) return hit;

        int limit = pageNum * pageSize;
        List<CompletableFuture<List<Map<String, Object>>>> futures = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            futures.add(CompletableFuture.supplyAsync(() ->
                    tpl.queryForList("SELECT * FROM sys_score WHERE deleted=0 ORDER BY exam_year DESC, id DESC LIMIT ?", limit),
                    executor));
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (CompletableFuture<List<Map<String, Object>>> f : futures) {
            try { merged.addAll(f.get(30, TimeUnit.SECONDS)); }
            catch (Exception e) { log.warn("分页合并失败: {}", e.getMessage()); }
        }
        merged.sort((a, b) -> {
            int ya = ((Number) a.get("exam_year")).intValue();
            int yb = ((Number) b.get("exam_year")).intValue();
            if (ya != yb) return yb - ya;
            return Long.compare(((Number) b.get("id")).longValue(), ((Number) a.get("id")).longValue());
        });
        int from = Math.min((pageNum - 1) * pageSize, merged.size());
        int to   = Math.min(from + pageSize, merged.size());
        List<Map<String, Object>> page = new ArrayList<>(merged.subList(from, to));
        cache.put(MemoryCacheManager.PAGE_CACHE, key, page);
        return page;
    }

    // ════════════════════════════════════════════════════
    //  写操作
    // ════════════════════════════════════════════════════

    public void insert(long studentId, long courseId, Long planId,
                       int examYear, String examTerm, double score, String status, String examDate) {
        int shard = router.route(studentId);
        templates[shard].update(
                "INSERT OR IGNORE INTO sys_score(student_id,course_id,plan_id,exam_year,exam_term,score,status,exam_date) VALUES(?,?,?,?,?,?,?,?)",
                studentId, courseId, planId, examYear, examTerm, score, status, examDate);
        evict(studentId);
    }

    public int batchInsert(int shardIdx, List<Object[]> rows) {
        if (rows.isEmpty()) return 0;
        int[] counts = templates[shardIdx].batchUpdate(
                "INSERT OR IGNORE INTO sys_score(student_id,course_id,exam_year,exam_term,score,status) VALUES(?,?,?,?,?,?)",
                rows);
        evictGlobalStats();
        return Arrays.stream(counts).sum();
    }

    public int delete(long studentId, long scoreId) {
        int shard = router.route(studentId);
        int affected = templates[shard].update(
                "UPDATE sys_score SET deleted=1 WHERE id=? AND student_id=?", scoreId, studentId);
        if (affected > 0) evict(studentId);
        return affected;
    }

    // ════════════════════════════════════════════════════
    //  缓存失效
    // ════════════════════════════════════════════════════

    private void evict(long studentId) {
        cache.remove(CACHE, "shard:sc:stu:" + studentId);
        cache.remove(CACHE, "shard:sc:cnt:" + studentId);
        evictGlobalStats();
    }

    private void evictGlobalStats() {
        cache.remove(CACHE, "shard:sc:cnt:all");
        cache.remove(CACHE, "shard:sc:stats:global");
        cache.invalidateAll(MemoryCacheManager.PAGE_CACHE);
    }

    // ════════════════════════════════════════════════════
    //  辅助
    // ════════════════════════════════════════════════════

    /** 并行读 shard_count_cache 表指定 key 并求和 */
    private long parallelSumCacheTable(String cacheKey) {
        String sql = "SELECT COALESCE((SELECT value FROM shard_count_cache WHERE key='" + cacheKey + "'),0)";
        List<CompletableFuture<Long>> fs = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            fs.add(CompletableFuture.supplyAsync(
                    () -> Optional.ofNullable(tpl.queryForObject(sql, Long.class)).orElse(0L),
                    executor));
        }
        return fs.stream().mapToLong(f -> { try { return f.get(30, TimeUnit.SECONDS); } catch (Exception e) { return 0L; } }).sum();
    }

    private long parallelSum(String sql) {
        List<CompletableFuture<Long>> fs = new ArrayList<>();
        for (JdbcTemplate tpl : templates) {
            fs.add(CompletableFuture.supplyAsync(
                    () -> Optional.ofNullable(tpl.queryForObject(sql, Long.class)).orElse(0L),
                    executor));
        }
        return fs.stream().mapToLong(f -> { try { return f.get(30, TimeUnit.SECONDS); } catch (Exception e) { return 0L; } }).sum();
    }

    public JdbcTemplate getTemplate(int shardIdx) { return templates[shardIdx]; }
    public int           getNumShards()            { return templates.length; }
    public ShardRouter   getRouter()               { return router; }
}
