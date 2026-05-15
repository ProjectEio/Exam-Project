package com.exam.cache;

/**
 * 缓存统计快照（不可变值对象）
 */
public record CacheStats(
        String name,
        int    size,
        long   hitCount,
        long   missCount,
        long   evictCount,
        double hitRate
) {
    /** 返回人类可读的摘要 */
    public String summary() {
        return String.format("[%s] size=%d hit=%d miss=%d evict=%d rate=%.2f%%",
                name, size, hitCount, missCount, evictCount, hitRate * 100);
    }
}
