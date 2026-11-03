package mr.epayment.portal;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Vraies dépendances pour les tests : PostgreSQL 16 et un stockage S3 (SeaweedFS, même image qu'en dev).
 * Les conteneurs sont partagés par tous les contextes Spring de l'exécution : ils ne démarrent qu'une fois.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final int S3_PORT = 8333;

    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer(
            DockerImageName.parse("postgres:16-alpine"));

    /** Sans fichier d'identités, SeaweedFS accepte les requêtes S3 sans contrôle : suffisant pour les tests. */
    private static final GenericContainer<?> S3 = new GenericContainer<>(
            DockerImageName.parse("chrislusf/seaweedfs:4.51"))
            .withCommand("server", "-dir=/data", "-s3", "-s3.port=" + S3_PORT)
            .withExposedPorts(S3_PORT)
            .waitingFor(Wait.forListeningPort());

    static {
        POSTGRES.start();
        S3.start();
    }

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return POSTGRES;
    }

    @Bean
    DynamicPropertyRegistrar s3Properties() {
        return registry -> registry.add("epayment.storage.endpoint",
                () -> "http://" + S3.getHost() + ":" + S3.getMappedPort(S3_PORT));
    }
}
