package mr.epayment.portal.transaction;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Cellules du CSV : séparateur, guillemets et injection de formule dans Excel. */
class TransactionCsvTests {

    @Test
    void cellsWithSeparatorsOrQuotesAreQuoted() {
        assertThat(TransactionCsv.escape("Facture; octobre")).isEqualTo("\"Facture; octobre\"");
        assertThat(TransactionCsv.escape("Dit \"urgent\"")).isEqualTo("\"Dit \"\"urgent\"\"\"");
        assertThat(TransactionCsv.escape("TRX-000001")).isEqualTo("TRX-000001");
    }

    @Test
    void formulasAreNeutralised() {
        assertThat(TransactionCsv.escape("=HYPERLINK(\"http://x\")")).startsWith("\"'=HYPERLINK");
        assertThat(TransactionCsv.escape("+22222000001")).isEqualTo("'+22222000001");
        assertThat(TransactionCsv.escape("@SUM(A1)")).isEqualTo("'@SUM(A1)");
    }
}
