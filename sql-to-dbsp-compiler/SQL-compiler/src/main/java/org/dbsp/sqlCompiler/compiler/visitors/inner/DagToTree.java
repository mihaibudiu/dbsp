package org.dbsp.sqlCompiler.compiler.visitors.inner;

import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.ir.IDBSPInnerNode;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPLiteral;

import javax.annotation.Nullable;

/** Convert an Expression DAG into an Expression tree */
public class DagToTree extends InnerRewriteVisitor {
    final RepeatedExpressions repeated;
    boolean duplicate;

    public DagToTree(DBSPCompiler compiler) {
        super(compiler, true);
        this.repeated = new RepeatedExpressions(compiler, true, false);
    }

    VisitDecision copy(DBSPExpression expression) {
        if (this.duplicate)
            this.map(expression, expression.deepCopy());
        else
            this.map(expression, expression);
        return VisitDecision.STOP;
    }

    @Nullable
    IDBSPInnerNode processing;

    @Override
    public void startVisit(IDBSPInnerNode node) {
        super.startVisit(node);
        this.processing = node;
        this.repeated.apply(node);
        this.duplicate = this.repeated.hasDuplicate();
    }

    @Override
    public void endVisit() {
        assert this.processing != null;
        if (this.processing.is(DBSPExpression.class)) {
            DBSPExpression result = this.getResultExpression();
            this.repeated.apply(result);
            assert !this.repeated.hasDuplicate();
        }
        this.processing = null;
    }

    @Override
    public VisitDecision preorder(DBSPLiteral expression) {
        return this.copy(expression);
    }

    @Override
    public VisitDecision preorder(DBSPVariablePath expression) {
        return this.copy(expression);
    }
}
