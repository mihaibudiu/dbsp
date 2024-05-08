package org.dbsp.sqlCompiler.compiler.backend.rust;

import org.dbsp.sqlCompiler.circuit.operator.DBSPAggregateOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPStreamAggregateOperator;
import org.dbsp.sqlCompiler.compiler.CompilerOptions;
import org.dbsp.sqlCompiler.compiler.IErrorReporter;
import org.dbsp.sqlCompiler.compiler.ProgramMetadata;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.EliminateStructs;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeStream;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeStruct;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeTuple;
import org.dbsp.sqlCompiler.ir.type.DBSPTypeVec;
import org.dbsp.util.IndentStream;

/** An extension of ToRustVisitor which generates code using SqlValue
 * and SqlTuple. */
public class ToRustDynamicVisitor extends ToRustVisitor {
    public ToRustDynamicVisitor(IErrorReporter reporter, IndentStream builder, CompilerOptions options, ProgramMetadata metadata) {
        super(reporter, builder, options, metadata);
    }

    @Override
    ToRustInnerVisitor createInnerVisitor(IndentStream builder) {
        return new ToRustInnerDynamicVisitor(this.errorReporter, builder, options, false);
    }

    @Override
    protected void generateFromTrait(DBSPTypeStruct type) {
        EliminateStructs es = new EliminateStructs(this.errorReporter);
        DBSPTypeTuple tuple = es.apply(type).to(DBSPTypeTuple.class);
        this.builder.append("impl From<")
                .append(type.sanitizedName)
                .append("> for ");
        tuple.accept(this.innerVisitor);
        this.builder.append(" {")
                .increase()
                .append("fn from(table: ")
                .append(type.sanitizedName)
                .append(") -> Self");
        this.builder.append(" {")
                .increase()
                .append("SqlTuple")
                .append("::from")
                .append(type.fields.size())
                .append("(");
        for (DBSPTypeStruct.Field field: type.fields.values()) {
            this.builder.append("table.")
                    .append(field.getSanitizedName());
            if (field.type.is(DBSPTypeVec.class)) {
                this.builder.append(".into_iter().map(|y| y.into()).collect()");
            } else {
                this.builder.append(".into()");
            }
            this.builder.append(", ");
        }
        this.builder.append(")").newline();
        this.builder.decrease()
                .append("}")
                .newline()
                .decrease()
                .append("}")
                .newline();

        this.builder.append("impl From<");
        tuple.accept(this.innerVisitor);
        this.builder.append("> for ")
                .append(type.sanitizedName);
        this.builder.append(" {")
                .increase()
                .append("fn from(tuple: ");
        tuple.accept(this.innerVisitor);
        this.builder.append(") -> Self");
        this.builder.append(" {")
                .increase()
                .append("Self {")
                .increase();
        int index = 0;
        for (DBSPTypeStruct.Field field: type.fields.values()) {
            this.builder
                    .append(field.getSanitizedName())
                    .append(": tuple[")
                    .append(index++)
                    .append("].clone()");
            if (field.type.is(DBSPTypeVec.class)) {
                this.builder.append(".into_iter().map(|y| y.into()).collect()");
            } else {
                this.builder.append(".into()");
            }
            this.builder.append(", ")
                    .newline();
        }
        this.builder.decrease().append("}").newline();
        this.builder.decrease()
                .append("}")
                .newline()
                .decrease()
                .append("}")
                .newline();
    }

    @Override
    public VisitDecision preorder(DBSPAggregateOperator operator) {
        if (operator.isLinear) {
            this.innerVisitor.to(ToRustInnerDynamicVisitor.class).setInsideLinearAggregation(true);
        }
        DBSPType streamType = new DBSPTypeStream(operator.outputType);
        this.writeComments(operator)
                .append("let ")
                .append(operator.getOutputName())
                .append(": ");
        streamType.accept(this.innerVisitor);
        this.builder.append(" = ");
        builder.append(operator.input().getOutputName())
                .append(".");
        builder.append(operator.operation)
                .append("(");
        operator.getFunction().accept(this.innerVisitor);
        builder.append(");");
        if (operator.isLinear) {
            this.innerVisitor.to(ToRustInnerDynamicVisitor.class).setInsideLinearAggregation(false);
        }
        return VisitDecision.STOP;
    }

    @Override
    public VisitDecision preorder(DBSPStreamAggregateOperator operator) {
        if (operator.isLinear) {
            this.innerVisitor.to(ToRustInnerDynamicVisitor.class).setInsideLinearAggregation(true);
        }
        super.preorder(operator);
        if (operator.isLinear) {
            this.innerVisitor.to(ToRustInnerDynamicVisitor.class).setInsideLinearAggregation(false);
        }
        return VisitDecision.STOP;
    }
}
