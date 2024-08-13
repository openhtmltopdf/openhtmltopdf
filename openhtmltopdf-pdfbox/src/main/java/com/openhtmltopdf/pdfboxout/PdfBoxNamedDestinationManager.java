package com.openhtmltopdf.pdfboxout;

import com.openhtmltopdf.css.constants.CSSName;
import com.openhtmltopdf.css.constants.IdentValue;
import com.openhtmltopdf.layout.SharedContext;
import com.openhtmltopdf.render.Box;
import com.openhtmltopdf.render.RenderingContext;
import com.openhtmltopdf.util.LogMessageId;
import com.openhtmltopdf.util.XRLog;
import org.apache.pdfbox.cos.COSDictionary;
import org.apache.pdfbox.pdmodel.PDDestinationNameTreeNode;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentNameDictionary;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageDestination;
import org.apache.pdfbox.pdmodel.interactive.documentnavigation.destination.PDPageXYZDestination;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;

public class PdfBoxNamedDestinationManager {

    private final PDDocument _writer;
    private final SharedContext _sharedContext;
    private final float _dotsPerPoint;
    private final PdfBoxFastOutputDevice _od;

    public PdfBoxNamedDestinationManager(PDDocument doc, SharedContext ctx, float dotsPerPoint, PdfBoxFastOutputDevice od) {
        this._writer = doc;
        this._sharedContext = ctx;
        this._dotsPerPoint = dotsPerPoint;
        this._od = od;
    }

    public void processNamedDestinations(RenderingContext c, Box root) {
        Map<String, Box> idMap = _sharedContext.getIdMap();
        if (idMap != null && !idMap.isEmpty()) {
            Map<String, PDPageDestination> names = new LinkedHashMap<>();
            idMap.forEach((id, box) -> {
                if (box.getStyle().isIdent(CSSName.FS_NAMED_DESTINATION, IdentValue.CREATE)) {
                    names.put(id, createDestination(c, box, root));
                }
            });

            if (!names.isEmpty()) {
                PDDocumentNameDictionary nameTree = new PDDocumentNameDictionary(_writer.getDocumentCatalog());
                PDDestinationNameTreeNode dests = nameTree.getDests();
                if (dests == null) {
                    dests = new PDDestinationNameTreeNode(new COSDictionary());
                    nameTree.setDests(dests);
                }

                try {
                    Map<String, PDPageDestination> allNames = dests.getNames();
                    if (allNames == null) {
                        allNames = names;
                    } else {
                        allNames.putAll(names);
                    }

                    dests.setNames(allNames);
                } catch (IOException e) {
                    XRLog.log(Level.WARNING, LogMessageId.LogMessageId0Param.EXCEPTION_INVALID_DESTS_ARRAY, e);
                }
            }
        }
    }

    private PDPageXYZDestination createDestination(RenderingContext c, Box box, Box root) {
        return PdfBoxBookmarkManager.createBoxDestination(c, _od.getWriter(), _od, _dotsPerPoint, root, box);
    }
}
