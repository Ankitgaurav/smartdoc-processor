package com.smartdoc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;

/**
 * Creates and configures the AWS S3 client bean.
 *
 * Why StaticCredentialsProvider?
 * We are providing keys explicitly from application.yml.
 * In production on EC2, you would use IAM roles instead
 * (no keys needed — EC2 instance inherits permissions automatically).
 * That is more secure — no keys in config files at all.
 */
@Configuration
public class AwsS3Config {

    @Value("${app.aws.s3.access-key}")
    private String accessKey;

    @Value("${app.aws.s3.secret-key}")
    private String secretKey;

    @Value("${app.aws.region}")
    private String region;

    /**
     * Creates a singleton S3Client bean used throughout the app.
     * Spring injects this wherever you declare: @Autowired S3Client s3Client
     */
    @Bean
    public S3Client s3Client() {
        AwsBasicCredentials credentials = AwsBasicCredentials.create(accessKey, secretKey);

        return S3Client.builder()
                .region(Region.of(region))
                .credentialsProvider(StaticCredentialsProvider.create(credentials))
                .build();
    }
}