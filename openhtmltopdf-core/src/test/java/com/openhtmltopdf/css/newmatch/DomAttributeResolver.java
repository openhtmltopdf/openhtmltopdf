package com.openhtmltopdf.css.newmatch;

import org.w3c.dom.Element;

import com.openhtmltopdf.css.extend.AttributeResolver;

/** Plain-DOM attribute resolver for matcher tests: classes and ids come straight from the attributes. */
final class DomAttributeResolver implements AttributeResolver {
    @Override
    public String getAttributeValue(Object o, String attrName) {
        Element e = (Element) o;
        return e.hasAttribute(attrName) ? e.getAttribute(attrName) : null;
    }

    @Override
    public String getAttributeValue(Object o, String namespaceURI, String attrName) {
        Element e = (Element) o;
        if (namespaceURI == null || namespaceURI.isEmpty()) {
            return getAttributeValue(o, attrName);
        }
        return e.hasAttributeNS(namespaceURI, attrName) ? e.getAttributeNS(namespaceURI, attrName) : null;
    }

    @Override public String getClass(Object e) { return getAttributeValue(e, "class"); }
    @Override public String getID(Object e) { return getAttributeValue(e, "id"); }
    @Override public String getNonCssStyling(Object e) { return null; }
    @Override public String getElementStyling(Object e) { return null; }
    @Override public String getLang(Object e) { return null; }
    @Override public boolean isLink(Object e) { return false; }
    @Override public boolean isVisited(Object e) { return false; }
    @Override public boolean isHover(Object e) { return false; }
    @Override public boolean isActive(Object e) { return false; }
    @Override public boolean isFocus(Object e) { return false; }
    @Override public boolean isMarker(Object e) { return false; }
}
