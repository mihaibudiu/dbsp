package org.dbsp.sqlCompiler.compiler.visitors.inner.unusedFields;

import org.apache.calcite.util.Pair;
import org.dbsp.sqlCompiler.circuit.OutputPort;
import org.dbsp.sqlCompiler.circuit.operator.DBSPMapOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPSimpleOperator;
import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.visitors.outer.CircuitCloneVisitor;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.type.user.DBSPTypeZSet;

/** Discover unused fields in some operators and remove them */
public class UnusedFields extends CircuitCloneVisitor {
    public final FindUnusedFields find;

    public UnusedFields(DBSPCompiler compiler) {
        super(compiler, false);
        this.find = new FindUnusedFields(compiler);
    }

    @Override
    public void postorder(DBSPMapOperator operator) {
        if (!operator.outputType().is(DBSPTypeZSet.class) ||
            !operator.input().outputType().is(DBSPTypeZSet.class)) {
            super.postorder(operator);
            return;
        }

        DBSPClosureExpression closure = operator.getClosureFunction();
        assert closure.parameters.length == 1;

        this.find.apply(closure);
        if (!this.find.foundUnusedFields()) {
            super.postorder(operator);
            return;
        }

        Pair<ParameterFieldRemap, RewriteFields> pair = this.find.getFieldRemap();
        FieldMap fm = pair.left.get(closure.parameters[0]);
        assert fm != null;

        OutputPort source = this.mapped(operator.input());
        DBSPClosureExpression projection = fm.getProjection();
        DBSPMapOperator adjust = new DBSPMapOperator(operator.getNode(), projection,
                new DBSPTypeZSet(projection.getResultType()), source);
        this.addOperator(adjust);

        DBSPClosureExpression compressed = pair.right.apply(closure).to(DBSPClosureExpression.class);
        DBSPSimpleOperator result = new DBSPMapOperator(
                operator.getNode(), compressed, operator.getOutputZSetType(), adjust.outputPort());
        this.map(operator, result);
    }
}
