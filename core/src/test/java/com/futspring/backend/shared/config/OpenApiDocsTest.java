package com.futspring.backend.shared.config;

import com.futspring.backend.support.BaseIntegrationTest;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// springdoc: off by default (production), public with the Bearer scheme when API_DOCS_ENABLED=true (dev)
class OpenApiDocsTest {

    @Nested
    class Disabled extends BaseIntegrationTest {

        @Test
        void apiDocs_areNotPublic() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isUnauthorized());
            mockMvc.perform(get("/swagger-ui.html")).andExpect(status().isUnauthorized());
        }
    }

    @Nested
    @TestPropertySource(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    class Enabled extends BaseIntegrationTest {

        @Test
        void apiDocs_arePublic_andDeclareTheBearerScheme() throws Exception {
            mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.components.securitySchemes.bearer.scheme").value("bearer"))
                    .andExpect(jsonPath("$.security[0].bearer").exists())
                    .andExpect(content().string(containsString("/api/v1/peladas/{id}")));
        }

        @Test
        void swaggerUi_isPublic() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }
}
