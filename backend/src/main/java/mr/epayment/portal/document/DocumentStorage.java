package mr.epayment.portal.document;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.checksums.RequestChecksumCalculation;
import software.amazon.awssdk.core.checksums.ResponseChecksumValidation;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;

/**
 * Stockage des documents (contrats PDF, versions signées, reçus) dans un bucket privé compatible S3.
 * Les documents ne sont jamais servis directement : le backend contrôle l'accès puis les relit ici.
 */
@Service
@EnableConfigurationProperties(StorageProperties.class)
public class DocumentStorage {

    private final S3Client s3;
    private final String bucket;
    private volatile boolean bucketReady;

    DocumentStorage(StorageProperties properties) {
        this.bucket = properties.bucket();
        this.s3 = S3Client.builder()
                .endpointOverride(URI.create(properties.endpoint()))
                .region(Region.of(properties.region()))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(properties.accessKey(), properties.secretKey())))
                .forcePathStyle(true)
                // Les serveurs S3 compatibles ne gèrent pas tous les sommes de contrôle récentes du SDK.
                .requestChecksumCalculation(RequestChecksumCalculation.WHEN_REQUIRED)
                .responseChecksumValidation(ResponseChecksumValidation.WHEN_REQUIRED)
                .httpClient(UrlConnectionHttpClient.create())
                .build();
    }

    public void put(String key, byte[] content, String contentType) {
        ensureBucket();
        s3.putObject(request -> request.bucket(bucket).key(key).contentType(contentType),
                RequestBody.fromBytes(content));
    }

    public byte[] get(String key) {
        return s3.getObjectAsBytes(request -> request.bucket(bucket).key(key)).asByteArray();
    }

    /** Le bucket est créé au premier usage : le backend démarre même si le stockage est momentanément absent. */
    private void ensureBucket() {
        if (bucketReady) {
            return;
        }
        try {
            s3.headBucket(request -> request.bucket(bucket));
        } catch (NoSuchBucketException e) {
            s3.createBucket(request -> request.bucket(bucket));
        }
        bucketReady = true;
    }
}
