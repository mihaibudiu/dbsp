package org.dbsp.sqlCompiler.compiler.visitors.outer;

import org.dbsp.sqlCompiler.compiler.errors.InternalCompilerError;
import org.dbsp.sqlCompiler.compiler.frontend.ExpressionCompiler;
import org.dbsp.sqlCompiler.ir.DBSPParameter;
import org.dbsp.sqlCompiler.ir.expression.DBSPBinaryExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPDerefExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPFieldExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPOpcode;
import org.dbsp.sqlCompiler.ir.expression.DBSPUnaryExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPLiteral;
import org.dbsp.util.IIndentStream;
import org.dbsp.util.Linq;
import org.dbsp.util.ToIndentableString;

import java.util.ArrayList;
import java.util.List;

/** Helper class for analyzing filter functions.
 * This extracts all comparisons of the form t.column CMP expression
 * from a list of conjunctions. */
class ComparisonsAnalyzer {
    /** A comparison between an output column and an expression which
     * may be monotone */
    static class Comparison implements ToIndentableString {
        /** Output column involved in comparison */
        public final int columnIndex;
        /** Expression that is compared with column; column >= expression */
        public final DBSPExpression comparedTo;
        /** Parameter that represents the row.  Only used for debugging. */
        final DBSPParameter parameter;
        /** Comparison operator */
        final DBSPOpcode opcode;

        Comparison(int columnIndex, DBSPExpression comparedTo, DBSPOpcode opcode, DBSPParameter parameter) {
            assert columnIndex >= 0;
            this.opcode = opcode;
            this.columnIndex = columnIndex;
            this.comparedTo = comparedTo;
            this.parameter = parameter;
        }

        @Override
        public IIndentStream toString(IIndentStream builder) {
            return builder
                    .append(this.parameter.name)
                    .append(this.columnIndex)
                    .append(this.opcode.toString())
                    .append(this.comparedTo);
        }

        @Override
        public String toString() {
            return this.parameter.name + "." + this.columnIndex +
                    " " + this.opcode + " " + this.comparedTo;
        }
    }

    public final List<Comparison> comparisons = new ArrayList<>();
    /** If true only accept comparisons of the form t.col >= value */
    public final boolean onlyGe;

    /** Analyze the condition of a filter and decompose it into a conjunction of comparisons */
    public ComparisonsAnalyzer(DBSPExpression closure, boolean onlyGe) {
        DBSPClosureExpression clo = closure.to(DBSPClosureExpression.class);
        assert clo.parameters.length == 1;
        DBSPParameter param = clo.parameters[0];
        DBSPExpression expression = clo.body;
        if (expression.is(DBSPUnaryExpression.class)) {
            DBSPUnaryExpression unary = expression.to(DBSPUnaryExpression.class);
            // If the filter is wrap_bool(expression), analyze expression
            if (unary.operation == DBSPOpcode.WRAP_BOOL)
                expression = unary.source;
        }
        this.onlyGe = onlyGe;
        this.complete = this.analyzeConjunction(expression, param);
    }

    public boolean isEmpty() {
        return this.comparisons.isEmpty();
    }

    @Override
    public String toString() {
        return this.comparisons.toString();
    }

    /** Check if `expression` is a reference to a column (field) of a given `parameter`.
     * Return the column index if it is, or -1 otherwise. */
    public static int isColumn(DBSPExpression expression, DBSPParameter param) {
        DBSPFieldExpression field = expression.as(DBSPFieldExpression.class);
        if (field == null)
            return -1;
        DBSPDerefExpression deref = field.expression.as(DBSPDerefExpression.class);
        if (deref == null)
            return -1;
        DBSPVariablePath var = deref.expression.as(DBSPVariablePath.class);
        if (var == null)
            return -1;
        if (var.variable.equals(param.name))
            return field.fieldNo;
        return -1;
    }

    /** If `larger` is a column reference, create a new comparison and add it to the list
     *
     * @return True if the expression is a comparison that has been added. */
    boolean addIfRightIsColumn(DBSPExpression smaller, DBSPExpression larger, DBSPOpcode opcode, DBSPParameter param) {
        int index = isColumn(larger, param);
        if (index < 0)
            return false;
        Comparison comp = new Comparison(index, smaller, opcode, param);
        this.comparisons.add(comp);
        return true;
    }

    static DBSPOpcode inverse(DBSPOpcode opcode) {
        return switch (opcode) {
            case ADD -> DBSPOpcode.SUB;
            case SUB -> DBSPOpcode.ADD;
            case LT -> DBSPOpcode.GTE;
            case GTE -> DBSPOpcode.LT;
            case LTE -> DBSPOpcode.GT;
            case GT -> DBSPOpcode.LTE;
            default -> throw new InternalCompilerError(opcode.toString());
        };
    }

