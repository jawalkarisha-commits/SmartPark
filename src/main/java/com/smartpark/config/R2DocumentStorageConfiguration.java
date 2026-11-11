package com.smartpark.config;

import java.net.URI;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "smartpark.kyc.r2", name = "endpoint")
public class R2DocumentStorageConfiguration {

    @Bean(destroyMethod = "close")
    S3Client r2S3Client(
            @Value("${smartpark.kyc.r2.endpoint}") String endpoint,
            @Value("${smartpark.kyc.r2.access-key-id}") String accessKeyId,
            @Value("${smartpark.kyc.r2.secret-access-key}") String secretAccessKey) {
        URI endpointUri = URI.create(endpoint);
        if (!"https".equalsIgnoreCase(endpointUri.getScheme())
                || endpointUri.getHost() == null) {
            throw new IllegalStateException(
                "SMARTPARK_KYC_R2_ENDPOINT must be an HTTPS URL"
            );
        }
        return S3Client.builder()
            .endpointOverride(endpointUri)
            .region(Region.of("auto"))
            .credentialsProvider(StaticCredentialsProvider.create(
                AwsBasicCredentials.create(accessKeyId, secretAccessKey)
            ))
            .serviceConfiguration(S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .build())
            .build();
    }
}
