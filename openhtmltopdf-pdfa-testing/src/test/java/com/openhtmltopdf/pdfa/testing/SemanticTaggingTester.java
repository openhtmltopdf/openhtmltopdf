package com.openhtmltopdf.pdfa.testing;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.junit.BeforeClass;
import org.junit.Test;
import org.verapdf.gf.foundry.VeraGreenfieldFoundryProvider;
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraPDFFoundry;
import org.verapdf.pdfa.flavours.PDFAFlavour;
import org.verapdf.pdfa.results.TestAssertion;
import org.verapdf.pdfa.results.TestAssertion.Status;
import org.verapdf.pdfa.results.ValidationResult;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.util.XRLog;

public class SemanticTaggingTester {
    @BeforeClass
    public static void initialize() {
        VeraGreenfieldFoundryProvider.initialise();
        XRLog.listRegisteredLoggers().forEach(log -> XRLog.setLevel(log, Level.WARNING));
    }

    private static <T> Predicate<T> distinctByKey(Function<? super T, ?> keyExtractor) {
        Set<Object> seen = ConcurrentHashMap.newKeySet();
        return t -> seen.add(keyExtractor.apply(t));
    }

    private boolean run(String resource) throws Exception {
        byte[] htmlBytes;
        try (InputStream is = SemanticTaggingTester.class.getResourceAsStream("/html/" + resource + ".html")) {
            if (is == null) {
                throw new IllegalStateException("HTML resource /html/" + resource + ".html not found");
            }
            htmlBytes = IOUtils.toByteArray(is);
        }
        String html = new String(htmlBytes, StandardCharsets.UTF_8);

        Files.createDirectories(Paths.get("target/test/artefacts/"));
        if (!Files.exists(Paths.get("target/test/artefacts/Karla-Bold.ttf"))) {
            try (InputStream in = SemanticTaggingTester.class.getResourceAsStream("/fonts/Karla-Bold.ttf")) {
                if (in == null) {
                    throw new IllegalStateException("Font resource /fonts/Karla-Bold.ttf not found");
                }
                Files.write(Paths.get("target/test/artefacts/Karla-Bold.ttf"), IOUtils.toByteArray(in));
            }
        }

        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.usePdfVersion(1.7f);
        builder.usePdfUaAccessibility(true);
        builder.useFont(new File("target/test/artefacts/Karla-Bold.ttf"), "TestFont");
        builder.withHtmlContent(html, SemanticTaggingTester.class.getResource("/html/").toString());
        builder.useExternalResourceAccessControl((uri, type) -> true, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.useExternalResourceAccessControl((uri, type) -> true, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        builder.toStream(baos);
        builder.run();
        byte[] pdfBytes = baos.toByteArray();

        Files.createDirectories(Paths.get("target/test/pdf/"));
        Files.write(Paths.get("target/test/pdf/" + resource + "--PDFUA_1.pdf"), pdfBytes);

        PDFAFlavour flavour = PDFAFlavour.PDFUA_1;

        try (VeraPDFFoundry foundry = Foundries.defaultInstance();
             InputStream is = new ByteArrayInputStream(pdfBytes);
             PDFAValidator validator = foundry.createValidator(flavour, true);
             PDFAParser parser = foundry.createParser(is, flavour)) {

            ValidationResult result = validator.validate(parser);

            List<TestAssertion> asserts = result.getTestAssertions().stream()
                    .filter(ta -> ta.getStatus() == Status.FAILED)
                    .filter(distinctByKey(TestAssertion::getRuleId))
                    .collect(Collectors.toList());

            String errs = asserts.stream()
                    .map(ta -> String.format("%s\n    %s", ta.getMessage().replaceAll("\\s+", " "), ta.getLocation().getContext()))
                    .collect(Collectors.joining("\n    ", "[\n    ", "\n]"));

            System.err.format("\nDISTINCT ERRORS(%s--PDFUA_1) (%d): %s\n", resource, asserts.size(), errs);

            return asserts.isEmpty() && result.isCompliant();
        }
    }
    @Test
    public void testBlockDisplayedTableIsTagged() throws Exception {
        assertTrue(run("pdfua-block-table"));
        List<String> tags = structureTypes("pdfua-block-table");
        assertEquals("one Table, from the anonymous table box", 1, tags.stream().filter("Table"::equals).count());
        assertEquals("both rows", 2, tags.stream().filter("TR"::equals).count());
        assertEquals("every cell", 4, tags.stream().filter("TD"::equals).count());
        assertEquals("the linked image keeps its Figure", Collections.singletonList("App"), figureAlts("pdfua-block-table"));
    }

    @Test
    public void testTableDisplayedAsTableKeepsItsStructure() throws Exception {
        assertTrue(run("pdfua-real-table"));
        List<String> tags = structureTypes("pdfua-real-table");
        assertEquals(1, tags.stream().filter("Table"::equals).count());
        assertEquals(2, tags.stream().filter("TR"::equals).count());
        assertEquals(2, tags.stream().filter("TH"::equals).count());
        assertEquals(2, tags.stream().filter("TD"::equals).count());
    }

    @Test
    public void testDecorativeImageIsAnArtifact() throws Exception {
        assertTrue(run("pdfua-decorative-image"));
        assertEquals(Collections.singletonList("Garden tile"), figureAlts("pdfua-decorative-image"));
    }

    @Test
    public void testCssTableLayoutIsNotATable() throws Exception {
        assertTrue(run("pdfua-css-table-cell"));
        assertEquals(0, structureTypes("pdfua-css-table-cell").stream().filter("Table"::equals).count());
    }

    private static List<String> structureTypes(String resource) throws Exception {
        List<String> types = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            collect(doc.getDocumentCatalog().getStructureTreeRoot(), types, null);
        }
        return types;
    }

    private static List<String> figureAlts(String resource) throws Exception {
        List<String> alts = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            collect(doc.getDocumentCatalog().getStructureTreeRoot(), new ArrayList<>(), alts);
        }
        return alts;
    }

    private static void collect(PDStructureNode node, List<String> types, List<String> figureAlts) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement element = (PDStructureElement) kid;
                types.add(element.getStructureType());
                if (figureAlts != null && "Figure".equals(element.getStructureType())) {
                    figureAlts.add(element.getAlternateDescription());
                }
                collect(element, types, figureAlts);
            }
        }
    }
}
