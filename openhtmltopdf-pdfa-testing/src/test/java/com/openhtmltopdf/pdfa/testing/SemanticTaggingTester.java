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
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.stream.Collectors;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.contentstream.operator.Operator;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdfparser.PDFStreamParser;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.documentinterchange.markedcontent.PDPropertyList;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureElement;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureNode;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotation;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAnnotationLink;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.outline.PDOutlineItem;
import org.apache.pdfbox.pdmodel.common.COSObjectable;
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
    public void testDecorativeImageInARunningLinkIsAnArtifact() throws Exception {
        assertTrue(run("pdfua-running-decorative-image"));
        assertEquals(Collections.emptyList(), figureAlts("pdfua-running-decorative-image"));
    }

    @Test
    public void testCssTableLayoutIsNotATable() throws Exception {
        assertTrue(run("pdfua-css-table-cell"));
        assertEquals(0, structureTypes("pdfua-css-table-cell").stream().filter("Table"::equals).count());
    }

    @Test
    public void testBlockDisplayedTableWithACaptionIsTagged() throws Exception {
        assertTrue(run("pdfua-block-table-caption"));
        List<String> tags = structureTypes("pdfua-block-table-caption");
        assertEquals("one Table", 1, tags.stream().filter("Table"::equals).count());
        assertEquals("both rows", 2, tags.stream().filter("TR"::equals).count());
        assertEquals("every cell", 4, tags.stream().filter("TD"::equals).count());
    }

    @Test
    public void testTableShapesAroundATableElementAreTagged() throws Exception {
        assertTrue(run("pdfua-table-shapes"));
        List<String> tags = structureTypes("pdfua-table-shapes");
        assertEquals("one Table per table element", 12, tags.stream().filter("Table"::equals).count());
        assertEquals("the two header cells", 2, tags.stream().filter("TH"::equals).count());
        assertEquals("one Figure per described image", 8, figureAlts("pdfua-table-shapes").size());
    }

    @Test
    public void testListItemDisplayedTableIsTagged() throws Exception {
        assertTrue(run("pdfua-table-list-item"));
        List<String> tags = structureTypes("pdfua-table-list-item");
        assertEquals("one Table", 1, tags.stream().filter("Table"::equals).count());
        assertEquals("no label for the marker", 0, tags.stream().filter("Lbl"::equals).count());
        assertRowsSitInTheTable("pdfua-table-list-item");
        assertEquals("both rows", 2, tags.stream().filter("TR"::equals).count());
        assertEquals("every cell", 4, tags.stream().filter("TD"::equals).count());
        assertEquals("the described image keeps its Figure", Collections.singletonList("Icon"), figureAlts("pdfua-table-list-item"));
    }

    @Test
    public void testListItemDisplayedDivMarkerIsAnArtifact() throws Exception {
        assertTrue(run("pdfua-div-list-item"));
        List<String> tags = structureTypes("pdfua-div-list-item");
        assertEquals("no label", 0, tags.stream().filter("Lbl"::equals).count());
        assertEquals("no list item", 0, tags.stream().filter("LI"::equals).count());
        assertEquals("each div's marker is drawn inside a Layout artifact", 2,
                paintedLayoutArtifacts("pdfua-div-list-item"));
    }

    @Test
    public void testInlineDisplayedTableIsTagged() throws Exception {
        assertTrue(run("pdfua-table-inline"));
        List<String> tags = structureTypes("pdfua-table-inline");
        assertEquals("one Table", 1, tags.stream().filter("Table"::equals).count());
        assertRowsSitInTheTable("pdfua-table-inline");
        assertEquals("both rows", 2, tags.stream().filter("TR"::equals).count());
        assertEquals("every cell", 4, tags.stream().filter("TD"::equals).count());
        assertEquals("the described image keeps its Figure", Collections.singletonList("Icon"), figureAlts("pdfua-table-inline"));
    }

    @Test
    public void testListItemDisplayedBoxesInsideAListAreTagged() throws Exception {
        assertTrue(run("pdfua-list-item-in-a-list"));
        List<String> tags = structureTypes("pdfua-list-item-in-a-list");
        assertEquals("one list", 1, tags.stream().filter("L"::equals).count());
        assertEquals("the real item and one synthetic item per box", 3,
                tags.stream().filter("LI"::equals).count());
        assertEquals("one Table", 1, tags.stream().filter("Table"::equals).count());
        assertRowsSitInTheTable("pdfua-list-item-in-a-list");
        assertNoSyntheticMarkerIsWritten("pdfua-list-item-in-a-list");
    }

    @Test
    public void testParagraphDirectlyInAListIsTagged() throws Exception {
        assertTrue(run("pdfua-paragraph-in-a-list"));
        List<String> tags = structureTypes("pdfua-paragraph-in-a-list");
        assertEquals("the real item and the synthetic one", 2,
                tags.stream().filter("LI"::equals).count());
        assertEquals(Collections.singletonList("LBody"), parentsOf("pdfua-paragraph-in-a-list", "P"));
    }

    @Test
    public void testEmptyAndGeneratedChildrenOfAListAddNoItems() throws Exception {
        assertTrue(run("pdfua-list-empty-children"));
        List<String> tags = structureTypes("pdfua-list-empty-children");
        assertEquals("one LI per li element", 5, tags.stream().filter("LI"::equals).count());
        assertEquals("the four lists only", 4, tags.stream().filter("L"::equals).count());
        assertNoSyntheticMarkerIsWritten("pdfua-list-empty-children");
    }

    @Test
    public void testTextAfterTheLastItemKeepsItsPosition() throws Exception {
        assertTrue(run("pdfua-list-tail-text"));
        assertEquals(Arrays.asList("Lbl LBody", "LBody"), listItemShapes("pdfua-list-tail-text"));
    }

    @Test
    public void testALinkOrABookmarkTargetInAListStaysInTheTree() throws Exception {
        assertTrue(run("pdfua-list-link-and-target"));
        assertLinksAndBookmarksReachTheRoot("pdfua-list-link-and-target", 1);
        assertNoSyntheticMarkerIsWritten("pdfua-list-link-and-target");
    }

    @Test
    public void testAnInlineLinkOrTargetInAListStaysInTheTree() throws Exception {
        assertTrue(run("pdfua-list-inline-link-and-target"));
        assertLinksAndBookmarksReachTheRoot("pdfua-list-inline-link-and-target", 1);
        assertNoSyntheticMarkerIsWritten("pdfua-list-inline-link-and-target");
        assertEquals("an unused id alone adds no list item", 5,
                structureTypes("pdfua-list-inline-link-and-target").stream().filter("LI"::equals).count());
    }

    private static void assertLinksAndBookmarksReachTheRoot(String resource, int expectedLinks) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            int links = 0;
            for (PDPage page : doc.getPages()) {
                for (PDAnnotation annotation : page.getAnnotations()) {
                    if (annotation instanceof PDAnnotationLink) {
                        Object parent = root.getParentTree().getValue(annotation.getStructParent());
                        PDStructureElement link = parent instanceof PDStructureElement
                                ? (PDStructureElement) parent
                                : (PDStructureElement) PDStructureNode.create((COSDictionary) ((COSObjectable) parent).getCOSObject());
                        assertEquals("Link", link.getStructureType());
                        assertTrue("the link reaches the structure tree root", reachesTheRoot(link));
                        links++;
                    }
                }
            }
            assertEquals(expectedLinks, links);
            PDOutlineItem bookmark = doc.getDocumentCatalog().getDocumentOutline().getFirstChild();
            assertTrue("the bookmark names a structure element", bookmark.getStructureElement() != null);
            assertTrue("the bookmark target reaches the structure tree root",
                    reachesTheRoot(bookmark.getStructureElement()));
        }
    }

    @Test
    public void testOnlyTheContentChildOfAMixedListGetsAnItem() throws Exception {
        assertTrue(run("pdfua-list-mixed-children"));
        assertEquals(Arrays.asList("Lbl LBody", "LBody"), listItemShapes("pdfua-list-mixed-children"));
        assertEquals(Collections.singletonList("LBody"), parentsOf("pdfua-list-mixed-children", "P"));
    }

    private static void assertNoSyntheticMarkerIsWritten(String resource) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            assertTrue("no structure element keeps the synthetic-item marker",
                    noMarker(doc.getDocumentCatalog().getStructureTreeRoot()));
        }
    }

    private static boolean noMarker(PDStructureNode node) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement element = (PDStructureElement) kid;
                if (element.getCOSObject().containsKey(COSName.getPDFName("SyntheticListItem"))
                        || !noMarker(element)) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean reachesTheRoot(PDStructureElement element) {
        PDStructureNode node = element;
        for (int depth = 0; depth < 64 && node instanceof PDStructureElement; depth++) {
            node = ((PDStructureElement) node).getParent();
        }
        return node instanceof PDStructureTreeRoot;
    }

    private static List<String> listItemShapes(String resource) throws Exception {
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            PDStructureElement list = firstOfType(doc.getDocumentCatalog().getStructureTreeRoot(), "L");
            List<String> shapes = new ArrayList<>();
            for (Object kid : list.getKids()) {
                if (kid instanceof PDStructureElement && "LI".equals(((PDStructureElement) kid).getStructureType())) {
                    List<String> types = new ArrayList<>();
                    for (Object grandKid : ((PDStructureElement) kid).getKids()) {
                        if (grandKid instanceof PDStructureElement) {
                            types.add(((PDStructureElement) grandKid).getStructureType());
                        }
                    }
                    shapes.add(String.join(" ", types));
                }
            }
            return shapes;
        }
    }

    private static PDStructureElement firstOfType(PDStructureNode node, String type) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement element = (PDStructureElement) kid;
                if (type.equals(element.getStructureType())) {
                    return element;
                }
                PDStructureElement nested = firstOfType(element, type);
                if (nested != null) {
                    return nested;
                }
            }
        }
        return null;
    }

    private static void assertRowsSitInTheTable(String resource) throws Exception {
        List<String> parents = parentsOf(resource, "TR");
        assertTrue("rows found", !parents.isEmpty());
        for (String parent : parents) {
            assertTrue("a TR sits in a Table, THead, TBody or TFoot, not in " + parent,
                    parent.equals("Table") || parent.equals("THead") || parent.equals("TBody")
                            || parent.equals("TFoot"));
        }
    }

    private static List<String> parentsOf(String resource, String type) throws Exception {
        List<String> parents = new ArrayList<>();
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            collectParents(doc.getDocumentCatalog().getStructureTreeRoot(), "StructTreeRoot", type, parents);
        }
        return parents;
    }

    private static void collectParents(PDStructureNode node, String nodeType, String type, List<String> parents) {
        for (Object kid : node.getKids()) {
            if (kid instanceof PDStructureElement) {
                PDStructureElement element = (PDStructureElement) kid;
                if (type.equals(element.getStructureType())) {
                    parents.add(nodeType);
                }
                collectParents(element, element.getStructureType(), type, parents);
            }
        }
    }

    private static int paintedLayoutArtifacts(String resource) throws Exception {
        List<String> painting = Arrays.asList("f", "F", "f*", "B", "B*", "b", "b*", "S", "s",
                "Tj", "TJ", "'", "\"", "Do", "sh");
        int count = 0;
        try (PDDocument doc = Loader.loadPDF(new File("target/test/pdf/" + resource + "--PDFUA_1.pdf"))) {
            for (PDPage page : doc.getPages()) {
                List<Object> tokens = new PDFStreamParser(page).parse();
                int depth = 0;
                int layoutDepth = -1;
                boolean painted = false;
                for (int i = 0; i < tokens.size(); i++) {
                    Object token = tokens.get(i);
                    if (!(token instanceof Operator)) {
                        continue;
                    }
                    String name = ((Operator) token).getName();
                    if ("BDC".equals(name) || "BMC".equals(name)) {
                        depth++;
                        if ("BDC".equals(name) && layoutDepth < 0 && i >= 2
                                && COSName.ARTIFACT.equals(tokens.get(i - 2))
                                && COSName.getPDFName("Layout").equals(
                                        properties(page, tokens.get(i - 1)).getItem(COSName.TYPE))) {
                            layoutDepth = depth;
                            painted = false;
                        }
                    } else if ("EMC".equals(name)) {
                        if (depth == layoutDepth) {
                            if (painted) {
                                count++;
                            }
                            layoutDepth = -1;
                        }
                        depth--;
                    } else if (layoutDepth > 0 && painting.contains(name)) {
                        painted = true;
                    }
                }
            }
        }
        return count;
    }

    private static COSDictionary properties(PDPage page, Object operand) {
        if (operand instanceof COSDictionary) {
            return (COSDictionary) operand;
        }
        if (operand instanceof COSName && page.getResources() != null) {
            PDPropertyList list = page.getResources().getProperties((COSName) operand);
            if (list != null) {
                return list.getCOSObject();
            }
        }
        return new COSDictionary();
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