    /** If 'larger' is an expression that adds or subtracts a constant from a column, create a
     * new comparison and add it to the list.
     *
     * @return True if the expression is a comparison that has been added. */
    boolean addIfOffsetOfColumn(DBSPExpression smaller, DBSPExpression larger, DBSPOpcode opcode, DBSPParameter param) {
        DBSPBinaryExpression binary = larger.as(DBSPBinaryExpression.class);
        if (binary == null)
            return false;
        if (binary.operation != DBSPOpcode.ADD && binary.operation != DBSPOpcode.SUB)
            return false;
        DBSPOpcode inverse = inverse(binary.operation);
        int column = isColumn(binary.left, param);
        if (column >= 0 && binary.right.is(DBSPLiteral.class)) {
            // col + constant, col - constant
            DBSPExpression newSmaller =
                    ExpressionCompiler.makeBinaryExpression(larger.getNode(), larger.getType(),
                            inverse, smaller, binary.right);
            Comparison comp = new Comparison(column, newSmaller, opcode, param);
            this.comparisons.add(comp);
            return true;
        }

        if (binary.operation != DBSPOpcode.ADD)
            return false;

        // constant + col
        column = isColumn(binary.right, param);
        if (column >= 0 && binary.left.is(DBSPLiteral.class)) {
            DBSPExpression newSmaller =
                    ExpressionCompiler.makeBinaryExpression(larger.getNode(), larger.getType(),
                            inverse, smaller, binary.left);
            Comparison comp = new Comparison(column, newSmaller, opcode, param);
            this.comparisons.add(comp);
            return true;
        }

        return false;
    }

    /** Check if `expression` is a comparison and if so add it to the list.
     * Return true if added. */
    boolean findComparison(DBSPExpression expression, DBSPParameter param) {
        DBSPBinaryExpression binary = expression.as(DBSPBinaryExpression.class);
        if (binary == null)
            return false;
        switch (binary.operation) {
            case LTE:
            case LT: {
                boolean added = this.addIfRightIsColumn(binary.left, binary.right, inverse(binary.operation), param);
                if (added)
                    return true;
                added = this.addIfOffsetOfColumn(binary.left, binary.right, inverse(binary.operation), param);
                if (added)
                    return true;
                if (!this.onlyGe)
                    return false;
                added = this.addIfRightIsColumn(binary.right, binary.left, binary.operation, param);
                if (added)
                    return true;
                return this.addIfOffsetOfColumn(binary.right, binary.left, binary.operation, param);
            }
            case GTE:
            case GT: {
                boolean added = this.addIfRightIsColumn(binary.right, binary.left, binary.operation, param);
                if (added)
                    return true;
                added = this.addIfOffsetOfColumn(binary.right, binary.left, binary.operation, param);
                if (added)
                    return true;
                if (this.onlyGe)
                    return false;
                added = this.addIfRightIsColumn(binary.left, binary.right, inverse(binary.operation), param);
                if (added)
                    return true;
                return this.addIfOffsetOfColumn(binary.left, binary.right, inverse(binary.operation), param);
            }
            case EQ: {
                // add both ways
                boolean added = this.addIfRightIsColumn(binary.left, binary.right, binary.operation, param);
                added = added || this.addIfRightIsColumn(binary.right, binary.left, binary.operation, param);
                if (added)
                    return true;
                added = this.addIfOffsetOfColumn(binary.left, binary.right, binary.operation, param);
                if (added)
                    return true;
                return this.addIfOffsetOfColumn(binary.right, binary.left, binary.operation, param);
            }
            default:
                return false;
        }
    }

    boolean analyzeConjunction(DBSPExpression expression, DBSPParameter param) {
        DBSPBinaryExpression binary = expression.as(DBSPBinaryExpression.class);
        if (binary == null)
            return false;
        if (binary.operation == DBSPOpcode.AND) {
            boolean foundLeft = this.analyzeConjunction(binary.left, param);
            boolean foundRight = this.analyzeConjunction(binary.right, param);
            return foundLeft && foundRight;
        } else {
            return this.findComparison(binary, param);
        }
    }

    /** True is the entire expression is composed of legal comparisons */
    final boolean complete;

    /** Get all the expressions that are below the specified output column */
    List<DBSPExpression> getLowerBounds(int columnIndex) {
        return Linq.map(
                Linq.where(this.comparisons, c -> c.columnIndex == columnIndex),
                c -> c.comparedTo);
    }
}
