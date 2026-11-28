package com.openhtmltopdf.extend;

public enum StructureType {
    LAYER,
    RUNNING,
    BACKGROUND,
    TEXT,
    FLOAT,
    BLOCK,
    INLINE,
    INLINE_CHILD_BOX,
    LIST_MARKER,
    REPLACED,
    /**
     * The shadows of text (text-shadow), painted before the text: a copy of it that is
     * not content.
     */
    TEXT_SHADOW;
}
