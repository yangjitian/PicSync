package com.yudi.cloud.config;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.ClientConfig;
import com.qcloud.cos.auth.BasicCOSCredentials;
import com.qcloud.cos.auth.COSCredentials;
import com.qcloud.cos.http.HttpProtocol;
import com.qcloud.cos.region.Region;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * COS客户端配置类（读取配置并生成COSClient实例）
 */
@Configuration
@ConfigurationProperties(prefix = "cos.client")
@Data
public class CosClientConfig {

    /**
     * 域名
     */
    private String host;

    /**
     * 腾讯云API密钥ID
     */
    private String secretId;

    /**
     * 腾讯云API密钥Key
     */
    private String secretKey;

    /**
     * 存储桶地域
     */
    private String region;

    /**
     * 存储桶名称
     */
    private String bucketName;

    /**
     * 生成COSClient实例（交给Spring管理，全局单例）
     */
    @Bean
    public COSClient cosClient() {
        // 1. 初始化身份信息（使用配置文件中的密钥）
        COSCredentials credentials = new BasicCOSCredentials(secretId, secretKey);
        // 2. 配置地域（与存储桶实际地域一致）
        ClientConfig clientConfig = new ClientConfig(new Region(region));
        // 3. 强制使用HTTPS协议（推荐，更安全）
        clientConfig.setHttpProtocol(HttpProtocol.https);
        // 4. 生成客户端实例（SDK会自动处理域名，无需手动配置host）
        return new COSClient(credentials, clientConfig);
    }
}