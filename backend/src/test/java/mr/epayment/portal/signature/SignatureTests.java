package mr.epayment.portal.signature;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import mr.epayment.portal.auth.KeycloakAdminClient;
import mr.epayment.portal.signature.OtpSender.OtpMessage;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
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

/** Signature électronique de bout en bout : code par email (simulé), scellement PAdES réel, stockage S3 réel. */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class SignatureTests {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private JdbcTemplate jdbc;

    @MockitoBean
    private KeycloakAdminClient keycloak;

    @MockitoBean
    private JavaMailSender mailSender;

    @MockitoBean
    private OtpSender otpSender;

    private String subscriberSubject;

    @BeforeEach
    void setUp() {
        subscriberSubject = UUID.randomUUID().toString();
        when(keycloak.createUser(anyString(), anyString())).thenReturn(subscriberSubject);
        when(mailSender.createMimeMessage()).thenAnswer(i -> new MimeMessage((Session) null));
    }

    @Test
    void subscriberSignsWithEmailCodeAndGetsSealedPdf() throws Exception {
        String contractId = contractSentToNewSubscriber("Société Signature");

        mvc.perform(get("/api/abonne/contrat").with(subscriber()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING_SIGNATURE"));
        byte[] original = mvc.perform(get("/api/abonne/contrat/pdf").with(subscriber()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();

        var challenge = requestCode().andExpect(status().isOk())
                .andExpect(jsonPath("$.sentTo").value(org.hamcrest.Matchers.matchesPattern("s••••••@exemple\\.mr")))
                .andExpect(jsonPath("$.maxAttempts").value(3))
                .andReturn().getResponse().getContentAsString();
        String signatureId = JsonPath.read(challenge, "$.signatureId");
        String code = lastCode();

        confirm(signatureId, wrong(code)).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.remainingAttempts").value(2));

        String signed = confirm(signatureId, code).andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SIGNED"))
                .andExpect(jsonPath("$.signedAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        byte[] sealed = mvc.perform(get("/api/abonne/contrat/pdf").with(subscriber()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(sha256(sealed)).isEqualTo(JsonPath.read(signed, "$.signedPdfSha256"));
        try (var pdf = Loader.loadPDF(sealed)) {
            assertThat(pdf.getSignatureDictionaries()).hasSize(1);
            var seal = pdf.getSignatureDictionaries().getFirst();
            assertThat(seal.getSubFilter()).isEqualTo("ETSI.CAdES.detached");
            assertThat(seal.getReason()).contains("Société Signature", "code à usage unique vérifié");
            assertThat(new PDFTextStripper().getText(pdf)).contains("Société Signature", "Bankily");
            // Cachet visible posé dans le cadre de signature, en dernière page.
            var widget = pdf.getSignatureFields().getFirst().getWidgets().getFirst();
            assertThat(widget.getRectangle().getHeight()).isEqualTo(SignatureStampPosition.HEIGHT);
            assertThat(widget.getPage()).isEqualTo(pdf.getPage(pdf.getNumberOfPages() - 1));
            assertThat(widget.getAppearance().getNormalAppearance().getAppearanceStream().getCOSObject().getLength())
                    .isPositive();
        }

        Map<String, Object> proof = jdbc.queryForMap(
                "select status, document_sha256, signed_document_sha256, host(ip_address) ip, otp_attempts "
                        + "from signature where id = ?::uuid", signatureId);
        assertThat(proof.get("status")).isEqualTo("SIGNED");
        assertThat(proof.get("document_sha256")).isEqualTo(sha256(original));
        assertThat(proof.get("signed_document_sha256")).isEqualTo(sha256(sealed));
        assertThat(proof.get("ip")).isNotNull();
        assertThat(jdbc.queryForList("select action from audit_log where resource_id = ? order by id", String.class,
                signatureId)).containsSubsequence("SIGNATURE_CODE_SENT", "SIGNATURE_CODE_REJECTED", "SIGNATURE_COMPLETED");

        // Activation automatique juste après la signature (moteur des droits, semaine 7).
        mvc.perform(get("/api/admin/contrats/{id}", contractId).with(admin()))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
        requestCode().andExpect(status().isUnprocessableContent());
    }

    @Test
    void threeWrongCodesInvalidateTheRequest() throws Exception {
        contractSentToNewSubscriber("Société Essais");
        String signatureId = JsonPath.read(requestCode().andReturn().getResponse().getContentAsString(), "$.signatureId");
        String code = lastCode();

        confirm(signatureId, wrong(code)).andExpect(jsonPath("$.remainingAttempts").value(2));
        confirm(signatureId, wrong(code)).andExpect(jsonPath("$.remainingAttempts").value(1));
        confirm(signatureId, wrong(code)).andExpect(jsonPath("$.remainingAttempts").value(0));
        confirm(signatureId, code).andExpect(status().isUnprocessableContent());

        assertThat(jdbc.queryForObject("select status from signature where id = ?::uuid", String.class, signatureId))
                .isEqualTo("FAILED");
    }

    @Test
    void expiredCodeIsRefused() throws Exception {
        contractSentToNewSubscriber("Société Expiration");
        String signatureId = JsonPath.read(requestCode().andReturn().getResponse().getContentAsString(), "$.signatureId");
        jdbc.update("update signature set otp_expires_at = now() - interval '1 second' where id = ?::uuid", signatureId);

        confirm(signatureId, lastCode()).andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("expiré")));
    }

    @Test
    void codeCannotBeRequestedAgainImmediately() throws Exception {
        contractSentToNewSubscriber("Société Renvoi");
        requestCode().andExpect(status().isOk());
        requestCode().andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("s.")));
    }

    @Test
    void subscriberWithoutContractHasNothingToSign() throws Exception {
        createSubscriber("Société Sans Contrat");
        mvc.perform(get("/api/abonne/contrat").with(subscriber())).andExpect(status().isNoContent());
        requestCode().andExpect(status().isUnprocessableContent());
    }

    @Test
    void adminCannotUseSubscriberSpace() throws Exception {
        mvc.perform(get("/api/abonne/contrat").with(admin())).andExpect(status().isForbidden());
    }

    private ResultActions requestCode() throws Exception {
        return mvc.perform(post("/api/abonne/contrat/signature").with(subscriber()));
    }

    private ResultActions confirm(String signatureId, String code) throws Exception {
        return mvc.perform(post("/api/abonne/contrat/signature/{id}/confirmation", signatureId).with(subscriber())
                .contentType(MediaType.APPLICATION_JSON).content("{\"code\":\"" + code + "\"}"));
    }

    private String lastCode() {
        var captor = ArgumentCaptor.forClass(OtpMessage.class);
        verify(otpSender, org.mockito.Mockito.atLeastOnce()).send(captor.capture());
        return captor.getValue().code();
    }

    private static String wrong(String code) {
        return code.equals("000000") ? "111111" : "000000";
    }

    private String contractSentToNewSubscriber(String name) throws Exception {
        String subscriberId = createSubscriber(name);
        String templateId = JsonPath.read(mvc.perform(get("/api/admin/contrats/modeles").with(admin()))
                .andReturn().getResponse().getContentAsString(), "$[0].id");
        String contractId = JsonPath.read(mvc.perform(post("/api/admin/contrats").with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"subscriberId\":\"%s\",\"templateId\":\"%s\"}".formatted(subscriberId, templateId)))
                .andReturn().getResponse().getContentAsString(), "$.id");
        LocalDate start = LocalDate.now();
        mvc.perform(put("/api/admin/contrats/{id}", contractId).with(admin()).contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"templateId":"%s","startDate":"%s","endDate":"%s","methods":[
                        {"code":"BANKILY","perTransactionLimit":50000,"monthlyLimit":500000}]}"""
                        .formatted(templateId, start, start.plusYears(1)))).andExpect(status().isOk());
        mvc.perform(post("/api/admin/contrats/{id}/envoyer", contractId).with(admin())).andExpect(status().isOk());
        return contractId;
    }

    private String createSubscriber(String name) throws Exception {
        String nif = String.valueOf(ThreadLocalRandom.current().nextInt(10_000_000, 99_999_999));
        String body = mvc.perform(post("/api/admin/abonnes").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"type":"MORALE","email":"s%s@exemple.mr","details":{"displayName":"%s","nif":"%s","phone":"22000001"}}"""
                                .formatted(UUID.randomUUID().toString().substring(0, 8), name, nif)))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.subscriber.id");
    }

    private RequestPostProcessor subscriber() {
        return jwt().jwt(token -> token.subject(subscriberSubject)).authorities(new SimpleGrantedAuthority("ROLE_ABONNE"));
    }

    private static RequestPostProcessor admin() {
        return jwt().jwt(token -> token.claim("email", "admin@epayment.mr"))
                .authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private static String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
    }
}
