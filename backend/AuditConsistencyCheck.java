import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 深入验证脚本：模拟审核后立即循环拉取状态
 */
public class AuditConsistencyCheck {
    private static final String BASE_URL = "http://localhost:8080/api";
    private static final String ADMIN_TOKEN = "YOUR_ADMIN_TOKEN_HERE"; // 需替换有效Token

    public static void main(String[] args) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        long registrationId = 1L; // 目标记录ID

        System.out.println(">>> 正在准备审核操作 (ID: " + registrationId + ")");

        // 1. 发起审核 (APPROVED)
        HttpRequest auditReq = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/registrations/" + registrationId + "/audit?status=APPROVED"))
                .header("Authorization", "Bearer " + ADMIN_TOKEN)
                .PUT(HttpRequest.BodyPublishers.noBody())
                .build();

        HttpResponse<String> auditResp = client.send(auditReq, HttpResponse.BodyHandlers.ofString());
        System.out.println("审核结果: " + auditResp.statusCode() + " | " + auditResp.body());

        System.out.println(">>> 立即开始高频查询循环...");
        long start = System.currentTimeMillis();
        int pendingCount = 0;
        int approvedCount = 0;
        int totalCheck = 0;

        // 循环 2 秒，高频检测
        while (System.currentTimeMillis() - start < 2000) {
            totalCheck++;
            HttpRequest queryReq = HttpRequest.newBuilder()
                    .uri(URI.create(BASE_URL + "/registrations/" + registrationId))
                    .header("Authorization", "Bearer " + ADMIN_TOKEN)
                    .GET()
                    .build();

            HttpResponse<String> queryResp = client.send(queryReq, HttpResponse.BodyHandlers.ofString());
            String body = queryResp.body();
            
            if (body.contains("\"status\":\"PENDING\"")) {
                pendingCount++;
            } else if (body.contains("\"status\":\"APPROVED\"")) {
                approvedCount++;
            }
            
            // 极短休眠，模拟高并发下的读
            Thread.sleep(10);
        }

        System.out.println("--------------------------------------");
        System.out.println("检测结束 (持续 2000ms):");
        System.out.println("总查询次数: " + totalCheck);
        System.out.println("读到 PENDING 的次数: " + pendingCount);
        System.out.println("读到 APPROVED 的次数: " + approvedCount);
        
        if (pendingCount > 0 && approvedCount > 0) {
            System.out.println("!!! 严重警告: 检测到状态反复 (Data Flapping) !!!");
            System.out.println("这通常意味着清理缓存后，由于并发导致旧数据写回缓存。");
        } else if (pendingCount > 0 && approvedCount == 0) {
            System.out.println("!!! 警告: 即使审核返回成功，查询依然全是旧数据 (Write Lag) !!!");
        } else {
            System.out.println("本次检测未发现不一致。");
        }
        System.out.println("--------------------------------------");
    }
}
