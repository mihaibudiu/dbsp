package org.dbsp.sqlCompiler.compiler.visitors.outer;

import org.dbsp.sqlCompiler.circuit.operator.DBSPAggregateOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPFlatMapOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPJoinFilterMapOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPMapIndexOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPMapOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPNoopOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPPartitionedRollingAggregateOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPPartitionedRollingAggregateWithWaterlineOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPStreamAggregateOperator;
import org.dbsp.sqlCompiler.compiler.IErrorReporter;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.ir.expression.DBSPApplyMethodExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPBinaryExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPBlockExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPFlatmap;
import org.dbsp.sqlCompiler.ir.expression.DBSPIfExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPOpcode;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPVariablePath;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPUSizeLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPVecLiteral;
import org.dbsp.sqlCompiler.ir.statement.DBSPLetStatement;
import org.dbsp.sqlCompiler.ir.statement.DBSPStatement;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeAny;
import org.dbsp.sqlCompiler.ir.type.derived.DBSPTypeRawTuple;
import org.dbsp.sqlCompiler.ir.type.derived.DBSPTypeTupleBase;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeUSize;
import org.dbsp.sqlCompiler.ir.type.user.DBSPTypeZSet;
import org.dbsp.util.Linq;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Lowers a circuit's representation; most changes are about
 * generating compilable Rust for operator functions. */
public class LowerCircuitVisitor extends CircuitCloneVisitor {
    public LowerCircuitVisitor(IErrorReporter reporter) {
        super(reporter, false);
    }

