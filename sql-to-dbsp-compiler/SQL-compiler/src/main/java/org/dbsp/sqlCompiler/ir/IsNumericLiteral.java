package org.dbsp.sqlCompiler.ir;

public interface IsNumericLiteral {
    /** True if the literal is positive */
    boolean gt0();
}
