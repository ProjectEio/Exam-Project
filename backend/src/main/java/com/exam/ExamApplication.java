package com.exam;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.exam.module.*.mapper")
public class ExamApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExamApplication.class, args);
        System.out.println("\n=========================================");
        System.out.println("  省考试院自学考试计划管理系统已启动");
        System.out.println("  接口文档: http://localhost:8080/doc.html");
        System.out.println("=========================================\n");
    }
}
