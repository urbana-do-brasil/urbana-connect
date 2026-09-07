package br.com.urbana.connect.interfaces.rest;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class TermsConsentStaticAssetTest {
    @Test
    void exposesAccessibleDecisionLabelsAndNoInlineExecutableMarkup() throws IOException {
        String html = resource("static/termos/index.html");

        assertThat(html).contains("lang=\"pt-BR\"", "Li e aceito os termos", "Não aceito",
                        "id=\"terms-content\"", "aria-live=\"polite\"")
                .doesNotContain("onclick=", "onload=", "<iframe");
    }

    @Test
    void keepsTheBearerInMemoryOnlyAndUsesTextContentForLegalText() throws IOException {
        String javascript = resource("static/termos/terms.js");

        assertThat(javascript).contains("history.replaceState", "textContent = presentation.content",
                        "Authorization: 'Bearer ' + token", "cache: 'no-store")
                .doesNotContain("innerHTML", "localStorage", "sessionStorage");
    }

    private static String resource(String path) throws IOException {
        try (InputStream stream = TermsConsentStaticAssetTest.class.getClassLoader().getResourceAsStream(path)) {
            assertThat(stream).as("classpath resource %s", path).isNotNull();
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
