package com.openhtmltopdf.pdfboxout;

import java.awt.geom.AffineTransform;
import java.io.IOException;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

import com.openhtmltopdf.util.LogMessageId;
import org.apache.pdfbox.cos.COSName;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDResources;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.interactive.annotation.PDAppearanceStream;
import org.apache.pdfbox.pdmodel.interactive.form.PDAcroForm;
import org.apache.pdfbox.pdmodel.interactive.form.PDChoice;
import org.apache.pdfbox.pdmodel.interactive.form.PDField;
import org.apache.pdfbox.pdmodel.interactive.form.PDTextField;
import org.w3c.dom.Element;
import org.w3c.dom.Node;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.pdfboxout.PdfBoxForm.CheckboxStyle;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.util.ArrayUtil;
import com.openhtmltopdf.util.XRLog;

/**
 * The per-document container for form and form control state.
 */
public class PdfBoxPerDocumentFormState {

    private static final String DEFAULT_APPEARANCE = "q\nQ\n";

    // We keep a map of forms for the document so we can add controls to the correct form as they are seen.
    private final Map<Element, PdfBoxForm> forms = new HashMap<>();

    // The list of controls in the document. Control class contains all the info we need to output a control.
    private final List<PdfBoxForm.Control> controls = new ArrayList<>();

    // A set of controls, so we don't double process a control.
    private final Set<Element> seenControls = new HashSet<>();

    // We keep a map of fonts to font resource name so we don't double add fonts needed for form controls.
    private final Map<PDFont, String> controlFonts = new HashMap<>();

    // The checkbox style to appearance stream map. We only create appearance streams on demand and once for a specific
    // style so we store appearance streams created here.
    private final Map<CheckboxStyle, PDAppearanceStream> checkboxAppearances = new EnumMap<>(CheckboxStyle.class);

    // Again, we only create these appearance streams as needed.
    private PDAppearanceStream checkboxOffAppearance;
    private PDAppearanceStream radioBoxOffAppearance;
    private PDAppearanceStream radioBoxOnAppearance;
private PDAppearanceStream signatureAppearance;
    // The ZapfDingbats font resource needed by checkbox and radio box appearance streams.
    private PDResources checkBoxFontResource;

    // PDF/A forbids non-embedded fonts, so check marks are drawn as paths instead of zapf dingbats.
    private boolean pdfAConform;

    public PDAppearanceStream getCheckboxStyle(CheckboxStyle style) {
        return checkboxAppearances.get(style);
    }

    public PDAppearanceStream getCheckboxOffStream() {
        return this.checkboxOffAppearance;
    }

    public PDAppearanceStream getRadioOffStream() {
        return this.radioBoxOffAppearance;
    }

    public PDAppearanceStream getRadioOnStream() {
        return this.radioBoxOnAppearance;
    }
public PDAppearanceStream getSignatureStream() {
        return this.signatureAppearance;
    }
    /**
     * Adds a form to a map to be used later by <code>processControls</code>.
     */
    public void addFormIfRequired(Box box, PdfBoxOutputDevice od) {
        if (!forms.containsKey(box.getElement())) {
            PdfBoxForm frm = PdfBoxForm.createForm(box.getElement(), this, od);
            forms.put(box.getElement(), frm);
        }
    }

    /**
     * Adds a control to a list to be used later by <code>processControls</code>.
     */
    public void addControlIfRequired(Box box, PDPage page, AffineTransform transform, RenderingContext c, float pageHeight) {
        if (!seenControls.contains(box.getElement())) {
            controls.add(new PdfBoxForm.Control(box, page, new AffineTransform(transform), c, pageHeight));
            seenControls.add(box.getElement());
        }
    }

    private String getControlFont(SharedContext sharedContext, PdfBoxForm.Control ctrl, RenderingContext renderingContext) {
        PDFont fnt = ((PdfBoxFSFont) sharedContext.getFont(ctrl.box.getStyle().getFont(renderingContext))).getFontDescriptions().get(0).getFont();
        String fontName;

        if (!controlFonts.containsKey(fnt)) {
            fontName = "OpenHTMLFont" + controlFonts.size();
            controlFonts.put(fnt, fontName);
        } else {
            fontName = controlFonts.get(fnt);
        }

        return fontName;
    }
private void createSignatureAppearanceStreams(PDDocument writer) {

        // this appearance is just a dummy to make the validation happy
        signatureAppearance = PdfBoxForm.createCheckboxAppearance(DEFAULT_APPEARANCE, writer, checkBoxFontResource);
    }

    private void createCheckboxAppearanceStreams(PDDocument writer, PdfBoxForm.Control ctrl) {
        CheckboxStyle style = CheckboxStyle.fromIdent(ctrl.box.getStyle().getIdent(CSSName.FS_CHECKBOX_STYLE));

        checkboxAppearances.computeIfAbsent(style, k -> createCheckmarkAppearance(style, writer));

        if (checkboxOffAppearance == null) {
            checkboxOffAppearance = PdfBoxForm.createCheckboxAppearance(DEFAULT_APPEARANCE, writer, checkBoxFontResource);
        }
    }

    private void createRadioboxAppearanceStream(PDDocument writer) {
        if (radioBoxOffAppearance == null) {
            radioBoxOffAppearance = PdfBoxForm.createCheckboxAppearance(DEFAULT_APPEARANCE, writer, checkBoxFontResource);
        }

        if (radioBoxOnAppearance == null) {
            radioBoxOnAppearance = createCheckmarkAppearance(CheckboxStyle.DIAMOND, writer);
        }
    }

