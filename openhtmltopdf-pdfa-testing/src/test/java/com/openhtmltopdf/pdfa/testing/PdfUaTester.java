package com.openhtmltopdf.pdfa.testing;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.pdfbox.io.IOUtils;
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

/**
 * PDF/UA-1 validation tests using veraPDF.
 * Validates structure tree ordering (rule 7.4.2-1) and link structure (rules 7.18.x).
 */
public class PdfUaTester {
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
        try (InputStream is = PdfUaTester.class.getResourceAsStream("/html/" + resource + ".html")) {
            if (is == null) {
                throw new IllegalStateException("HTML resource /html/" + resource + ".html not found");
            }
            htmlBytes = IOUtils.toByteArray(is);
        }
        String html = new String(htmlBytes, StandardCharsets.UTF_8);

        Files.createDirectories(Paths.get("target/test/artefacts/"));
        if (!Files.exists(Paths.get("target/test/artefacts/Karla-Bold.ttf"))) {
            try (InputStream in = PdfUaTester.class.getResourceAsStream("/fonts/Karla-Bold.ttf")) {
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
        builder.withHtmlContent(html, PdfUaTester.class.getResource("/html/").toString());
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
    public void testAllInOnePdfUa1() throws Exception {
        assertTrue(run("all-in-one"));
    }

    @Test
    public void testStructureWithRunningFooterLinks() throws Exception {
        assertTrue(run("pdfua-structure"));
    }

    @Test
    public void testDescriptionListsPdfUa1() throws Exception {
        assertTrue(run("pdfua-description-lists"));
    }

    /**
     * An absolutely positioned li::before, as used for custom list markers, must not
     * make a list item outside its list (GH issue #96).
     */
    @Test
    public void testPositionedListItemMarkerPdfUa1() throws Exception {
        assertTrue(run("pdfua-positioned-list-marker"));
    }

    /**
     * Content inside an opacity group is drawn in the group's form XObject, so it is
     * marked and numbered in that form's stream; the form gets its own parent tree
     * entry. GH issue #50.
     */
    @Test
    public void testOpacityGroupsPdfUa1() throws Exception {
        assertTrue(run("pdfua-opacity"));
        assertMarkedContentReferencesResolve("pdfua-opacity");
    }

    /**
     * veraPDF does not check that a marked-content reference leads to the content it
     * stands for: this checks that each one names an MCID that draws something in
     * its stream (the page's, or the form's given by /Stm), and that the parent tree
     * entry of that stream maps the MCID back to the referring structure element.
     */
    private static void assertMarkedContentReferencesResolve(String resource) throws Exception {
        try (org.apache.pdfbox.pdmodel.PDDocument doc = org.apache.pdfbox.Loader.loadPDF(
                new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            MarkedContentChecker checker = new MarkedContentChecker(doc);
            checker.walk(doc.getDocumentCatalog().getStructureTreeRoot().getCOSObject());
            assertTrue("references checked", checker.references > 0);
            assertTrue("references into a form", checker.formReferences > 0);
            assertTrue("broken references: " + checker.broken, checker.broken.isEmpty());
        }
    }

    private static class MarkedContentChecker {
        private final java.util.Map<Integer, org.apache.pdfbox.cos.COSArray> parentTree = new java.util.HashMap<>();
        private final java.util.Map<org.apache.pdfbox.cos.COSBase, java.util.Set<Integer>> drawn = new java.util.IdentityHashMap<>();
        private final List<String> broken = new java.util.ArrayList<>();
        private int references;
        private int formReferences;

        MarkedContentChecker(org.apache.pdfbox.pdmodel.PDDocument doc) {
            org.apache.pdfbox.cos.COSArray nums = doc.getDocumentCatalog().getStructureTreeRoot()
                    .getParentTree().getCOSObject().getCOSArray(org.apache.pdfbox.cos.COSName.NUMS);
            for (int i = 0; i < nums.size(); i += 2) {
                org.apache.pdfbox.cos.COSBase value = nums.getObject(i + 1);
                if (value instanceof org.apache.pdfbox.cos.COSArray) {
                    parentTree.put(((org.apache.pdfbox.cos.COSInteger) nums.getObject(i)).intValue(),
                            (org.apache.pdfbox.cos.COSArray) value);
                }
            }
        }

        void walk(org.apache.pdfbox.cos.COSDictionary node) throws IOException {
            org.apache.pdfbox.cos.COSBase k = node.getDictionaryObject(org.apache.pdfbox.cos.COSName.K);
            List<org.apache.pdfbox.cos.COSBase> kids = new java.util.ArrayList<>();
            if (k instanceof org.apache.pdfbox.cos.COSArray) {
                for (org.apache.pdfbox.cos.COSBase kid : (org.apache.pdfbox.cos.COSArray) k) {
                    kids.add(kid instanceof org.apache.pdfbox.cos.COSObject ? ((org.apache.pdfbox.cos.COSObject) kid).getObject() : kid);
                }
            } else if (k != null) {
                kids.add(k);
            }

            for (org.apache.pdfbox.cos.COSBase kid : kids) {
                if (kid instanceof org.apache.pdfbox.cos.COSInteger) {
                    check(node, (org.apache.pdfbox.cos.COSDictionary) node.getDictionaryObject(org.apache.pdfbox.cos.COSName.PG), null,
                            ((org.apache.pdfbox.cos.COSInteger) kid).intValue());
                } else if (kid instanceof org.apache.pdfbox.cos.COSDictionary) {
                    org.apache.pdfbox.cos.COSDictionary dict = (org.apache.pdfbox.cos.COSDictionary) kid;
                    org.apache.pdfbox.cos.COSName type = dict.getCOSName(org.apache.pdfbox.cos.COSName.TYPE);
                    if (org.apache.pdfbox.cos.COSName.MCR.equals(type)) {
                        org.apache.pdfbox.cos.COSDictionary page = (org.apache.pdfbox.cos.COSDictionary) (dict.containsKey(org.apache.pdfbox.cos.COSName.PG) ?
                                dict.getDictionaryObject(org.apache.pdfbox.cos.COSName.PG) : node.getDictionaryObject(org.apache.pdfbox.cos.COSName.PG));
                        check(node, page, (org.apache.pdfbox.cos.COSStream) dict.getDictionaryObject(org.apache.pdfbox.cos.COSName.getPDFName("Stm")),
                                dict.getInt(org.apache.pdfbox.cos.COSName.MCID));
                    } else if (!org.apache.pdfbox.cos.COSName.OBJR.equals(type)) {
                        walk(dict);
                    }
                }
            }
        }

        private void check(org.apache.pdfbox.cos.COSDictionary element, org.apache.pdfbox.cos.COSDictionary page,
                org.apache.pdfbox.cos.COSStream form, int mcid) throws IOException {
            references++;
            java.util.Set<Integer> mcids = new java.util.HashSet<>();
            org.apache.pdfbox.cos.COSDictionary owner;
            if (form != null) {
                formReferences++;
                owner = form;
                mcids.addAll(drawnMcids(form));
            } else {
                owner = page;
                java.util.Iterator<org.apache.pdfbox.pdmodel.common.PDStream> streams =
                        new org.apache.pdfbox.pdmodel.PDPage(page).getContentStreams();
                while (streams.hasNext()) {
                    mcids.addAll(drawnMcids(streams.next().getCOSObject()));
                }
            }

            org.apache.pdfbox.cos.COSArray parents = parentTree.get(owner.getInt(org.apache.pdfbox.cos.COSName.STRUCT_PARENTS, -1));
            boolean parentMatches = parents != null && mcid < parents.size() && parents.getObject(mcid) == element;
            if (!mcids.contains(mcid) || !parentMatches) {
                broken.add(element.getNameAsString(org.apache.pdfbox.cos.COSName.S) + " MCID " + mcid +
                        (form != null ? " in a form" : " on a page") +
                        (mcids.contains(mcid) ? "" : ", draws nothing") + (parentMatches ? "" : ", wrong parent"));
            }
        }

        /**
         * The MCIDs of the marked-content sequences in this stream that draw something.
         */
        private java.util.Set<Integer> drawnMcids(org.apache.pdfbox.cos.COSStream stream) throws IOException {
            java.util.Set<Integer> result = drawn.get(stream);
            if (result != null) {
                return result;
            }
            result = new java.util.HashSet<>();
            java.util.Set<String> painting = new java.util.HashSet<>(java.util.Arrays.asList(
                    "Tj", "TJ", "'", "\"", "Do", "f", "F", "f*", "S", "s", "B", "B*", "b", "b*", "sh"));
            java.util.Deque<Integer> open = new java.util.ArrayDeque<>();
            Object previous = null;
            for (Object token : new org.apache.pdfbox.pdfparser.PDFStreamParser(
                    new org.apache.pdfbox.pdmodel.common.PDStream(stream).toByteArray()).parse()) {
                if (token instanceof org.apache.pdfbox.contentstream.operator.Operator) {
                    String name = ((org.apache.pdfbox.contentstream.operator.Operator) token).getName();
                    if (name.equals("BDC")) {
                        open.push(previous instanceof org.apache.pdfbox.cos.COSDictionary &&
                                ((org.apache.pdfbox.cos.COSDictionary) previous).containsKey(org.apache.pdfbox.cos.COSName.MCID) ?
                                ((org.apache.pdfbox.cos.COSDictionary) previous).getInt(org.apache.pdfbox.cos.COSName.MCID) : -1);
                    } else if (name.equals("BMC")) {
                        open.push(-1);
                    } else if (name.equals("EMC")) {
                        if (!open.isEmpty()) {
                            open.pop();
                        }
                    } else if (painting.contains(name)) {
                        for (int id : open) {
                            if (id >= 0) {
                                result.add(id);
                            }
                        }
                    }
                }
                previous = token;
            }
            drawn.put(stream, result);
            return result;
        }
    }

    /**
     * Verifies that empty img elements (no src or broken src) do not cause
     * NPE in FigureStructualElement.finish() when building the PDF/UA
     * structure tree. The FigureContentItem may be null if no content was
     * painted for the replaced element.
     */
    @Test
    public void testEmptyFigureDoesNotCauseNpe() throws Exception {
        // Should not throw NPE — compliance is not required, just no crash.
        run("pdfua-figure-empty");
    }
}
