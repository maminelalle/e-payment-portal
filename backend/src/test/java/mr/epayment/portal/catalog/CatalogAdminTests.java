package mr.epayment.portal.catalog;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.concurrent.ThreadLocalRandom;
import mr.epayment.portal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class CatalogAdminTests {

    @Autowired
    private MockMvc mvc;

    @Test
    void catalogContainsTheDemoMethodsAndTheInactiveOnes() throws Exception {
        mvc.perform(get("/api/admin/moyens").with(admin()))
                .andExpect(status().isOk())
                // D'autres tests ajoutent des moyens : on vérifie la présence de ceux livrés, pas la liste exacte.
                .andExpect(jsonPath("$[*].code", org.hamcrest.Matchers.hasItems("BANKILY", "MASRVI", "VISA", "SEDAD",
                        "MASTERCARD")))
                .andExpect(jsonPath("$[?(@.code == 'SEDAD')].status").value("INACTIVE"))
                .andExpect(jsonPath("$[?(@.code == 'MASTERCARD')].provider.code").value("VISA_GATEWAY"))
                .andExpect(jsonPath("$[?(@.code == 'VISA')].category").value("CARD"))
                .andExpect(jsonPath("$[?(@.code == 'BANKILY')].provider.code").value("BANKILY"));
    }

    @Test
    void adminCanDeactivateAndReactivateAMethod() throws Exception {
        changeStatus("MASRVI", "INACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("INACTIVE"));
        changeStatus("MASRVI", "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        changeStatus("INCONNU", "ACTIVE").andExpect(status().isNotFound());
        changeStatus("MASRVI", "PEUT-ETRE").andExpect(status().isBadRequest());
    }

    @Test
    void providersShowWhichOnesAreConnected() throws Exception {
        mvc.perform(get("/api/admin/moyens/fournisseurs").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("ENABLED"))
                .andExpect(jsonPath("$[?(@.code == 'VISA_GATEWAY')].kind").value("CARD"))
                .andExpect(jsonPath("$[?(@.code == 'BANKILY')].kind").value("WALLET"))
                .andExpect(jsonPath("$[?(@.code == 'SEDAD')].status").value("DISABLED"));
    }

    @Test
    void adminAddsAMethodOnAConnectedProviderThenEditsItsTexts() throws Exception {
        String code = "AMEX_" + ThreadLocalRandom.current().nextInt(100_000, 999_999);
        mvc.perform(post("/api/admin/moyens").with(admin()).contentType(MediaType.APPLICATION_JSON).content("""
                        {"code":" %s ","providerCode":"VISA_GATEWAY","currency":"mru","details":{"name":"American Express",
                         "shortLabel":"ax","color":"#2e77bb","description":"Carte bancaire · passerelle bancaire",
                         "instructions":"Vous serez redirigé vers la page sécurisée de la banque.",
                         "conditions":"Cartes émises en Mauritanie uniquement."}}""".formatted(code.toLowerCase())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.category").value("CARD"))
                .andExpect(jsonPath("$.status").value("INACTIVE"))
                .andExpect(jsonPath("$.currency").value("MRU"))
                .andExpect(jsonPath("$.shortLabel").value("AX"))
                .andExpect(jsonPath("$.color").value("#2E77BB"))
                .andExpect(jsonPath("$.conditions").value("Cartes émises en Mauritanie uniquement."));

        // Même code : refusé ; fournisseur non raccordé, code ou couleur invalides : refusés champ par champ.
        mvc.perform(post("/api/admin/moyens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(newMethod(code, "VISA_GATEWAY", "#000000")))
                .andExpect(status().isConflict());
        mvc.perform(post("/api/admin/moyens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(newMethod("CLICK", "SEDAD", "#000000")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.providerCode").exists());
        mvc.perform(post("/api/admin/moyens").with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content(newMethod("1CLICK", "BANKILY", "rouge")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.code").exists())
                .andExpect(jsonPath("$.errors.color").exists());

        // Modification des textes ; le code, le fournisseur et la devise ne bougent pas.
        mvc.perform(put("/api/admin/moyens/{code}", code).with(admin()).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Amex","shortLabel":"AX","color":"#2E77BB","description":null,
                                 "instructions":"Nouvelle consigne.","conditions":"   "}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Amex"))
                .andExpect(jsonPath("$.instructions").value("Nouvelle consigne."))
                .andExpect(jsonPath("$.conditions").doesNotExist())
                .andExpect(jsonPath("$.provider.code").value("VISA_GATEWAY"));
        changeStatus(code, "ACTIVE").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ACTIVE"));
        changeStatus(code, "INACTIVE").andExpect(status().isOk());
    }

    @Test
    void aMethodWhoseProviderIsNotConnectedCannotBeActivated() throws Exception {
        changeStatus("SEDAD", "ACTIVE")
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("pas encore raccordé")));
    }

    @Test
    void logoIsUploadedCheckedAndServedPublicly() throws Exception {
        byte[] png = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0x0D, 'I', 'H', 'D', 'R'};
        String body = mvc.perform(multipart(HttpMethod.PUT, "/api/admin/moyens/BANKILY/logo")
                        .file(new MockMultipartFile("file", "bankily.png", "image/png", png)).with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logoUrl").value(org.hamcrest.Matchers.startsWith("/api/public/moyens/BANKILY/logo?v=")))
                .andReturn().getResponse().getContentAsString();
        String logoUrl = com.jayway.jsonpath.JsonPath.read(body, "$.logoUrl");

        // Public (balise <img>), type déclaré d'après le contenu, cache long
        mvc.perform(get(logoUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"))
                .andExpect(content().bytes(png))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));

        // Un fichier qui n'est pas une image (même nommé .png) ou trop lourd est refusé
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/moyens/BANKILY/logo")
                        .file(new MockMultipartFile("file", "faux.png", "image/png", "<svg onload=alert(1)>".getBytes()))
                        .with(admin()))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.logo").exists());
        byte[] tooBig = new byte[300 * 1024];
        System.arraycopy(png, 0, tooBig, 0, png.length);
        mvc.perform(multipart(HttpMethod.PUT, "/api/admin/moyens/BANKILY/logo")
                        .file(new MockMultipartFile("file", "gros.png", "image/png", tooBig)).with(admin()))
                .andExpect(status().isBadRequest());

        mvc.perform(delete("/api/admin/moyens/BANKILY/logo").with(admin()))
                .andExpect(jsonPath("$.logoUrl").doesNotExist());
        mvc.perform(get("/api/public/moyens/BANKILY/logo")).andExpect(status().isNotFound());
    }

    @Test
    void subscriberCannotChangeLogos() throws Exception {
        mvc.perform(delete("/api/admin/moyens/BANKILY/logo")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_ABONNE"))))
                .andExpect(status().isForbidden());
    }

    private static String newMethod(String code, String provider, String color) {
        return """
                {"code":"%s","providerCode":"%s","currency":"MRU","details":{"name":"Test","shortLabel":"TS",
                 "color":"%s"}}""".formatted(code, provider, color);
    }

    private org.springframework.test.web.servlet.ResultActions changeStatus(String code, String status) throws Exception {
        return mvc.perform(patch("/api/admin/moyens/{code}/statut", code).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
