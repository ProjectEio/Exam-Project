package com.exam.shard;

/**
 * Hash 分片路由器
 * ──────────────────────────────────────────────────────────
 * 策略：对 studentId 做 MurmurHash 风格扰动后取模
 *       保证均匀分布，避免连续 ID 落到同一分片
 *
 * 分片数必须为 2 的幂（方便位运算），推荐 4 或 8。
 */
public class ShardRouter {

    private final int numShards;
    private final int mask;          // numShards - 1，用于位与运算

    public ShardRouter(int numShards) {
        if (numShards <= 0 || (numShards & (numShards - 1)) != 0) {
            throw new IllegalArgumentException("numShards must be a power of 2");
        }
        this.numShards = numShards;
        this.mask      = numShards - 1;
    }

    /**
     * 根据 studentId 计算目标分片索引 (0 ~ numShards-1)
     *
     * 采用 Thomas Wang 整数哈希函数进行位扰动，
     * 使连续 ID 散列到不同分片，分布更均匀。
     */
    public int route(long studentId) {
        long h = studentId;
        h = (~h) + (h << 21);
        h =   h  ^ (h >>> 24);
        h = (h + (h << 3)) + (h << 8);
        h =   h  ^ (h >>> 14);
        h = (h + (h << 2)) + (h << 4);
        h =   h  ^ (h >>> 28);
        h =   h  + (h << 31);
        // 取绝对值后对分片数取模（位与）
        return (int)(Math.abs(h) & mask);
    }

    /** 路由字符串 key（取其 hashCode） */
    public int routeString(String key) {
        int h = key.hashCode();
        h ^= (h >>> 16);
        return Math.abs(h) & mask;
    }

    public int getNumShards() { return numShards; }
}
