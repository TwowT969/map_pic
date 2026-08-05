package org.lxp.mapalbum.framework.oss;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.ObjectMetadata;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.yaml.snakeyaml.Yaml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FileInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * OSS 全链路冒烟（上传 -> 签名 URL -> 下载 -> 删除），验证 endpoint/凭证/bucket/私有读配置正确可用。
 *
 * <p>默认跳过，需 {@code -Doss.live.test=true} 显式开启，避免无凭证环境/CI 失败。
 * 直接读 application-local.yml 取凭证，不加载 Spring 上下文、不依赖 MySQL/Redis 中间件。
 * 测试对象用 {@code _oss_live_test/} 前缀，结束后物理删除，不留垃圾。
 *
 * @author lxp
 */
@EnabledIfSystemProperty(named = "oss.live.test", matches = "true")
class OssConnectivityTest {

    @Test
    void putSignDownloadDelete_cycleWorks() throws Exception {
        Map<String, Object> oss = readOssConfig();
        assumeTrue(oss != null && oss.get("endpoint") != null && oss.get("access-key-id") != null,
                "application-local.yml 缺少 oss 配置，跳过");

        String endpoint = https((String) oss.get("endpoint"));
        String ak = (String) oss.get("access-key-id");
        String sk = (String) oss.get("access-key-secret");
        String bucket = (String) oss.get("bucket-name");

        OSS client = new OSSClientBuilder().build(endpoint, ak, sk);
        String key = "_oss_live_test/" + System.currentTimeMillis() + ".txt";
        try {
            // 1. 上传
            byte[] content = ("oss-live-test-" + System.currentTimeMillis()).getBytes(StandardCharsets.UTF_8);
            ObjectMetadata meta = new ObjectMetadata();
            meta.setContentLength(content.length);
            meta.setContentType("text/plain");
            client.putObject(bucket, key, new ByteArrayInputStream(content), meta);

            // 2. 生成签名 URL（私有 bucket 读访问）
            URL signed = client.generatePresignedUrl(bucket, key,
                    new Date(System.currentTimeMillis() + 60_000));
            assertNotNull(signed, "签名 URL 不应为空");
            assertTrue(signed.toString().startsWith("https://"), "签名 URL 应为 https：" + signed);

            // 3. 用签名 URL 下载，内容应与上传一致
            HttpURLConnection conn = (HttpURLConnection) signed.openConnection();
            conn.setConnectTimeout(10_000);
            conn.setReadTimeout(10_000);
            assertEquals(200, conn.getResponseCode(), "签名 URL 下载应返回 200");
            byte[] got = readAll(conn.getInputStream());
            assertArrayEquals(content, got, "下载内容应与上传一致");
            conn.disconnect();

            System.out.println("[OssConnectivityTest] OK 上传/签名/下载全链路通过 key=" + key);
        } finally {
            try {
                client.deleteObject(bucket, key);
            } catch (Exception ignore) {
                // 清理失败不掩盖用例结果
            }
            client.shutdown();
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> readOssConfig() throws Exception {
        try (InputStream in = new FileInputStream("src/main/resources/application-local.yml")) {
            Map<String, Object> root = new Yaml().load(in);
            return (Map<String, Object>) root.get("oss");
        }
    }

    private static String https(String endpoint) {
        if (endpoint == null || endpoint.isEmpty()) {
            return endpoint;
        }
        return endpoint.startsWith("http://") || endpoint.startsWith("https://")
                ? endpoint : "https://" + endpoint;
    }

    private static byte[] readAll(InputStream in) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) != -1) {
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }
}
