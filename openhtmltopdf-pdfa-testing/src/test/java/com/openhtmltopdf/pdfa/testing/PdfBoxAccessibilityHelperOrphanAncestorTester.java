package com.openhtmltopdf.pdfa.testing;

import com.openhtmltopdf.outputdevice.helper.ExternalResourceControlPriority;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder.PdfAConformance;
import com.openhtmltopdf.util.XRLog;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.cos.COSArray;
import org.apache.pdfbox.cos.COSBase;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.io.IOUtils;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.documentinterchange.logicalstructure.PDStructureTreeRoot;
import org.junit.BeforeClass;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Random;
import java.util.logging.Level;

import static org.junit.Assert.assertEquals;

/**
 * Reproduces the orphan-ancestor leak fixed in
 * {@link com.openhtmltopdf.pdfboxout.PdfBoxAccessibilityHelper#ensureAncestorTree}.
 *
 * <p>When a paginated document with multi-font subsetting and many
 * {@code page-break-inside: avoid} blocks crosses ~30k tagged content items,
 * {@code ensureAncestorTree} can produce a chain whose top-most intermediate
 * is reachable only via parent pointers. {@code finishTreeItems} walks the
 * children list, never reaches it, and the descendant content items end up
 * with {@code parentElem} unset. veraPDF reports those as PDF/UA-1 7.1-t3
 * ("Content not tagged") and PDFBox sees them as null entries in the
 * structure tree's {@code /ParentTree} {@code /Nums} array.
 *
 * <p>The check walks {@code /StructTreeRoot/ParentTree/Nums} directly with
 * PDFBox rather than running veraPDF: validating a 35k-row PDF through
 * veraPDF takes minutes and dwarfs the rendering itself, while the
 * structure-tree traverser produces a deterministic count in seconds and
 * is sensitive to exactly the defect this fix targets. Without the fix the
 * same fixture produces ~140 null entries on the same engine build.
 *
 * <p>The test is heavy (~2 minutes wall-clock, ~1 GB heap), so it follows
 * the existing {@code *Tester} naming convention used elsewhere in this
 * module: Surefire's default include patterns ({@code *Test} / {@code Test*}
 * / {@code *Tests} / {@code *TestCase}) skip it during a plain
 * {@code mvn test}. Run explicitly:
 * <pre>
 *   mvn -pl openhtmltopdf-pdfa-testing -am \
 *     -Dtest=PdfBoxAccessibilityHelperOrphanAncestorTester \
 *     -Dsurefire.failIfNoSpecifiedTests=false test
 * </pre>
 *
 * <p>The fixture font is a ~14 KB subset of Noto Sans CJK Regular,
 * Copyright (c) 2014, 2015 Adobe Systems Incorporated (http://www.adobe.com/),
 * licensed under the SIL Open Font License 1.1
 * (https://scripts.sil.org/OFL). The original font's source and the SIL
 * OFL text are at https://github.com/notofonts/noto-cjk and
 * https://scripts.sil.org/OFL respectively. The subset retains only the
 * codepoints used by this fixture's synthetic phrases and is bundled with
 * its NOTICE and OFL.txt under the same {@code fonts/} resource folder.
 */
public class PdfBoxAccessibilityHelperOrphanAncestorTester {

    /**
     * The orphan-ancestor leak only surfaces on large documents. We tested a
     * range of sizes and the bug shows up reliably from ~35,000 paginated
     * rows upward; smaller fixtures don't trip it.
     */
    private static final int ROW_COUNT = 35_000;

    /**
     * Synthetic placeholder phrases. Real meaning is irrelevant — what
     * matters is that the row content forces the renderer to subset glyphs
     * from the bundled CJK fallback font, since the orphan-ancestor leak
     * is sensitive to mixed-script subsetting.
     */
    private static final String[] PHRASES = {
        "Lorem ipsum dolor sit",       // Latin
        "Suspendisse vitae elit nec",  // Latin
        "Quisque tristique vehicula",  // Latin
        "Лорем ипсум долор сит",       // Cyrillic (lorem ipsum transliterated)
        "Воркен мауно турпис",         // Cyrillic
        "로렘 입숨 돌로르",              // Hangul
        "사메트 콘셀",                   // Hangul
        "ロレム イプサム ドロル",          // Hiragana
        "から受取",                      // Hiragana + Han
        "向 汇款",                       // Han
        "从 收到",                       // Han
    };

    @BeforeClass
    public static void initialize() {
        XRLog.listRegisteredLoggers().forEach(log -> XRLog.setLevel(log, Level.WARNING));
    }

    @Test
    public void parentTreeHasNoOrphanedEntries() throws Exception {
        byte[] pdfBytes = render(buildHtml(ROW_COUNT));
        long orphans = countOrphanedParentTreeEntries(pdfBytes);
        assertEquals(
            "Found " + orphans + " null entries in /StructTreeRoot/ParentTree/Nums; "
                + "every paginated content item must resolve to a real PDStructureElement.",
            0L, orphans);
    }

