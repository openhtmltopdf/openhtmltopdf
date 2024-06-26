/*
 * Condition.java
 * Copyright (c) 2004, 2005 Torbjoern Gannholm
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public License
 * as published by the Free Software Foundation; either version 2.1
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.	See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 */
package com.openhtmltopdf.css.newmatch;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.openhtmltopdf.css.extend.AttributeResolver;
import com.openhtmltopdf.css.extend.TreeResolver;
import com.openhtmltopdf.css.parser.CSSParseException;


/**
 * Part of a Selector
 *
 * @author tstgm
 */
abstract class Condition {

    abstract boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes);
    abstract void toCSS(StringBuilder sb);

    /**
     * the CSS condition [attribute]
     *
     * @param name PARAM
     * @return Returns
     */
    static Condition createAttributeExistsCondition(String namespaceURI, String name) {
        return new AttributeExistsCondition(namespaceURI, name);
    }

    /**
     * the CSS condition [attribute^=value]
     */
    static Condition createAttributePrefixCondition(String namespaceURI, String name, String value) {
        return new AttributePrefixCondition(namespaceURI, name, value);
    }
    
    /**
     * the CSS condition [attribute$=value]
     */
    static Condition createAttributeSuffixCondition(String namespaceURI, String name, String value) {
        return new AttributeSuffixCondition(namespaceURI, name, value);
    }
    
    /**
     * the CSS condition [attribute*=value]
     */
    static Condition createAttributeSubstringCondition(String namespaceURI, String name, String value) {
        return new AttributeSubstringCondition(namespaceURI, name, value);
    }
    
    /**
     * the CSS condition [attribute=value]
     */
    static Condition createAttributeEqualsCondition(String namespaceURI, String name, String value) {
        return new AttributeEqualsCondition(namespaceURI, name, value);
    }

    /**
     * the CSS condition [attribute~=value]
     *
     * @param name  PARAM
     * @param value PARAM
     * @return Returns
     */
    static Condition createAttributeMatchesListCondition(String namespaceURI, String name, String value) {
        return new AttributeMatchesListCondition(namespaceURI, name, value);
    }

    /**
     * the CSS condition [attribute|=value]
     *
     * @param name  PARAM
     * @param value PARAM
     * @return Returns
     */
    static Condition createAttributeMatchesFirstPartCondition(String namespaceURI, String name, String value) {
        return new AttributeMatchesFirstPartCondition(namespaceURI, name, value);
    }

    /**
     * the CSS condition .class
     *
     * @param className PARAM
     * @return Returns
     */
    static Condition createClassCondition(String className) {
        return new ClassCondition(className);
    }

    /**
     * the CSS condition #ID
     *
     * @param id PARAM
     * @return Returns
     */
    static Condition createIDCondition(String id) {
        return new IDCondition(id);
    }

    /**
     * the CSS condition lang(Xx)
     *
     * @param lang PARAM
     * @return Returns
     */
    static Condition createLangCondition(String lang) {
        return new LangCondition(lang);
    }

    /**
     * the CSS condition that element has pseudo-class :first-child
     *
     * @return Returns
     */
    static Condition createFirstChildCondition() {
        return new FirstChildCondition();
    }
    
    /**
     * the CSS condition that element has pseudo-class :last-child
     *
     * @return Returns
     */
    static Condition createLastChildCondition() {
        return new LastChildCondition();
    }
    
    /**
     * the CSS condition that element has pseudo-class :nth-child(an+b)
     *
     * @param number PARAM
     * @return Returns
     */
    static Condition createNthChildCondition(String number) {
        return NthChildCondition.fromString(number);
    }

    /**
     * the CSS condition that element has pseudo-class :even
     * 
     * @return Returns
     */
    static Condition createEvenChildCondition() {
        return new EvenChildCondition();
    }
    
    /**
     * the CSS condition that element has pseudo-class :odd
     * 
     * @return Returns
     */
    static Condition createOddChildCondition() {
        return new OddChildCondition();
    }

    /**
     * the CSS condition that element has pseudo-class :link
     *
     * @return Returns
     */
    static Condition createLinkCondition() {
        return new LinkCondition();
    }

    /**
     * for unsupported or invalid CSS
     *
     * @return Returns
     */
    static Condition createUnsupportedCondition() {
        return new UnsupportedCondition();
    }
    
    private static abstract class AttributeCompareCondition extends Condition {
        protected String _namespaceURI;
        protected String _name;
        protected String _value;
        
        protected abstract boolean compare(String attrValue, String conditionValue);

        AttributeCompareCondition(String namespaceURI, String name, String value) {
            _namespaceURI = namespaceURI;
            _name = name;
            _value = value;
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            if (attRes == null) {
                return false;
            }
            String val = attRes.getAttributeValue(e, _namespaceURI, _name);
            if (val == null) {
                return false;
            }
            
            return compare(val, _value);
        }

        protected void toCSS(StringBuilder sb, String type) {
            sb.append('[');
            sb.append(_name);
            sb.append(type);
            sb.append('=');
            sb.append('\"');
            sb.append(_value);
            sb.append('\"');
            sb.append(']');
        }
    }

    private static class AttributeExistsCondition extends AttributeCompareCondition {
        AttributeExistsCondition(String namespaceURI, String name) {
            super(namespaceURI, name, null);
        }
        
        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            if (attRes == null) {
                return false;
            }
            String val = attRes.getAttributeValue(e, _namespaceURI, _name);
            if (val == null) {
                return false;
            }
            
            return true;
        }
        
        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            throw new UnsupportedOperationException();
        }
        
        @Override
        void toCSS(StringBuilder sb) {
            sb.append('[');
            sb.append(_name);
            sb.append(']');
        }
    }
    
    private static class AttributeEqualsCondition extends AttributeCompareCondition {
        AttributeEqualsCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }

        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            return attrValue.equals(conditionValue);
        }

        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "");
        }
    }
    
    private static class AttributePrefixCondition extends AttributeCompareCondition {
        AttributePrefixCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }

        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            return attrValue.startsWith(conditionValue);
        }

        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "^");
        }
    }
    
    private static class AttributeSuffixCondition extends AttributeCompareCondition {
        AttributeSuffixCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }

        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            return attrValue.endsWith(conditionValue);
        }

        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "$");
        }
    }
    
    private static class AttributeSubstringCondition extends AttributeCompareCondition {
        AttributeSubstringCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }

        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            return attrValue.indexOf(conditionValue) > -1;
        }

        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "*");
        }
    }

    private static class AttributeMatchesListCondition extends AttributeCompareCondition {
        AttributeMatchesListCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }
        
        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            String[] ca = split(attrValue, ' ');
            boolean matched = false;
            for (int j = 0; j < ca.length; j++) {
                if (conditionValue.equals(ca[j])) {
                    matched = true;
                }
            }
            return matched;
        }

        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "~");
        }
    }

    private static class AttributeMatchesFirstPartCondition extends AttributeCompareCondition {
        AttributeMatchesFirstPartCondition(String namespaceURI, String name, String value) {
            super(namespaceURI, name, value);
        }
        
        @Override
        protected boolean compare(String attrValue, String conditionValue) {
            String[] ca = split(attrValue, '-');
            if (conditionValue.equals(ca[0])) {
                return true;
            }
            return false;
        }
        
        @Override
        void toCSS(StringBuilder sb) {
            toCSS(sb, "|");
        }
    }

    private static class ClassCondition extends Condition {

        private String _className;

        ClassCondition(String className) {
            _className = className;
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            if (attRes == null) {
                return false;
            }
            String c = attRes.getClass(e);
            if (c == null || c.length() < _className.length()) {
                return false;
            }

            // This is much faster than calling `split()` and comparing individual values in a loop.
            // NOTE: In jQuery, for example, the attribute value first has whitespace normalized to spaces. But
            // in an XML DOM, space normalization in attributes is supposed to have happened already.
            int idx = c.indexOf(_className);
            if (idx == -1){
                return false;
            }
            int beforeMatch = idx - 1;
            int afterMatch = idx + _className.length();
            boolean delimitedBefore = beforeMatch < 0 || Character.isWhitespace(c.charAt(beforeMatch));
            boolean delimitedAfter = afterMatch == c.length() || Character.isWhitespace(c.charAt(afterMatch));
            return delimitedBefore && delimitedAfter;
        }

        @Override
        public void toCSS(StringBuilder sb) {
            sb.append('.');
            sb.append(_className);
        }
    }

    private static class IDCondition extends Condition {

        private String _id;

        IDCondition(String id) {
            _id = id;
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            if (attRes == null) {
                return false;
            }
            if (!_id.equals(attRes.getID(e))) {
                return false;
            }
            return true;
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append('#');
            sb.append(_id);
        }
    }

    private static class LangCondition extends Condition {
        private String _lang;

        LangCondition(String lang) {
            _lang = lang;
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            if (attRes == null) {
                return false;
            }
            String lang = attRes.getLang(e);
            if (lang == null) {
                return false;
            }
            if(_lang.equalsIgnoreCase(lang)) {
                return true;
            }
            String[] ca = split(lang, '-');
            if (_lang.equalsIgnoreCase(ca[0])) {
                return true;
            }
            return false;
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":lang(");
            sb.append(_lang);
            sb.append(')');
        }
    }

    private static class FirstChildCondition extends Condition {

        FirstChildCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            return treeRes.isFirstChildElement(e);
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":first-child");
        }
    }
    
    private static class LastChildCondition extends Condition {

        LastChildCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            return treeRes.isLastChildElement(e);
        }
        
        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":last-child");
        }
    }

    private static class NthChildCondition extends Condition {

        private static final Pattern pattern = Pattern.compile("([-+]?)(\\d*)n(\\s*([-+])\\s*(\\d+))?");

        private final int a;
        private final int b;
        private final String input;

        NthChildCondition(int a, int b, String input) {
            this.a = a;
            this.b = b;
            this.input = input;
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            // getPositionOfElement() starts at 0, CSS spec starts at 1
            int position = treeRes.getPositionOfElement(e)+1;

            position -= b;

            if (a == 0) {
                return position == 0;
            } else if ((a < 0) && (position > 0)) {
                return false; // n is negative
            } else {
                return position % a == 0;
            }
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":nth-child(");
            sb.append(input);
            sb.append(')');
        }

        static NthChildCondition fromString(String number) {
            number = number.trim().toLowerCase();

            if ("even".equals(number)) {
                return new NthChildCondition(2, 0, number);
            } else if ("odd".equals(number)) {
                return new NthChildCondition(2, 1, number);
            } else {
                try {
                    return new NthChildCondition(0, Integer.parseInt(number), number);
                } catch (NumberFormatException e) {
                    Matcher m = pattern.matcher(number);

                    if (!m.matches()) {
                        throw new CSSParseException("Invalid nth-child selector: " + number, -1);
                    } else {
                        int a = m.group(2).equals("") ? 1 : Integer.parseInt(m.group(2));
                        int b = (m.group(5) == null) ? 0 : Integer.parseInt(m.group(5));
                        if ("-".equals(m.group(1))) {
                            a *= -1;
                        }
                        if ("-".equals(m.group(4))) {
                            b *= -1;
                        }

                        return new NthChildCondition(a, b, number);
                    }
                }
            }
        }
    }

    private static class EvenChildCondition extends Condition {

        EvenChildCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            int position = treeRes.getPositionOfElement(e);
            return position >= 0 && position % 2 == 0;
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":nth-child(even)");
        }
    }
    
    private static class OddChildCondition extends Condition {

        OddChildCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            int position = treeRes.getPositionOfElement(e);
            return position >= 0 && position % 2 == 1;
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":nth-child(odd)");
        }
    }

    private static class LinkCondition extends Condition {

        LinkCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            return attRes.isLink(e);
        }

        @Override
        void toCSS(StringBuilder sb) {
            sb.append(":link");
        }
    }

    /**
     * represents unsupported (or invalid) css, never matches
     */
    private static class UnsupportedCondition extends Condition {

        UnsupportedCondition() {
        }

        @Override
        boolean matches(Object e, AttributeResolver attRes, TreeResolver treeRes) {
            return false;
        }

        @Override
        void toCSS(StringBuilder sb) {
            // Nothing we can do...
        }
    }
    
    private static String[] split(String s, char ch) {
        if (s.indexOf(ch) == -1) {
            return new String[] { s };
        } else {
            List<String> result = new ArrayList<>();
            
            int last = 0;
            int next = 0;
            
            while ((next = s.indexOf(ch, last)) != -1) {
                if (next != last) {
                    result.add(s.substring(last, next));
                }
                last = next + 1;
            }
            
            if (last != s.length()) {
                result.add(s.substring(last));
            }
            
            return result.toArray(new String[result.size()]);
        }
    }    
}

