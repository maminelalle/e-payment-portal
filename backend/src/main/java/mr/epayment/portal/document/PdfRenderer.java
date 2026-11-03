package mr.epayment.portal.document;

import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Locale;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.helper.W3CDom;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.thymeleaf.templatemode.TemplateMode;
import org.thymeleaf.templateresolver.StringTemplateResolver;

/**
 * Transforme un modèle HTML Thymeleaf en PDF.
 * <p>
 * Les modèles sont du code exécuté par Thymeleaf : ils viennent des migrations, jamais d'une saisie libre
 * d'un utilisateur. Les variables, elles, sont échappées à l'affichage ({@code th:text}).
 */
@Service
public class PdfRenderer {

    private final TemplateEngine engine;

    PdfRenderer() {
        var resolver = new StringTemplateResolver();
        resolver.setTemplateMode(TemplateMode.HTML);
        resolver.setCacheable(false);
        this.engine = new TemplateEngine();
        this.engine.setTemplateResolver(resolver);
    }

    public String renderHtml(String template, Map<String, Object> variables) {
        return engine.process(template, new Context(Locale.FRENCH, variables));
    }

    public byte[] renderPdf(String template, Map<String, Object> variables) {
        String html = renderHtml(template, variables);
        try (var out = new ByteArrayOutputStream()) {
            new PdfRendererBuilder()
                    .useFastMode()
                    .withW3cDocument(new W3CDom().fromJsoup(Jsoup.parse(html)), "/")
                    .toStream(out)
                    .run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Génération du PDF impossible", e);
        }
    }
}
