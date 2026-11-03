package mr.epayment.portal.signature;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;

/**
 * Emplacement du cachet de signature visible : le cadre « Emplacement de la signature » du modèle de contrat,
 * repéré par son texte ; à défaut, le bas de la dernière page. Coordonnées en points depuis le coin haut gauche
 * de la page (convention EU DSS).
 */
record SignatureStampPosition(int page, float x, float y, float width, float height) {

    static final String MARKER = "Emplacement de la signature";

    static final float HEIGHT = 46;
    /** Marges du modèle (20 mm sur les côtés, 24 mm en bas) : le cachet reste dans la zone de texte. */
    private static final float SIDE_MARGIN = 56.7f;
    private static final float BOTTOM_MARGIN = 68;
    /** Écart entre la ligne de base du texte repère et le haut du cadre (taille de police + marge intérieure). */
    private static final float MARKER_TO_BOX_TOP = 18;
    private static final float MARKER_TO_BOX_LEFT = 9;

    static SignatureStampPosition locate(byte[] pdf) {
        try (PDDocument document = Loader.loadPDF(pdf)) {
            int pages = document.getNumberOfPages();
            for (int page = pages; page >= 1; page--) {
                TextPosition first = findMarker(document, page);
                if (first != null) {
                    // Le cachet recouvre tout le cadre (centré sur la page, 1 pt de plus de chaque côté pour masquer
                    // le pointillé) et le texte repère.
                    var box = document.getPage(page - 1).getMediaBox();
                    float left = first.getXDirAdj() - MARKER_TO_BOX_LEFT - 1;
                    float top = Math.min(first.getYDirAdj() - MARKER_TO_BOX_TOP - 1,
                            box.getHeight() - BOTTOM_MARGIN - HEIGHT);
                    return new SignatureStampPosition(page, left, top, box.getWidth() - 2 * left, HEIGHT);
                }
            }
            var last = document.getPage(pages - 1).getMediaBox();
            return new SignatureStampPosition(pages, SIDE_MARGIN, last.getHeight() - BOTTOM_MARGIN - HEIGHT,
                    last.getWidth() - 2 * SIDE_MARGIN, HEIGHT);
        } catch (IOException e) {
            throw new UncheckedIOException("Lecture du PDF à signer impossible", e);
        }
    }

    private static TextPosition findMarker(PDDocument document, int page) throws IOException {
        TextPosition[] found = new TextPosition[1];
        var stripper = new PDFTextStripper() {
            @Override
            protected void writeString(String text, List<TextPosition> positions) throws IOException {
                if (found[0] == null && text.contains(MARKER)) {
                    found[0] = positions.get(text.indexOf(MARKER));
                }
                super.writeString(text, positions);
            }
        };
        stripper.setStartPage(page);
        stripper.setEndPage(page);
        stripper.getText(document);
        return found[0];
    }
}