    private PDAppearanceStream createCheckmarkAppearance(CheckboxStyle style, PDDocument writer) {
        return pdfAConform ?
                PdfBoxForm.createCheckboxVectorAppearance(style, writer) :
                PdfBoxForm.createCheckboxAppearance(style, writer, checkBoxFontResource);
    }

    private void createCheckboxFontResource() {
        if (checkBoxFontResource == null && !pdfAConform) {
            checkBoxFontResource = new PDResources();
            checkBoxFontResource.put(COSName.getPDFName("OpenHTMLZap"), new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS));
        }
    }

    public void processControls(SharedContext sharedContext, PDDocument writer, Box root, RenderingContext renderingContext) {
        processControls(sharedContext, writer, root, renderingContext, false);
    }

    /**
     * Turns the collected controls into AcroForm fields. In PDF/A mode the NeedAppearances
     * flag is forbidden, so appearance streams are generated for every field instead.
     */
    public void processControls(SharedContext sharedContext, PDDocument writer, Box root, RenderingContext renderingContext, boolean pdfAConform) {
        this.pdfAConform = pdfAConform;
        boolean anyFormControl = false;

        for (PdfBoxForm.Control ctrl : controls) {
            PdfBoxForm frm = findEnclosingForm(ctrl.box.getElement());
            String fontName = null;

            if (ctrl.box.getElement().getAttribute("type").equals("text") && ctrl.box.getElement().getAttribute("class").contains("signature")) {
                createSignatureAppearanceStreams(writer);
            } else if (!ArrayUtil.isOneOf(ctrl.box.getElement().getAttribute("type"), "checkbox", "radio", "hidden")) {
                // Need to embed a font for every control other than checkbox, radio and hidden.
                fontName = getControlFont(sharedContext, ctrl, renderingContext);
            } else if (ctrl.box.getElement().getAttribute("type").equals("checkbox")) {
                createCheckboxFontResource();
                createCheckboxAppearanceStreams(writer, ctrl);
            } else if (ctrl.box.getElement().getAttribute("type").equals("radio")) {
                createCheckboxFontResource();
                createRadioboxAppearanceStream(writer);
            }

            if (frm != null) {
                frm.addControl(ctrl, fontName);
                anyFormControl = true;
            }
        }

        // Forms without controls (e.g. a <form> wrapping plain content) would produce an
        // empty AcroForm, which is useless and rejected by some consumers such as signing
        // services, so leave it out. Issue 289.
        if (anyFormControl) {
            processForms(writer, root, pdfAConform);
        }
    }

    private void processForms(PDDocument writer, Box root, boolean pdfAConform) {
        PDResources resources = new PDResources();
        /* Default fonts */
        resources.put(COSName.HELV, new PDType1Font(Standard14Fonts.FontName.HELVETICA));
        resources.put(COSName.ZA_DB, new PDType1Font(Standard14Fonts.FontName.ZAPF_DINGBATS));
        for (Map.Entry<PDFont, String> fnt : controlFonts.entrySet()) {
            resources.put(COSName.getPDFName(fnt.getValue()), fnt.getKey());
        }

        int start = 0;
        PDAcroForm acro = new PDAcroForm(writer);

        acro.setDefaultAppearance("/Helv 0 Tf 0 g");
        acro.setNeedAppearances(Boolean.TRUE);
        acro.setDefaultResources(resources);

        for (PdfBoxForm frm : forms.values()) {
            try {
                start = 1 + frm.process(acro, start, root);
            } catch (IOException e) {
                throw new PdfContentStreamAdapter.PdfException("processControls", e);
            }
        }

        if (pdfAConform) {
            // PDF/A forbids NeedAppearances=true (ISO 19005-1 6.9, ISO 19005-2 6.4.1),
            // so every widget needs its own appearance stream.
            acro.setNeedAppearances(Boolean.FALSE);
            try {
                addFieldTextToFontSubsets(acro);
                acro.refreshAppearances();
            } catch (IOException e) {
                throw new PdfContentStreamAdapter.PdfException("processControls", e);
            }
        }

        writer.getDocumentCatalog().setAcroForm(acro);
    }

    /**
     * Generated appearance streams show the field values with the embedded control fonts.
     * These fonts are subset to the glyphs of the page content, so add the field text too.
     */
    private void addFieldTextToFontSubsets(PDAcroForm acro) {
        StringBuilder text = new StringBuilder();
        for (PDField field : acro.getFieldTree()) {
            if (field instanceof PDTextField) {
                String value = ((PDTextField) field).getValue();
                if (value != null) {
                    text.append(value);
                }
            } else if (field instanceof PDChoice) {
                ((PDChoice) field).getOptionsDisplayValues().forEach(text::append);
            }
        }

        for (PDFont fnt : controlFonts.keySet()) {
            if (fnt.willBeSubset()) {
                text.codePoints().forEach(fnt::addToSubset);
            }
        }
    }

    /**
     * Helper function to find an enclosing PdfBoxForm given a control element.
     */
    private PdfBoxForm findEnclosingForm(Node e) {
        Element frmElement = DOMUtil.findClosestEnclosingElementWithNodeName(e, "form");

        if (frmElement != null &&
                forms.containsKey(frmElement)) {
            return forms.get(frmElement);
        }

        XRLog.log(Level.WARNING, LogMessageId.LogMessageId1Param.GENERAL_PDF_FOUND_FORM_CONTROL_WITH_NO_ENCLOSING_FORM, e.getNodeName());
        return null;
    }
}
