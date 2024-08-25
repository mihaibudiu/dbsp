package org.dbsp.sqlCompiler.ir;

public interface IsIntervalLiteral extends IsNumericLiteral {
    IsIntervalLiteral multiply(long value);
}
