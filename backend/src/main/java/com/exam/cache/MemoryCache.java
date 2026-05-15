package com.exam.cache;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 手动实现的分段 LRU + TTL 内存缓冲
 * -----------------------------------------------
 * 设计：16 个独立段，每段拥有独立 ReentrantLock
 *       读写都用 writeLock（因为 LinkedHashMap accessOrder=true
 *       在 get 时会修改顺序，不能用读锁）
 * 优点：16段并行 → 锁粒度仅 1/16，支持高并发
 * 淘汰：每段独立 LRU（LinkedHashMap.removeEldestEntry）
 * TTL ：每次 get 时做懒惰过期检查
 */
@SuppressWarnings("unchecked")
public class MemoryCache<K, V> {

    private static final int SEGMENT_COUNT = 16;       // 必须是2的幂
    private static final int SEGMENT_MASK  = SEGMENT_COUNT - 1;

    private final Segment<K, V>[] segments;
    private final AtomicLong totalHits      = new AtomicLong();
    private final AtomicLong totalMisses    = new AtomicLong();
    private final AtomicLong totalEvictions = new AtomicLong();
    private final String name;

    /**
     * @param name    缓存名称（用于统计面板）
     * @param maxSize 总最大条目数
     * @param ttlMs   每条记录的存活时间（毫秒）
     */
    public MemoryCache(String name, int maxSize, long ttlMs) {
        this.name = name;
        this.segments = new Segment[SEGMENT_COUNT];
        int segSize = Math.max(16, maxSize / SEGMENT_COUNT);
        for (int i = 0; i < SEGMENT_COUNT; i++) {
            segments[i] = new Segment<>(segSize, ttlMs, totalEvictions);
        }
    }

    // ────────────────── 路由 ──────────────────

    private Segment<K, V> segmentFor(K key) {
        int h = key.hashCode();
        h ^= (h >>> 16);   // 扰动高位，减少碰撞
        return segments[h & SEGMENT_MASK];
    }

    // ────────────────── 公开 API ──────────────────

    /** 查找缓存；返回 null 表示不存在或已过期 */
    public V get(K key) {
        V val = segmentFor(key).get(key);
        if (val != null) totalHits.incrementAndGet();
        else             totalMisses.incrementAndGet();
        return val;
    }

    /** 写入缓存 */
    public void put(K key, V value) {
        segmentFor(key).put(key, value);
    }

    /** 删除指定 key */
    public void remove(K key) {
        segmentFor(key).remove(key);
    }

    /** 清空整个缓存 */
    public void clear() {
        for (Segment<K, V> seg : segments) seg.clear();
    }

    // ────────────────── 统计 ──────────────────

    public String getName()         { return name; }
    public long getHitCount()       { return totalHits.get(); }
    public long getMissCount()      { return totalMisses.get(); }
    public long getEvictCount()     { return totalEvictions.get(); }

    public double getHitRate() {
        long total = totalHits.get() + totalMisses.get();
        return total == 0 ? 0.0 : (double) totalHits.get() / total;
    }

    public int size() {
        int s = 0;
        for (Segment<K, V> seg : segments) s += seg.size();
        return s;
    }

    // ══════════════════════════════════════════════════
    //  内部：单个缓存段
    // ══════════════════════════════════════════════════

    private static final class Segment<K, V> {

        private final ReentrantLock lock      = new ReentrantLock();
        private final long          ttlMs;
        private final AtomicLong    evictions;

        /** accessOrder=true → get 操作把 entry 移到末尾（最近使用） */
        private final LinkedHashMap<K, CacheEntry<V>> map;

        Segment(int maxSize, long ttlMs, AtomicLong evictions) {
            this.ttlMs     = ttlMs;
            this.evictions = evictions;
            this.map = new LinkedHashMap<K, CacheEntry<V>>(maxSize + 1, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<K, CacheEntry<V>> eldest) {
                    boolean doRemove = size() > maxSize;
                    if (doRemove) evictions.incrementAndGet();
                    return doRemove;
                }
            };
        }

        V get(K key) {
            lock.lock();
            try {
                CacheEntry<V> entry = map.get(key);
                if (entry == null) return null;
                if (System.currentTimeMillis() > entry.expireAt) {
                    map.remove(key);
                    return null;
                }
                return entry.value;
            } finally {
                lock.unlock();
            }
        }

        void put(K key, V value) {
            lock.lock();
            try {
                map.put(key, new CacheEntry<>(value, System.currentTimeMillis() + ttlMs));
            } finally {
                lock.unlock();
            }
        }

        void remove(K key) {
            lock.lock();
            try { map.remove(key); }
            finally { lock.unlock(); }
        }

        void clear() {
            lock.lock();
            try { map.clear(); }
            finally { lock.unlock(); }
        }

        int size() {
            lock.lock();
            try { return map.size(); }
            finally { lock.unlock(); }
        }
    }

    // ══════════════════════════════════════════════════
    //  内部：缓存条目（value + 过期时间戳）
    // ══════════════════════════════════════════════════

    private static final class CacheEntry<V> {
        final V    value;
        final long expireAt;   // System.currentTimeMillis() + ttlMs

        CacheEntry(V value, long expireAt) {
            this.value    = value;
            this.expireAt = expireAt;
        }
    }
}
