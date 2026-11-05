package mr.epayment.portal.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.time.LocalDate;
import java.util.List;
import mr.epayment.portal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class AdminDashboardTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void figuresMatchTheDatabase() throws Exception {
        String body = mvc.perform(get("/api/admin/tableau-de-bord").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days").value(30))
                .andExpect(jsonPath("$.perDay.length()").value(30))
                .andExpect(jsonPath("$.perDay[29].day").value(LocalDate.now(AdminDashboardController.ZONE).toString()))
                .andExpect(jsonPath("$.latestTransactions.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)))
                .andExpect(jsonPath("$.contractsToProcess.length()").value(org.hamcrest.Matchers.lessThanOrEqualTo(5)))
                .andReturn().getResponse().getContentAsString();

        assertThat(((Number) JsonPath.read(body, "$.activeSubscribers")).longValue()).isEqualTo(
                jdbc.queryForObject("select count(*) from subscriber where status = 'ACTIVE'", Long.class));
        assertThat(((Number) JsonPath.read(body, "$.contractsPendingSignature")).longValue()).isEqualTo(
                jdbc.queryForObject("select count(*) from contract where status = 'PENDING_SIGNATURE'", Long.class));
        assertThat(((Number) JsonPath.read(body, "$.transactionsThisMonth.current")).longValue()).isEqualTo(
                jdbc.queryForObject("select count(*) from payment_transaction "
                        + "where created_at >= date_trunc('month', now() at time zone 'UTC') at time zone 'UTC'",
                        Long.class));

        // Conception §3.1 : abonnés et contrats par statut, tous les statuts présents (à 0 si besoin).
        for (String status : List.of("PENDING", "ACTIVE", "SUSPENDED", "DISABLED")) {
            assertThat(((Number) JsonPath.read(body, "$.subscribersByStatus." + status)).longValue()).isEqualTo(
                    jdbc.queryForObject("select count(*) from subscriber where status = ?", Long.class, status));
        }
        for (String status : List.of("DRAFT", "PENDING_SIGNATURE", "SIGNED", "ACTIVE", "SUSPENDED", "EXPIRED",
                "TERMINATED")) {
            assertThat(((Number) JsonPath.read(body, "$.contractsByStatus." + status)).longValue()).isEqualTo(
                    jdbc.queryForObject("select count(*) from contract where status = ?", Long.class, status));
        }
        // Transactions de la période : par statut (6 statuts), par moyen, par type et par fournisseur, mêmes totaux.
        List<Integer> byStatus = JsonPath.read(body, "$.transactionsByStatus[*].count");
        assertThat(byStatus).hasSize(6);
        long periodTotal = jdbc.queryForObject("select count(*) from payment_transaction "
                + "where created_at >= (current_date - 29)::timestamp at time zone 'UTC'", Long.class);
        assertThat(sum(byStatus)).isEqualTo(periodTotal);
        assertThat(sum(JsonPath.<List<Integer>>read(body, "$.perMethod[*].count"))).isEqualTo(periodTotal);
        assertThat(sum(JsonPath.<List<Integer>>read(body, "$.perCategory[*].count"))).isEqualTo(periodTotal);
        assertThat(sum(JsonPath.<List<Integer>>read(body, "$.perProvider[*].count"))).isEqualTo(periodTotal);

        mvc.perform(get("/api/admin/tableau-de-bord?jours=7").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"))))
                .andExpect(jsonPath("$.perDay.length()").value(7));
    }

    private static long sum(List<? extends Number> values) {
        return values.stream().mapToLong(Number::longValue).sum();
    }

    @Test
    void subscribersCannotSeeIt() throws Exception {
        mvc.perform(get("/api/admin/tableau-de-bord").with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_ABONNE"))))
                .andExpect(status().isForbidden());
    }
}
