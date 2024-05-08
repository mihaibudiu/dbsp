package org.dbsp.sqlCompiler.compiler.backend.rust;

import org.dbsp.sqlCompiler.compiler.CompilerOptions;
import org.dbsp.sqlCompiler.compiler.IErrorReporter;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.ir.expression.DBSPCloneExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPFieldExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeCode;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeSemigroup;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTuple;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTupleBase;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeUser;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBaseType;
import org.dbsp.util.IndentStream;

/** Similar to ToRustInnerVisitor, but generate code using SqlValue
 * and SqlTuple instead of scalar values and Tup. */
public class ToRustInnerDynamicVisitor extends ToRustInnerVisitor {
    static final String tupleName = "SqlTuple";
    // Flag to generate code differently inside linear aggregations
    boolean insideLinearAggregation;

    public ToRustInnerDynamicVisitor(IErrorReporter reporter, IndentStream builder, CompilerOptions options, boolean compact) {
        super(reporter, builder, options, compact);
        this.insideLinearAggregation = false;
    }

    public void setInsideLinearAggregation(boolean inside) {
        this.insideLinearAggregation = inside;
    }

    @Override
    public VisitDecision preorder(DBSPTypeTuple type) {
        if (type.mayBeNull)
            this.builder.append("Option<");
        if (this.insideLinearAggregation)
            this.builder.append("Monoid");
        this.builder.append(tupleName);
        if (type.mayBeNull)
            this.builder.append(">");
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPTupleExpression expression) {
        if (expression.isNull)
            return this.doNullExpression(expression);
        boolean newlines = this.compact && expression.fields.length > 2;
        this.builder.append(tupleName)
                .append("::from")
                .append(expression.size())
                .append("(");
        if (newlines)
            this.builder.increase();
        boolean first = true;
        for (DBSPExpression field : expression.fields) {
            if (!first) {
                this.builder.append(", ");
                if (newlines)
                    this.builder.newline();
            }
            first = false;
            this.builder.append("SqlValue::from(");
            field.accept(this);
            this.builder.append(")");
        }
        if (newlines)
            this.builder.decrease();
        this.builder.append(")");
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPCloneExpression expression) {
        expression.expression.accept(this);
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPTypeUser type) {
        if (type.code != DBSPTypeCode.SEMIGROUP)
            return super.preorder(type);
        this.builder.append(type.name);
        if (type.typeArgs.length > 0) {
            this.builder.append("<");
            boolean first = true;
            for (DBSPType fType: type.typeArgs) {
                if (!first)
                    this.builder.append(", ");
                first = false;
                if (fType.is(DBSPTypeBaseType.class))
                    this.builder.append("SqlValue");
                else
                    fType.accept(this);
            }
            this.builder.append(">");
        }
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPTypeSemigroup type) {
        this.builder.append(type.name);
        int args = type.typeArgs.length;
        this.builder.append("<");
        // Skip first half of the type arguments
        boolean first = true;
        for (int i = args / 2; i < args; i++) {
            if (!first)
                this.builder.append(", ");
            first = false;
            DBSPType fType = type.typeArgs[i];
            fType.accept(this);
        }
        this.builder.append(">");
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPFieldExpression expression) {
        DBSPType sourceType = expression.expression.getType();
        DBSPType fieldType = expression.getType();
        DBSPTypeTupleBase tuple = sourceType.as(DBSPTypeTupleBase.class);
        if (tuple == null || tuple.isRaw()) {
            // tuple can be null if type is Any.
            return super.preorder(expression);
        }
        if (fieldType.is(DBSPTypeBaseType.class)) {
            this.builder.append("<");
            fieldType.accept(this);
            this.builder.append(">::from(");
        } else if (fieldType.is(DBSPTypeTuple.class)) {
            this.builder.append("<SqlTuple>::from(");
        }
        expression.expression.accept(this);
        if (sourceType.mayBeNull) {
            // TODO: this should be done differently
            if (!sourceType.hasCopy() &&
                    !expression.expression.is(DBSPCloneExpression.class))
                this.builder.append(".clone()");
            this.builder.append(".unwrap()");
        }
        this.builder.append("[")
                .append(expression.fieldNo)
                .append("].clone()");
        if (fieldType.is(DBSPTypeBaseType.class) || fieldType.is(DBSPTypeTuple.class)) {
            this.builder.append(")");
        }
        return VisitDecision.STOP;
    }
}
