package mr.epayment.portal.subscriber;

import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Au démarrage : chiffre les NNI / NIF encore en clair (enregistrés avant la migration V16) et calcule leurs
 * empreintes. Sans effet une fois tout chiffré : peut tourner à chaque démarrage.
 */
@Component
class SensitiveDataBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataBackfill.class);

    private final JdbcTemplate jdbc;

    /** {@code sensitiveData} : la clé doit être chargée avant le rattrapage. */
    SensitiveDataBackfill(JdbcTemplate jdbc, SensitiveData sensitiveData) {
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        int[] count = {0};
        jdbc.query("""
                select id, nni, nif from subscriber
                where (nni is not null and nni not like 'v1:%') or (nif is not null and nif not like 'v1:%')
                """, rs -> {
            UUID id = rs.getObject("id", UUID.class);
            String nni = SensitiveData.decrypt(rs.getString("nni"));
            String nif = SensitiveData.decrypt(rs.getString("nif"));
            jdbc.update("update subscriber set nni = ?, nni_hash = ?, nif = ?, nif_hash = ? where id = ?",
                    SensitiveData.encrypt(nni), SensitiveData.hash(nni), SensitiveData.encrypt(nif),
                    SensitiveData.hash(nif), id);
            count[0]++;
        });
        if (count[0] > 0) {
            log.info("{} identifiant(s) NNI/NIF chiffré(s) en base", count[0]);
        }
    }
}
