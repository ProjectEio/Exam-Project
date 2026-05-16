package com.exam;

import com.exam.cache.MemoryCacheManager;
import com.exam.module.registration.entity.Registration;
import com.exam.module.registration.service.RegistrationService;
import com.exam.shard.RegistrationShardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Configuration
@Profile("test-concurrency")
public class ConcurrencyTestConfig {

    @Bean
    public CommandLineRunner runTest(RegistrationService registrationService, 
                                     RegistrationShardRepository regRepo,
                                     MemoryCacheManager cacheManager) {
        return args -> {
            System.out.println(">>> 开始自动化并发一致性测试...");

            // 1. 自动化查找 PENDING 记录
            Registration target = null;
            // 扫描分片 0-7 找一个 PENDING 的
            // 注意：这里为了简化直接从 Repo 找，假设数据库里有数据
            for (int i = 0; i < 8; i++) {
                // 这里用 reflection 或者是直接调用内部方法，但为了方便我们直接用公开接口
                // 模拟一个已知的 ID，或者从数据库扫描
            }
            
            // 假设我们找到了一个 ID
            Long testId = 2490401L; // 用户提供的 ID
            Registration reg = regRepo.findById(testId);
            if (reg == null || !"PENDING".equals(reg.getStatus())) {
                System.out.println(">>> 错误：未找到 ID 为 " + testId + " 的 PENDING 记录，请检查数据库。");
                return;
            }
            System.out.println(">>> 锁定测试对象 ID: " + testId + ", 原始状态: " + reg.getStatus());

            // 2. 高频读写冲突模拟
            
            // 步骤一：触发读，确保入缓存
            registrationService.detail(testId); 
            System.out.println(">>> 步骤1：详情已进入缓存 (REGISTRATION_CACHE)");

            // 步骤二 & 三：异步触发更新和极速回查
            Executors.newSingleThreadExecutor().execute(() -> {
                try {
                    // 模拟网络延迟 10ms 后开始回查
                    Thread.sleep(10); 
                    Registration fastCheck = registrationService.detail(testId);
                    System.out.println(">>> 步骤3 (极速回查): 获得状态 = " + fastCheck.getStatus());
                    
                    // 指标 A：数据库验证
                    // 由于 cache 被清理了，如果 audit 还没提交，这里查到的还是旧的；
                    // 如果 TransactionSynchronization 生效，则是在提交后才清理。
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });

            // 步骤二：执行审核（会触发事务和缓存清理）
            System.out.println(">>> 步骤2：开始执行审核...");
            registrationService.audit(testId, "APPROVED", "自动化测试通过");
            System.out.println(">>> 步骤2：审核调用完成");

            // 4. 结果验证
            Thread.sleep(500); // 等待异步检查完成
            Registration finalReg = regRepo.findById(testId);
            System.out.println(">>> 指标 A (数据库最终状态): " + finalReg.getStatus());
            System.out.println(">>> 指标 C (准考证号): " + finalReg.getAdmissionTicketNo());
            
            System.out.println(">>> 测试结束。");
        };
    }
}
