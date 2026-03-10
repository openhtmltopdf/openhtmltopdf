package com.openhtmltopdf.pdfa.testing;

import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
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
import org.verapdf.pdfa.Foundries;
import org.verapdf.pdfa.PDFAParser;
import org.verapdf.pdfa.PDFAValidator;
import org.verapdf.pdfa.VeraGreenfieldFoundryProvider;
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
}
