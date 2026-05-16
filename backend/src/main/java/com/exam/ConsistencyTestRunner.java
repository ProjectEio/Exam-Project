package com.exam;

import com.exam.cache.MemoryCacheManager;
import com.exam.common.UserContext;
import com.exam.module.registration.entity.Registration;
import com.exam.module.registration.service.RegistrationService;
import com.exam.shard.RegistrationShardRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

@Component
public class ConsistencyTestRunner implements CommandLineRunner {

    private final RegistrationService registrationService;
    private final RegistrationShardRepository regRepo;
    private final MemoryCacheManager cacheManager;

    public ConsistencyTestRunner(RegistrationService registrationService, 
                                RegistrationShardRepository regRepo, 
                                MemoryCacheManager cacheManager) {
        this.registrationService = registrationService;
        this.regRepo = regRepo;
        this.cacheManager = cacheManager;
    }

    @Override
    public void run(String... args) throws Exception {
        // 仅在手动触发或特定条件下运行，避免干扰正常启动
        if (System.getProperty("runTest") == null) return;

        System.out.println("\n========== 数据一致性自动化测试报告 ==========");
        
        // 1. 模拟管理员权限
        // 注意：这里需要根据实际 UserContext 实现来模拟，如果是基于 ThreadLocal
        // 假设 UserContext 有静态方法设置
        
        Long testId = 2490401L; 
        Registration target = regRepo.findById(testId);
        if (target == null) {
            System.out.println("❌ 未找到测试记录 " + testId);
            return;
        }
        if (!"PENDING".equals(target.getStatus())) {
            System.out.println("⚠️ 警告：记录状态为 " + target.getStatus() + ", 尝试将其重置为 PENDING 进行测试");
            regRepo.updateStatus(testId, "PENDING", null, null, "UNPAID");
            cacheManager.invalidateAll(MemoryCacheManager.REGISTRATION_CACHE);
            cacheManager.invalidateAll(MemoryCacheManager.PAGE_CACHE);
        }

        System.out.println("1. 测试对象锁定: " + testId);
        
        // 2. 第一步：触发并发读（入缓存）
        registrationService.detail(testId);
        System.out.println("2. 缓存预热成功 (REGISTRATION_CACHE)");

        // 3. 第二步 & 第三步：极速冲突模拟
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> fastCheckStatus = new AtomicReference<>();
        ExecutorService executor = Executors.newSingleThreadExecutor();

        // 异步回查线程
        executor.execute(() -> {
            try {
                latch.await(); // 等待审核开始
                Thread.sleep(20); // 20ms 极速回查
                Registration r = registrationService.detail(testId);
                fastCheckStatus.set(r.getStatus());
            } catch (Exception e) {
                e.printStackTrace();
            }
        });

        System.out.println("3. 执行审核并通过 (APPROVED)...");
        latch.countDown();
        registrationService.audit(testId, "APPROVED", "一致性验证测试");

        Thread.sleep(200); // 等待回查完成

        // 4. 验证结果
        Registration dbReg = regRepo.findById(testId);
        String finalStatus = dbReg.getStatus();
        String ticketNo = dbReg.getAdmissionTicketNo();

        System.out.println("\n--- 验证结果 ---");
        System.out.println("指标 A (数据库状态): " + finalStatus);
        System.out.println("指标 B (缓存可见性): " + (fastCheckStatus.get() != null ? fastCheckStatus.get() : "未捕获"));
        System.out.println("指标 C (准考证号): " + (ticketNo != null ? "已生成 [" + ticketNo + "]" : "未生成"));

        boolean cachePollution = "PENDING".equals(fastCheckStatus.get()) && "APPROVED".equals(finalStatus);
        System.out.println("\n是否观察到缓存污染: " + (cachePollution ? "🔴 是 (BUG!)" : "🟢 否 (一致性良好)"));
        System.out.println("TransactionSynchronization 是否生效: " + (!cachePollution ? "✅ 是" : "❌ 否"));
        System.out.println("============================================\n");
        
        executor.shutdown();
    }
}
