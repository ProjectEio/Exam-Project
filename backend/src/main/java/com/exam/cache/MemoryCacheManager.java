package com.exam.cache;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 统一缓存管理器
 * ─────────────────────────────────────────────────────────
 * 管理系统内所有命名缓存的生命周期，提供统一的 get/put/invalidate API，
 * 以及各缓存命中率、大小等统计信息的汇总接口。
 *
 * 各缓存规格说明（按业务热度调参）：
 *   user         – 用户信息，变动少，TTL 10 min，上限 5 万
 *   score_list   – 学生全部成绩列表，TTL 5 min，上限 10 万（命中率最高）
 *   page         – 分页结果，TTL 30 s，上限 5000（防止分页缓存爆内存）
 *   plan         – 考试计划，TTL 2 min，上限 1 万
 *   registration – 报名记录，TTL 5 min，上限 10 万
 */
@Component
public class MemoryCacheManager {

    // ── 缓存名称常量 ──────────────────────────────────────
    public static final String USER_CACHE         = "user";
    public static final String SCORE_LIST_CACHE   = "score_list";
    public static final String PAGE_CACHE         = "page";
    public static final String PLAN_CACHE         = "plan";
    public static final String REGISTRATION_CACHE = "registration";

    private final Map<String, MemoryCache<String, Object>> caches = new ConcurrentHashMap<>();

    public MemoryCacheManager() {
        // 注册所有业务缓存（名称, 最大条目, TTL毫秒）
        register(USER_CACHE,         50_000,  10 * 60_000L);
        register(SCORE_LIST_CACHE,  100_000,   5 * 60_000L);
        register(PAGE_CACHE,          5_000,       30_000L);
        register(PLAN_CACHE,         10_000,   2 * 60_000L);
        register(REGISTRATION_CACHE,100_000,   5 * 60_000L);
    }

    private void register(String name, int maxSize, long ttlMs) {
        caches.put(name, new MemoryCache<>(name, maxSize, ttlMs));
    }

    // ── 读 ────────────────────────────────────────────────

    /**
     * 从指定缓存中查找 key 对应的值；
     * 不存在或已过期返回 null。
     */
    @SuppressWarnings("unchecked")
    public <T> T get(String cacheName, String key) {
        MemoryCache<String, Object> c = caches.get(cacheName);
        return c == null ? null : (T) c.get(key);
    }

    // ── 写 ────────────────────────────────────────────────

    /** 写入缓存（key → value） */
    public void put(String cacheName, String key, Object value) {
        MemoryCache<String, Object> c = caches.get(cacheName);
        if (c != null) c.put(key, value);
    }

    // ── 失效 ──────────────────────────────────────────────

    /** 删除单条缓存 */
    public void remove(String cacheName, String key) {
        MemoryCache<String, Object> c = caches.get(cacheName);
        if (c != null) c.remove(key);
    }

    /** 清空某个命名缓存的所有条目 */
    public void invalidateAll(String cacheName) {
        MemoryCache<String, Object> c = caches.get(cacheName);
        if (c != null) c.clear();
    }

    /** 按前缀清空指定缓存 */
    public void clearByPrefix(String cacheName, String prefix) {
        MemoryCache<String, Object> c = caches.get(cacheName);
        if (c != null) c.clearByPrefix(prefix);
    }

    /** 清空所有缓存 */
    public void invalidateAllCaches() {
        caches.values().forEach(MemoryCache::clear);
    }

    // ── 统计 ──────────────────────────────────────────────

    /** 返回所有缓存的统计快照（key = 缓存名） */
    public Map<String, CacheStats> allStats() {
        return caches.entrySet().stream().collect(Collectors.toMap(
                Map.Entry::getKey,
                e -> {
                    MemoryCache<String, Object> c = e.getValue();
                    return new CacheStats(c.getName(), c.size(),
                            c.getHitCount(), c.getMissCount(),
                            c.getEvictCount(), c.getHitRate());
                },
                (a, b) -> a,
                LinkedHashMap::new
        ));
    }

    /** 返回全局命中率（所有缓存的加权平均） */
    public double globalHitRate() {
        long totalHit  = caches.values().stream().mapToLong(MemoryCache::getHitCount).sum();
        long totalMiss = caches.values().stream().mapToLong(MemoryCache::getMissCount).sum();
        long total = totalHit + totalMiss;
        return total == 0 ? 0.0 : (double) totalHit / total;
    }

    public Collection<MemoryCache<String, Object>> allCaches() {
        return caches.values();
    }
}
