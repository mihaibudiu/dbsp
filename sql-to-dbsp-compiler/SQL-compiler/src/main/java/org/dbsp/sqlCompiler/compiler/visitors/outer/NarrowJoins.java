package org.dbsp.sqlCompiler.compiler.visitors.outer;

import org.dbsp.sqlCompiler.circuit.OutputPort;
import org.dbsp.sqlCompiler.circuit.operator.DBSPJoinBaseOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPJoinIndexOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPJoinOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPMapIndexOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPSimpleOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPStreamJoinIndexOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPStreamJoinOperator;
import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.frontend.TypeCompiler;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.inner.unusedFields.FieldMap;
import org.dbsp.sqlCompiler.compiler.visitors.inner.unusedFields.FindUnusedFields;
import org.dbsp.sqlCompiler.compiler.visitors.inner.unusedFields.ParameterFieldRemap;
import org.dbsp.sqlCompiler.compiler.visitors.inner.unusedFields.RewriteFields;
import org.dbsp.sqlCompiler.ir.DBSPParameter;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPRawTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.derived.DBSPTypeRawTuple;
import org.dbsp.sqlCompiler.ir.type.user.DBSPTypeIndexedZSet;
import org.dbsp.util.Linq;

import java.util.List;
import java.util.Objects;

/** Find and remove unused fields in Join operators. */
public class NarrowJoins extends Repeat {
    public NarrowJoins(DBSPCompiler compiler) {
        super(compiler, new OnePass(compiler));
    }

    static class OnePass extends Passes {
        OnePass(DBSPCompiler compiler) {
            super("NarrowJoins", compiler);
            // Moves projections from joins to their inputs
            this.add(new RemoveJoinFields(compiler));
            this.add(new DeadCode(compiler, true, false));
            // Merges projections into other joins if possible
            this.add(new OptimizeWithGraph(compiler, g -> new OptimizeMaps(compiler, false, g)));
        }
    }

    static class RemoveJoinFields extends CircuitCloneVisitor {
        RemoveJoinFields(DBSPCompiler compiler) {
            super(compiler, false);
        }

        DBSPMapIndexOperator getProjection(CalciteObject node, FieldMap fieldMap, OutputPort input) {
            DBSPType inputType = input.getOutputIndexedZSetType().getKVRefType();
            DBSPVariablePath var = inputType.var();
            List<DBSPExpression> resultFields = Linq.map(fieldMap.getUsedFields(),
                    f -> var.field(1).deref().field(f).applyCloneIfNeeded());
            DBSPRawTupleExpression raw = new DBSPRawTupleExpression(
                    DBSPTupleExpression.flatten(var.field(0).deref()),
                    new DBSPTupleExpression(resultFields, false));
            DBSPClosureExpression projection = raw.closure(var);

            OutputPort source = this.mapped(input);
            DBSPTypeIndexedZSet ix = TypeCompiler.makeIndexedZSet(projection.getResultType().to(DBSPTypeRawTuple.class));
            DBSPMapIndexOperator map = new DBSPMapIndexOperator(node, projection, ix, source);
            this.addOperator(map);
            return map;
        }

        boolean processJoin(DBSPJoinBaseOperator join) {
            FindUnusedFields fu = new FindUnusedFields(this.compiler);
            DBSPClosureExpression joinFunction = join.getClosureFunction();
            fu.apply(joinFunction);

            assert joinFunction.parameters.length == 3;
            DBSPParameter left = joinFunction.parameters[1];
            DBSPParameter right = joinFunction.parameters[2];

            var pair = fu.getFieldRemap();
            ParameterFieldRemap remap = pair.left;
            FieldMap leftRemap = Objects.requireNonNull(remap.get(left));
            FieldMap rightRemap = Objects.requireNonNull(remap.get(right));
            if (!leftRemap.hasUnusedFields() && !rightRemap.hasUnusedFields())
                return false;

            DBSPSimpleOperator leftMap = getProjection(join.getNode(), leftRemap, join.left());
            DBSPSimpleOperator rightMap = getProjection(join.getNode(), rightRemap, join.right());

            RewriteFields rw = pair.right;
            // Parameter 0 is not used in the body of the function, leave it unchanged
            rw.changeToIdentity(joinFunction.parameters[0]);
            DBSPExpression newJoinFunction = rw.apply(joinFunction).to(DBSPExpression.class);
            DBSPSimpleOperator replacement = join.withFunction(newJoinFunction, join.outputType)
                    .withInputs(Linq.list(leftMap.outputPort(), rightMap.outputPort()), true);
            this.map(join, replacement);
            return true;
        }

        @Override
        public void postorder(DBSPJoinIndexOperator join) {
            boolean done = this.processJoin(join);
            if (!done)
                super.postorder(join);
        }

        @Override
        public void postorder(DBSPStreamJoinIndexOperator join) {
            boolean done = this.processJoin(join);
            if (!done)
                super.postorder(join);
        }

        @Override
        public void postorder(DBSPStreamJoinOperator join) {
            boolean done = this.processJoin(join);
            if (!done)
                super.postorder(join);
        }

        @Override
        public void postorder(DBSPJoinOperator join) {
            boolean done = this.processJoin(join);
            if (!done)
                super.postorder(join);
        }
    }
}
