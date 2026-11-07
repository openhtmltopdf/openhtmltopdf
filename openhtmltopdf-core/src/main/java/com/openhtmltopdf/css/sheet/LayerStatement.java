package com.openhtmltopdf.css.sheet;

import java.util.Collections;
import java.util.List;

/**
 * Cascade layers named at this point of a style sheet, by an <code>@layer a, b;</code>
 * statement or by the start of an <code>@layer</code> block. Layers are ordered by
 * where they are first named (CSS Cascade 5, 6.4.3), so the statement is kept in the
 * style sheet's contents in encounter order.
 */
public class LayerStatement {
    private final List<String> _names;

    /**
     * @param names the full names of the layers, such as <code>base.reset</code>
     */
    public LayerStatement(List<String> names) {
        _names = Collections.unmodifiableList(names);
    }

    public List<String> getNames() {
        return _names;
    }
}