    /** Rewrite a flatmap operation into a Rust method call.
     * @param flatmap  Flatmap operation to rewrite. */
    public static DBSPExpression rewriteFlatmap(DBSPFlatmap flatmap) {
        //   move |x: &Tuple2<Vec<i32>, Option<i32>>, | -> _ {
        //     let x0: Vec<i32> = x.0.clone();
        //     let x1: x.1.clone();
        //     let array = (*x).0.clone();
        //     array.clone().into_iter().map({
        //        move |e: i32, | -> Tuple3<Vec<i32>, Option<i32>, i32> {
        //            Tuple3::new(x0.clone(), x1.clone(), e)
        //        }
        //     })
        DBSPVariablePath rowVar = new DBSPVariablePath(flatmap.inputElementType.ref());
        DBSPType eType = flatmap.collectionElementType;
        if (flatmap.collectionIndexType != null)
            eType = new DBSPTypeRawTuple(new DBSPTypeUSize(CalciteObject.EMPTY, false), eType);
        DBSPVariablePath elem = new DBSPVariablePath(eType);
        List<DBSPStatement> statements = new ArrayList<>();
        List<DBSPExpression> resultColumns = new ArrayList<>();
        for (int i = 0; i < flatmap.leftCollectionIndexes.size(); i++) {
            int index = flatmap.leftCollectionIndexes.get(i);
            // let x0: Vec<i32> = x.0.clone();
            // let x1: x.1.clone();
            DBSPExpression field = rowVar.deref().field(index).applyCloneIfNeeded();
            DBSPVariablePath fieldClone = new DBSPVariablePath(field.getType());
            DBSPLetStatement stat = new DBSPLetStatement(fieldClone.variable, field);
            statements.add(stat);
            resultColumns.add(fieldClone.applyCloneIfNeeded());
        }

        if (flatmap.emitIteratedElement) {
            if (flatmap.collectionIndexType != null) {
                // e.1, as produced by the iterator
                resultColumns.add(elem.field(1));
            } else {
                if (flatmap.rightProjections != null) {
                    for (DBSPClosureExpression clo: flatmap.rightProjections) {
                        resultColumns.add(clo.call(elem.borrow()).applyCloneIfNeeded());
                    }
                } else if (elem.getType().is(DBSPTypeTupleBase.class)) {
                        // Calcite's UNNEST has a strange semantics:
                        // If e is a tuple type, unpack its fields here
                        DBSPTypeTupleBase tuple = elem.getType().to(DBSPTypeTupleBase.class);
                        for (int ei = 0; ei < tuple.size(); ei++) {
                            resultColumns.add(elem.field(ei).applyCloneIfNeeded());
                        }
                } else {
                        // e
                        resultColumns.add(elem);
                }
            }
        }

        if (flatmap.collectionIndexType != null) {
            // The INDEX field produced WITH ORDINALITY
            Objects.requireNonNull(flatmap.collectionIndexType);
            resultColumns.add(new DBSPBinaryExpression(flatmap.getNode(),
                    new DBSPTypeUSize(CalciteObject.EMPTY, false), DBSPOpcode.ADD,
                    elem.field(0),
                    new DBSPUSizeLiteral(1)).cast(flatmap.collectionIndexType));
        }
        resultColumns = flatmap.shuffle.shuffle(resultColumns);

        // let array = if (*x).0.is_none() {
        //    vec!()
        // } else {
        //    (*x).0.clone().unwrap()
        // };
        // or
        // let array = (*x).0.clone();
        DBSPExpression extractArray = flatmap.collectionExpression.call(rowVar);
        DBSPLetStatement statement = new DBSPLetStatement("array", extractArray.borrow());
        statements.add(statement);
        DBSPType arrayType = extractArray.getType();

        DBSPExpression arrayExpression;
        if (arrayType.mayBeNull) {
            DBSPExpression condition = statement.getVarReference().deref().is_null();
            DBSPExpression empty = new DBSPVecLiteral(flatmap.getNode(), arrayType.setMayBeNull(false), Linq.list());
            DBSPExpression contents = statement.getVarReference().deref().applyClone().unwrap();
            arrayExpression = new DBSPIfExpression(flatmap.getNode(), condition, empty, contents);
        } else {
            arrayExpression = statement.getVarReference().deref().applyClone();
        }
        statement = new DBSPLetStatement("array_clone", arrayExpression);
        statements.add(statement);

        // move |e: i32, | -> Tuple3<Vec<i32>, Option<i32>, i32> {
        //   Tuple3::new(x0.clone(), x1.clone(), e)
        // }
        DBSPClosureExpression toTuple = new DBSPTupleExpression(resultColumns, false)
                .closure(elem);
        DBSPExpression iter = new DBSPApplyMethodExpression(flatmap.getNode(), "into_iter",
                DBSPTypeAny.getDefault(),
                statement.getVarReference().applyClone());
        if (flatmap.collectionIndexType != null) {
            iter = new DBSPApplyMethodExpression(flatmap.getNode(), "enumerate",
                    DBSPTypeAny.getDefault(), iter);
        }
        DBSPExpression function = new DBSPApplyMethodExpression(flatmap.getNode(),
                "map", DBSPTypeAny.getDefault(),
                iter, toTuple);
        DBSPExpression block = new DBSPBlockExpression(statements, function);
        return block.closure(rowVar);
    }

    @Override
    public void postorder(DBSPFlatMapOperator node) {
        DBSPOperator result;
        if (node.getFunction().is(DBSPFlatmap.class)) {
            List<DBSPOperator> sources = Linq.map(node.inputs, this::mapped);
            DBSPExpression function = rewriteFlatmap(node.getFunction().to(DBSPFlatmap.class));
            result = node.withFunction(function, node.outputType).withInputs(sources, this.force);
            this.map(node, result);
        } else {
            super.postorder(node);
        }
    }

    @Override
    public void postorder(DBSPStreamAggregateOperator node) {
        if (node.function != null) {
            // OrderBy
            super.postorder(node);
            return;
        }

        DBSPExpression function = node.getAggregate().asFold(this.errorReporter);
        DBSPOperator result = new DBSPStreamAggregateOperator(node.getNode(),node.getOutputIndexedZSetType(),
                function, null, this.mapped(node.input()));
        this.map(node, result);
    }

    @Override
    public void postorder(DBSPAggregateOperator node) {
        if (node.function != null) {
            // OrderBy
            super.postorder(node);
            return;
        }
        DBSPExpression function = node.getAggregate().asFold(this.errorReporter);
        DBSPOperator result = new DBSPAggregateOperator(
                node.getNode(), node.getOutputIndexedZSetType(),
                function, null, this.mapped(node.input()));
        this.map(node, result);
    }

