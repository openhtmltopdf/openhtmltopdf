package com.openhtmltopdf.util;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.charset.StandardCharsets;

import org.junit.Test;

public class SVGUriDetectorTest {

    @Test
    public void testSvgDataUris() {
        assertTrue(SVGUriDetector.isSvgUri("data:image/svg+xml,%3Csvg%3E%3C/svg%3E"));
        assertTrue(SVGUriDetector.isSvgUri("data:image/svg+xml;base64,PHN2Zz48L3N2Zz4="));
        assertTrue(SVGUriDetector.isSvgUri("data:image/svg+xml;utf8,<svg></svg>"));
        assertTrue(SVGUriDetector.isSvgUri("data:image/svg+xml;charset=utf-8;base64,PHN2Zz48L3N2Zz4="));
        assertTrue(SVGUriDetector.isSvgUri("DATA:IMAGE/SVG+XML,%3Csvg%3E%3C/svg%3E"));

        assertFalse(SVGUriDetector.isSvgUri("data:image/png;base64,iVBORw0KGgo="));
        // A media type is not a media type without the comma that ends it.
        assertFalse(SVGUriDetector.isSvgUri("data:image/svg+xml"));
    }

    @Test
    public void testSvgFileUris() {
        assertTrue(SVGUriDetector.isSvgUri("logo.svg"));
        assertTrue(SVGUriDetector.isSvgUri("/images/logo.SVG"));
        assertTrue(SVGUriDetector.isSvgUri("https://example.com/logo.svg?v=2"));
        assertTrue(SVGUriDetector.isSvgUri("https://example.com/logo.svg#icon"));

        assertFalse(SVGUriDetector.isSvgUri("logo.png"));
        assertFalse(SVGUriDetector.isSvgUri("https://example.com/svg/logo.png"));
        assertFalse(SVGUriDetector.isSvgUri(null));
    }

    private static boolean looksLikeSvg(String content) {
        return SVGUriDetector.looksLikeSvgContent(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void testContentSniffing() {
        assertTrue(looksLikeSvg("<svg xmlns='http://www.w3.org/2000/svg'/>"));
        assertTrue(looksLikeSvg("  \n<?xml version='1.0'?>\n<svg/>"));
        assertTrue(looksLikeSvg("﻿<svg/>"));
        assertTrue(looksLikeSvg("<!-- a comment --><s:svg xmlns:s='http://www.w3.org/2000/svg'/>"));

        assertFalse(looksLikeSvg("<html><body>not an image</body></html>"));
        assertFalse(looksLikeSvg("PNG\r\n"));
        assertFalse(looksLikeSvg(""));
        assertFalse(SVGUriDetector.looksLikeSvgContent(null));
    }
}
