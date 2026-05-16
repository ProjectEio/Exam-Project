package com.exam.module.statistics.repository;

import com.exam.module.statistics.dto.ChartItem;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Repository
public class StatisticsChartCacheRepository {

    private final JdbcTemplate jdbcTemplate;

    public StatisticsChartCacheRepository(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void init() {
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chart_registration_trend_cache (
                sort_no INTEGER PRIMARY KEY,
                label   TEXT NOT NULL,
                value   REAL NOT NULL DEFAULT 0
            )
            """);
        jdbcTemplate.execute("""
            CREATE TABLE IF NOT EXISTS chart_pass_rate_cache (
                sort_no INTEGER PRIMARY KEY,
                label   TEXT NOT NULL,
                value   REAL NOT NULL DEFAULT 0
            )
            """);
    }

    public List<ChartItem> loadRegistrationTrend() {
        init();
        return jdbcTemplate.query(
                "SELECT label, value FROM chart_registration_trend_cache ORDER BY sort_no ASC",
                (rs, i) -> new ChartItem(rs.getString("label"), BigDecimal.valueOf(rs.getDouble("value"))));
    }

    public List<ChartItem> loadPassRate() {
        init();
        return jdbcTemplate.query(
                "SELECT label, value FROM chart_pass_rate_cache ORDER BY sort_no ASC",
                (rs, i) -> new ChartItem(rs.getString("label"), BigDecimal.valueOf(rs.getDouble("value"))));
    }

    public void refreshRegistrationTrend(Map<String, Long> byTerm) {
        init();
        jdbcTemplate.update("DELETE FROM chart_registration_trend_cache");
        if (byTerm == null || byTerm.isEmpty()) return;

        List<Object[]> rows = new ArrayList<>();
        int sortNo = 1;
        for (Map.Entry<String, Long> entry : byTerm.entrySet()) {
            rows.add(new Object[]{sortNo++, entry.getKey(), entry.getValue()});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO chart_registration_trend_cache(sort_no,label,value) VALUES(?,?,?)",
                rows);
    }

    public void refreshPassRate(Map<Long, long[]> stats, Map<Long, String> nameMap) {
        init();
        jdbcTemplate.update("DELETE FROM chart_pass_rate_cache");
        if (stats == null || stats.isEmpty()) return;

        List<ChartItem> items = stats.entrySet().stream()
                .filter(e -> e.getValue() != null && e.getValue().length >= 2 && e.getValue()[0] > 0)
                .map(e -> {
                    long total = e.getValue()[0];
                    long pass = e.getValue()[1];
                    double rate = pass * 100.0 / total;
                    String label = nameMap.getOrDefault(e.getKey(), "课程" + e.getKey());
                    return new ChartItem(label, BigDecimal.valueOf(rate).setScale(1, java.math.RoundingMode.HALF_UP));
                })
                .sorted(Comparator.comparing(ChartItem::getValue).reversed().thenComparing(ChartItem::getLabel))
                .limit(10)
                .toList();

        if (items.isEmpty()) return;
        List<Object[]> rows = new ArrayList<>();
        for (int i = 0; i < items.size(); i++) {
            ChartItem item = items.get(i);
            rows.add(new Object[]{i + 1, item.getLabel(), item.getValue()});
        }
        jdbcTemplate.batchUpdate(
                "INSERT INTO chart_pass_rate_cache(sort_no,label,value) VALUES(?,?,?)",
                rows);
    }

    public boolean hasRegistrationTrend() {
        init();
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_registration_trend_cache", Long.class);
        return count != null && count > 0;
    }

    public boolean hasPassRate() {
        init();
        Long count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM chart_pass_rate_cache", Long.class);
        return count != null && count > 0;
    }
}