    public static DBSPClosureExpression lowerJoinFilterMapFunctions(
            IErrorReporter errorReporter, DBSPJoinFilterMapOperator node) {
        if (node.filter == null)
            return node.getClosureFunction();
        if (node.map == null) {
            // Generate code of the form
            // let tmp = join(...);
            // if (filter(tmp)) {
            //    Some(tmp)
            // } else {
            //     None
            // }
            DBSPClosureExpression expression = node.getClosureFunction();
            DBSPClosureExpression filter = node.filter.to(DBSPClosureExpression.class);
            DBSPLetStatement let = new DBSPLetStatement("tmp", expression.body);
            DBSPExpression cond = filter.call(let.getVarReference().borrow()).reduce(errorReporter);
            DBSPExpression tmp = let.getVarReference();
            DBSPIfExpression ifexp = new DBSPIfExpression(
                    node.getNode(),
                    cond,
                    tmp.some(),
                    tmp.getType().setMayBeNull(true).none());
            DBSPBlockExpression block = new DBSPBlockExpression(Linq.list(let), ifexp);
            return block.closure(expression.parameters);
        } else {
            // Generate code of the form
            // if (filter(join(...)) {
            //    Some(map(join(...)))
            // } else {
            //    None
            // }
            DBSPClosureExpression expression = node.getClosureFunction();
            DBSPClosureExpression filter = node.filter.to(DBSPClosureExpression.class);
            DBSPExpression cond = filter
                    .call(expression.body.borrow())
                    .reduce(errorReporter);
            DBSPExpression map = node.map.to(DBSPClosureExpression.class)
                    .call(expression.body.borrow())
                    .reduce(errorReporter);
            DBSPIfExpression ifexp = new DBSPIfExpression(
                    node.getNode(),
                    cond,
                    map.some(),
                    map.getType().setMayBeNull(true).none());
            return ifexp.closure(expression.parameters);
        }
    }

    @Override
    public void postorder(DBSPJoinFilterMapOperator node) {
        if (node.filter == null) {
            // Already lowered
            super.postorder(node);
            return;
        }
        DBSPExpression newFunction = lowerJoinFilterMapFunctions(this.errorReporter, node);
        DBSPOperator result = new DBSPJoinFilterMapOperator(node.getNode(), node.getOutputZSetType(),
                newFunction, null, null, node.isMultiset,
                this.mapped(node.left()), this.mapped(node.right()))
                .copyAnnotations(node);
        this.map(node, result);
    }

    @Override
    public void postorder(DBSPNoopOperator node) {
        DBSPOperator replacement;
        if (node.outputType.is(DBSPTypeZSet.class)) {
            replacement = new DBSPMapOperator(node.getNode(), node.getFunction(),
                    node.getOutputZSetType(), this.mapped(node.input()));
        } else {
            replacement = new DBSPMapIndexOperator(node.getNode(), node.getFunction(),
                    node.getOutputIndexedZSetType(), this.mapped(node.input()));
        }
        this.map(node, replacement);
    }

    @Override
    public void postorder(DBSPPartitionedRollingAggregateOperator node) {
        if (node.aggregate == null) {
            super.postorder(node);
            return;
        }
        DBSPExpression function = node.getAggregate().asFold(this.errorReporter);
        DBSPOperator result = new DBSPPartitionedRollingAggregateOperator(node.getNode(),
                node.partitioningFunction, function, null, node.lower, node.upper,
                node.getOutputIndexedZSetType(), this.mapped(node.input()));
        this.map(node, result);
    }

    @Override
    public void postorder(DBSPPartitionedRollingAggregateWithWaterlineOperator node) {
        if (node.aggregate == null) {
            super.postorder(node);
            return;
        }
        DBSPExpression function = node.aggregate.asFold(this.errorReporter);
        DBSPOperator result = new DBSPPartitionedRollingAggregateWithWaterlineOperator(node.getNode(),
                node.partitioningFunction, function, null, node.lower, node.upper,
                node.getOutputIndexedZSetType(),
                this.mapped(node.left()), this.mapped(node.right()));
        this.map(node, result);
    }
}