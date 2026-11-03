package mr.epayment.portal.document;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Paramètres {@code epayment.storage.*} : stockage compatible S3 (SeaweedFS en local, MinIO ou S3 ailleurs). */
@ConfigurationProperties("epayment.storage")
record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey) {
}
