package com.exam.module.system.controller;

import com.exam.common.R;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 系统级公共接口（健康检查、版本信息）。无需认证。
 */
@Tag(name = "00-系统")
@RestController
@RequestMapping("/api/system")
public class SystemController {

    @Value("${spring.application.name}")
    private String appName;

    @Operation(summary = "健康检查", description = "用于探活，永远返回 ok")
    @GetMapping("/health")
    public R<Map<String, Object>> health() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("status", "UP");
        info.put("app", appName);
        info.put("time", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        return R.ok(info);
    }

    @Operation(summary = "系统信息", description = "返回 JVM / OS / 应用 等运行时信息")
    @GetMapping("/info")
    public R<Map<String, Object>> info() {
        Map<String, Object> info = new LinkedHashMap<>();
        info.put("app", appName);
        info.put("version", "1.0.0");
        info.put("javaVersion", System.getProperty("java.version"));
        info.put("javaVendor", System.getProperty("java.vendor"));
        info.put("os", System.getProperty("os.name") + " " + System.getProperty("os.version"));
        info.put("availableProcessors", Runtime.getRuntime().availableProcessors());

        Runtime rt = Runtime.getRuntime();
        long mb = 1024 * 1024;
        Map<String, Object> mem = new LinkedHashMap<>();
        mem.put("totalMB", rt.totalMemory() / mb);
        mem.put("freeMB",  rt.freeMemory() / mb);
        mem.put("maxMB",   rt.maxMemory() / mb);
        info.put("memory", mem);
        return R.ok(info);
    }
}
