package org.dbsp.sqlCompiler.ir.aggregate;

import org.dbsp.sqlCompiler.compiler.errors.InternalCompilerError;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.EquivalenceContext;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.DBSPNode;
import org.dbsp.sqlCompiler.ir.IDBSPInnerNode;
import org.dbsp.sqlCompiler.ir.IDBSPNode;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.util.IIndentStream;

/**
 * A linear aggregate is compiled as two functions:
 * - a function from the row to a value of a group
 * - a postprocessing function that also takes as an argument the number of elements in the group
 * The emptySetResult is a constant containing the result
 * returned by a top-level aggregate (no group-by) for an empty set.
 */
public class LinearAggregate extends AggregateBase {
    /** A closure with signature |value| -> groupElement */
    public final DBSPClosureExpression map;
    /** Function that post-processes a count and the actual result to produce the final result. */
    public final DBSPClosureExpression postProcess;
    /** Result produced for an empty set */
    public final DBSPExpression emptySetResult;

    public LinearAggregate(
            CalciteObject origin,
            DBSPClosureExpression map,
            DBSPClosureExpression postProcess,
            DBSPExpression emptySetResult) {
        super(origin, emptySetResult.getType());
        this.map = map;
        this.postProcess = postProcess;
        this.emptySetResult = emptySetResult;
    }

    public void validate() {
        // These validation rules actually don't apply for window-based aggregates.
        DBSPType emptyResultType = this.emptySetResult.getType();
        DBSPType postProcessType = this.postProcess.getResultType();
        if (!emptyResultType.sameType(postProcessType)) {
            throw new InternalCompilerError("Post-process result type " + postProcessType +
                    " different from empty set type " + emptyResultType, this);
        }
    }

    /** Result produced for an empty set */
    public DBSPExpression getEmptySetResult() {
        return this.emptySetResult;
    }

    @Override
    public boolean isLinear() {
        return true;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        VisitDecision decision = visitor.preorder(this);
        if (decision.stop()) return;
        visitor.push(this);
        this.map.accept(visitor);
        this.postProcess.accept(visitor);
        this.emptySetResult.accept(visitor);
        visitor.pop(this);
        visitor.postorder(this);
    }

    public DBSPClosureExpression getPostprocessing() {
        return this.postProcess;
    }

    @Override
    public boolean sameFields(IDBSPNode other) {
        LinearAggregate o = other.as(LinearAggregate.class);
        if (o == null)
            return false;
        return this.map == o.map &&
                this.postProcess == o.postProcess &&
                this.emptySetResult == o.emptySetResult;
    }

    @Override
    public IIndentStream toString(IIndentStream builder) {
        builder.append("[").increase();
        builder.append("increment=")
                .append(this.map)
                .newline()
                .append("postProcess=")
                .append(this.postProcess)
                .newline()
                .append("emptySetResult=")
                .append(this.emptySetResult)
                .newline();
        builder.newline().decrease().append("]");
        return builder;
    }

    public boolean equivalent(EquivalenceContext context, LinearAggregate other) {
        return context.equivalent(this.map, other.map) &&
                context.equivalent(this.postProcess, other.postProcess) &&
                context.equivalent(this.emptySetResult, other.emptySetResult);
    }

    @Override
    public DBSPExpression deepCopy() {
        return new LinearAggregate(
                this.getNode(),
                this.map.deepCopy().to(DBSPClosureExpression.class),
                this.postProcess.deepCopy().to(DBSPClosureExpression.class),
                this.emptySetResult.deepCopy());
    }

    @Override
    public boolean equivalent(EquivalenceContext context, DBSPExpression other) {
        return false;
    }
}
