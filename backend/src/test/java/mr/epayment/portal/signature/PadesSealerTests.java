package mr.epayment.portal.signature;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PadesSealerTests {

    @Test
    void stampTextKeepsFrenchAccentsAndReplacesUnsupportedCharacters() {
        assertThat(PadesSealer.winAnsi("Signé par Société Atlas · à 10:00\nligne 2"))
                .isEqualTo("Signé par Société Atlas · à 10:00\nligne 2");
        assertThat(PadesSealer.winAnsi("Signé par شركة")).isEqualTo("Signé par ????");
    }
}