    /**
     * Walks {@code /StructTreeRoot/ParentTree/Nums} and counts how many
     * value entries are null or empty. A correctly emitted UA-1 document
     * has zero of these; the orphan-ancestor leak surfaces here as null
     * indirect references.
     */
    private static long countOrphanedParentTreeEntries(byte[] pdfBytes) throws Exception {
        try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
            PDStructureTreeRoot root = doc.getDocumentCatalog().getStructureTreeRoot();
            if (root == null) {
                throw new IllegalStateException("PDF has no StructTreeRoot");
            }
            COSDictionary parentTreeDict =
                (COSDictionary) root.getCOSObject().getDictionaryObject(COSName.PARENT_TREE);
            if (parentTreeDict == null) {
                throw new IllegalStateException("StructTreeRoot has no /ParentTree");
            }
            COSArray nums = collectNums(parentTreeDict);
            long orphans = 0;
            for (int i = 1; i < nums.size(); i += 2) {
                COSBase val = nums.getObject(i);
                if (val == null) {
                    orphans++;
                } else if (val instanceof COSArray) {
                    COSArray arr = (COSArray) val;
                    for (int j = 0; j < arr.size(); j++) {
                        COSBase entry = arr.getObject(j);
                        if (entry == null) {
                            orphans++;
                        } else if (entry instanceof COSDictionary
                                && ((COSDictionary) entry).size() == 0) {
                            orphans++;
                        }
                    }
                }
            }
            return orphans;
        }
    }

    /** Recursively concatenates every {@code /Nums} array reachable below {@code node}. */
    private static COSArray collectNums(COSDictionary node) {
        COSArray out = new COSArray();
        COSBase nums = node.getDictionaryObject(COSName.NUMS);
        if (nums instanceof COSArray) {
            out.addAll((COSArray) nums);
        }
        COSBase kids = node.getDictionaryObject(COSName.KIDS);
        if (kids instanceof COSArray) {
            COSArray kidsArr = (COSArray) kids;
            for (int i = 0; i < kidsArr.size(); i++) {
                COSBase k = kidsArr.getObject(i);
                if (k instanceof COSDictionary) {
                    out.addAll(collectNums((COSDictionary) k));
                }
            }
        }
        return out;
    }

    private static String buildHtml(int rows) throws Exception {
        String shell = readResource("/html/orphan-ancestor-shell.html");
        String rowTemplate = readResource("/html/orphan-ancestor-row.html");
        Random rng = new Random(42);
        StringBuilder body = new StringBuilder(rows * 256);
        for (int i = 0; i < rows; i++) {
            String desc = PHRASES[rng.nextInt(PHRASES.length)];
            body.append(rowTemplate
                .replace("{{desc}}", desc)
                .replace("{{i}}", Integer.toString(i)));
        }
        return shell.replace("{{rows}}", body.toString());
    }

    private static String readResource(String resource) throws Exception {
        try (InputStream in = PdfBoxAccessibilityHelperOrphanAncestorTester.class
                .getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource: " + resource);
            }
            return new String(IOUtils.toByteArray(in), java.nio.charset.StandardCharsets.UTF_8);
        }
    }

    private static byte[] render(String html) throws Exception {
        File interFont = stageResource("/fonts/Karla-Bold.ttf", "Karla-Bold.ttf");
        // Subset of Noto Sans CJK Regular (SIL OFL 1.1, see fonts/OFL.txt and
        // fonts/NotoSansCJK-OrphanReproducer-subset.NOTICE for attribution).
        // Loading a CJK font alongside the Latin font is what reliably trips
        // the orphan-ancestor leak at this scale; the subset keeps the
        // bundled artefact small while still exercising the code path.
        File cjkFont = stageResource(
            "/fonts/NotoSansCJK-OrphanReproducer-subset.ttf",
            "NotoSansCJK-OrphanReproducer-subset.ttf");

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.usePdfUaAccessibility(true);
        builder.usePdfAConformance(PdfAConformance.PDFA_3_A);
        builder.useFont(interFont, "Inter");
        builder.useFont(cjkFont, "Noto Sans CJK");
        builder.withHtmlContent(html, null);
        builder.useExternalResourceAccessControl(
            (uri, type) -> true, ExternalResourceControlPriority.RUN_AFTER_RESOLVING_URI);
        builder.useExternalResourceAccessControl(
            (uri, type) -> true, ExternalResourceControlPriority.RUN_BEFORE_RESOLVING_URI);
        try (InputStream colorProfile = PdfBoxAccessibilityHelperOrphanAncestorTester.class
                .getResourceAsStream("/colorspaces/sRGB.icc")) {
            builder.useColorProfile(IOUtils.toByteArray(colorProfile));
        }
        builder.toStream(baos);
        builder.run();
        return baos.toByteArray();
    }

    private static File stageResource(String resource, String fileName) throws Exception {
        Files.createDirectories(Paths.get("target/test/artefacts"));
        java.nio.file.Path target = Paths.get("target/test/artefacts", fileName);
        if (!Files.exists(target)) {
            try (InputStream in = PdfBoxAccessibilityHelperOrphanAncestorTester.class
                    .getResourceAsStream(resource)) {
                if (in == null) {
                    throw new IllegalStateException("Missing test resource: " + resource);
                }
                Files.write(target, IOUtils.toByteArray(in));
            }
        }
        return target.toFile();
    }
}
