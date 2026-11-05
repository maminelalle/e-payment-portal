package mr.epayment.portal.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.auth.KeycloakAdminClient;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Contrats de bout en bout : brouillon → PDF (stockage S3 réel) → envoi pour signature → email. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class ContractAdminTests {

    private static final String THREE_METHODS = """
            [{"code":"BANKILY","perTransactionLimit":50000,"monthlyLimit":500000},
             {"code":"MASRVI","perTransactionLimit":50000,"monthlyLimit":300000},
             {"code":"VISA","perTransactionLimit":100000,"monthlyLimit":1000000}]""";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    @MockitoBean
    private JavaMailSender mailSender;

    private String templateId;

    @BeforeEach
    void setUp() throws Exception {
        when(keycloak.createUser(anyString(), anyString())).thenAnswer(i -> UUID.randomUUID().toString());
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
        templateId = JsonPath.read(mvc.perform(get("/api/admin/contrats/modeles").with(admin()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
    }

    @Test
    void draftIsCompletedThenSentWithFrozenPdfAndEmail() throws Exception {
        String subscriberId = createSubscriber("Société Atlas Test");
        String id = createDraft(subscriberId)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andExpect(jsonPath("$.reference").value(org.hamcrest.Matchers.matchesPattern("CTR-\\d{4}-\\d{4,}")))
                .andReturn().getResponse().getContentAsString();
        id = JsonPath.read(id, "$.id");

        updateDraft(id, THREE_METHODS, "1.5")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.methods.length()").value(3))
                .andExpect(jsonPath("$.methods[0].shortLabel").exists());

        byte[] draftPdf = mvc.perform(get("/api/admin/contrats/{id}/pdf", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        String draftText = pdfText(draftPdf);
        assertThat(draftText).contains("Société Atlas Test", "Bankily", "Masrvi", "Visa", "50 000,00 MRU",
                "1 000 000,00 MRU", "1,50 %", "BROUILLON");

        String sent = mvc.perform(post("/api/admin/contrats/{id}/envoyer", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SIGNATURE"))
                .andExpect(jsonPath("$.sentAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        String sha256 = JsonPath.read(sent, "$.pdfSha256");

        byte[] finalPdf = mvc.perform(get("/api/admin/contrats/{id}/pdf", id).with(admin()))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(finalPdf))).isEqualTo(sha256);
        assertThat(pdfText(finalPdf)).doesNotContain("BROUILLON");

        // Une fois envoyé, le contrat est figé.
        updateDraft(id, THREE_METHODS, null).andExpect(status().isUnprocessableContent());
        mvc.perform(delete("/api/admin/contrats/{id}", id).with(admin())).andExpect(status().isUnprocessableContent());

        verify(mailSender, timeout(5000)).send(any(MimeMessage.class));
        assertThat(jdbc.queryForList("select action from audit_log where resource_id = ? order by id", String.class, id))
                .containsSubsequence("CONTRACT_DRAFT_CREATED", "CONTRACT_DRAFT_UPDATED", "CONTRACT_SENT");
    }

    @Test
    void incompleteDraftCannotBeSent() throws Exception {
        String id = JsonPath.read(createDraft(createSubscriber("Abonné incomplet")).andReturn().getResponse()
                .getContentAsString(), "$.id");

        mvc.perform(post("/api/admin/contrats/{id}/envoyer", id).with(admin()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.methods").exists())
                .andExpect(jsonPath("$.errors.startDate").exists())
                .andExpect(jsonPath("$.errors.endDate").exists());
    }

    @Test
    void limitsAndCatalogAreChecked() throws Exception {
        String id = JsonPath.read(createDraft(createSubscriber("Abonné plafonds")).andReturn().getResponse()
                .getContentAsString(), "$.id");

        updateDraft(id, "[{\"code\":\"BANKILY\",\"perTransactionLimit\":50000,\"monthlyLimit\":1000}]", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.methods").exists());
        updateDraft(id, "[{\"code\":\"SEDAD\",\"perTransactionLimit\":1,\"monthlyLimit\":1}]", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.methods").exists());
        updateDraft(id, "[{\"code\":\"BANKILY\",\"perTransactionLimit\":-5,\"monthlyLimit\":10}]", null)
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.perTransactionLimit").exists());
    }

    @Test
    void onlyOneContractInProgressPerSubscriber() throws Exception {
        String subscriberId = createSubscriber("Abonné unique");
        String first = JsonPath.read(createDraft(subscriberId).andReturn().getResponse().getContentAsString(), "$.id");
        String second = JsonPath.read(createDraft(subscriberId).andReturn().getResponse().getContentAsString(), "$.id");
        updateDraft(first, THREE_METHODS, null).andExpect(status().isOk());
        updateDraft(second, THREE_METHODS, null).andExpect(status().isOk());

        mvc.perform(post("/api/admin/contrats/{id}/envoyer", first).with(admin())).andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", second).with(admin()))
                .andExpect(status().isConflict());

        mvc.perform(get("/api/admin/contrats").param("abonne", subscriberId).with(admin()))
                .andExpect(jsonPath("$.totalElements").value(2));
        mvc.perform(delete("/api/admin/contrats/{id}", second).with(admin())).andExpect(status().isNoContent());
    }

    @Test
    void subscriberCannotManageContracts() throws Exception {
        mvc.perform(get("/api/admin/contrats").with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ABONNE"))))
                .andExpect(status().isForbidden());
    }

    private ResultActions createDraft(String subscriberId) throws Exception {
        return mvc.perform(post("/api/admin/contrats").with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("{\"subscriberId\":\"%s\",\"templateId\":\"%s\"}".formatted(subscriberId, templateId)));
    }

    private ResultActions updateDraft(String id, String methods, String commission) throws Exception {
        LocalDate start = LocalDate.now();
        return mvc.perform(put("/api/admin/contrats/{id}", id).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"templateId":"%s","startDate":"%s","endDate":"%s","commissionRate":%s,"methods":%s}"""
                        .formatted(templateId, start, start.plusYears(1).minusDays(1), commission, methods)));
    }

    private String createSubscriber(String name) throws Exception {
        String nif = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String body = mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MORALE","email":"c-%s@exemple.mr","details":{"displayName":"%s","nif":"%s","phone":"22000001"}}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 8), name, nif)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.subscriber.id");
    }

    private static String pdfText(byte[] pdf) throws Exception {
        try (var document = Loader.loadPDF(pdf)) {
            return new PDFTextStripper().getText(document);
        }
    }

    @Test
    void templatesAreListedPreviewedAndAtLeastOneStaysProposed() throws Exception {
        String body = mvc.perform(get("/api/admin/modeles").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code == 'STANDARD')].version").value(1))
                .andExpect(jsonPath("$[?(@.code == 'STANDARD')].status").value("ACTIVE"))
                .andReturn().getResponse().getContentAsString();
        String id = com.jayway.jsonpath.JsonPath.<java.util.List<String>>read(body, "$[?(@.code == 'STANDARD')].id")
                .getFirst();

        mvc.perform(get("/api/admin/modeles/{id}/apercu", id).with(admin()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/pdf"));
        // Seule version proposée : l'archiver laisserait l'assistant sans modèle
        mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                        .patch("/api/admin/modeles/{id}/statut", id).with(admin())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"active\":false}"))
                .andExpect(status().isUnprocessableContent());
        mvc.perform(get("/api/admin/modeles").with(jwt().authorities(
                        new org.springframework.security.core.authority.SimpleGrantedAuthority("ROLE_ABONNE"))))
                .andExpect(status().isForbidden());
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
