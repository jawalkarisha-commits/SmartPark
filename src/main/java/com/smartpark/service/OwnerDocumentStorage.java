package com.smartpark.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Service
public class OwnerDocumentStorage {

    private final Path storageDirectory;
    private final S3Client s3Client;
    private final String bucket;

    public OwnerDocumentStorage(
            ObjectProvider<S3Client> s3ClientProvider,
            @Value("${smartpark.kyc.storage-directory}") String storageDirectory,
            @Value("${smartpark.kyc.r2.bucket:}") String bucket) {
        this.storageDirectory = Path.of(storageDirectory).toAbsolutePath().normalize();
        this.s3Client = s3ClientProvider.getIfAvailable();
        this.bucket = bucket;
        if (s3Client != null && bucket.isBlank()) {
            throw new IllegalStateException(
                "SMARTPARK_KYC_R2_BUCKET is required when R2 storage is enabled"
            );
        }
    }

    public void store(String key, byte[] content, String contentType) throws IOException {
        if (s3Client != null) {
            try {
                s3Client.putObject(
                    PutObjectRequest.builder()
                        .bucket(bucket)
                        .key(key)
                        .contentType(contentType)
                        .build(),
                    RequestBody.fromBytes(content)
                );
            } catch (SdkException exception) {
                throw new IOException("Owner document could not be uploaded to object storage", exception);
            }
            return;
        }

        Path file = resolveLocalKey(key);
        Files.createDirectories(storageDirectory);
        Files.write(
            file, content, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE
        );
    }

    public byte[] read(String key) throws IOException {
        if (s3Client != null) {
            try {
                return s3Client.getObjectAsBytes(GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build()).asByteArray();
            } catch (SdkException exception) {
                throw new IOException("Owner document could not be read from object storage", exception);
            }
        }
        return Files.readAllBytes(resolveLocalKey(key));
    }

    public void delete(String key) throws IOException {
        if (s3Client != null) {
            try {
                s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(bucket)
                    .key(key)
                    .build());
            } catch (SdkException exception) {
                throw new IOException("Owner document could not be removed from object storage", exception);
            }
            return;
        }
        Files.deleteIfExists(resolveLocalKey(key));
    }

    private Path resolveLocalKey(String key) throws IOException {
        if (key == null || key.isBlank()) {
            throw new IOException("Stored owner document reference is invalid");
        }
        Path file = storageDirectory.resolve(key).normalize();
        if (!file.getParent().equals(storageDirectory)) {
            throw new IOException("Stored owner document reference is invalid");
        }
        return file;
    }
}
