package mr.epayment.portal.catalog;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import mr.epayment.portal.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
    void catalogContainsTheThreeDemoMethodsWithTheirProvider() throws Exception {
        mvc.perform(get("/api/admin/moyens").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].code", containsInAnyOrder("BANKILY", "MASRVI", "VISA")))
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

    private org.springframework.test.web.servlet.ResultActions changeStatus(String code, String status) throws Exception {
        return mvc.perform(patch("/api/admin/moyens/{code}/statut", code).with(admin())
                .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"" + status + "\"}"));
    }

    private static RequestPostProcessor admin() {
        return jwt().authorities(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }
}
