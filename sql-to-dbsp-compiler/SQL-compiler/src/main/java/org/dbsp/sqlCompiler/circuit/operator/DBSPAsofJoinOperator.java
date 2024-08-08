/*
 * Copyright 2022 VMware, Inc.
 * SPDX-License-Identifier: MIT
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package org.dbsp.sqlCompiler.circuit.operator;

import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.outer.CircuitVisitor;
import org.dbsp.sqlCompiler.ir.expression.DBSPClosureExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPComparatorExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.user.DBSPTypeZSet;

import javax.annotation.Nullable;
import java.util.List;

/** This operator is purely incremental, it does not have a non-incremental form */
public final class DBSPAsofJoinOperator extends DBSPBinaryOperator {
    public final DBSPComparatorExpression comparator;
    public final DBSPClosureExpression leftTimestamp;
    public final DBSPClosureExpression rightTimestamp;
    public final boolean isLeft;

    /**
     * Create an ASOF join operator
     *
     * @param node            Calcite node
     * @param outputType      Output type of operator
     * @param function        Closure from key, valueLeft, valueRight to result type
     * @param leftTimestamp   Function that extracts a "timestamp" from the left input
     * @param rightTimestamp  Function that extracts a "timestamp" from the right input
     * @param comparator      Function that compares two timestamps
     * @param isMultiset      True if output is a multiset
     * @param isLeft          True if this is a left join
     * @param left            Left input
     * @param right           Right input
     */
    public DBSPAsofJoinOperator(CalciteObject node, DBSPTypeZSet outputType,
                                DBSPExpression function,
                                DBSPClosureExpression leftTimestamp,
                                DBSPClosureExpression rightTimestamp,
                                DBSPComparatorExpression comparator,
                                boolean isMultiset, boolean isLeft,
                                DBSPOperator left, DBSPOperator right) {
        super(node, "asof_join", function, outputType, isMultiset, left, right);
        this.isLeft = isLeft;
        this.comparator = comparator;
        this.leftTimestamp = leftTimestamp;
        this.rightTimestamp = rightTimestamp;

        DBSPType elementResultType = this.getOutputZSetElementType();
        this.checkResultType(function, elementResultType);
    }

    @Override
    public void accept(CircuitVisitor visitor) {
        visitor.push(this);
        VisitDecision decision = visitor.preorder(this);
        if (!decision.stop())
            visitor.postorder(this);
        visitor.pop(this);
    }

    @Override
    public DBSPOperator withFunction(@Nullable DBSPExpression expression, DBSPType outputType) {
        throw new UnimplementedException(this.getNode());
    }

    @Override
    public DBSPOperator withInputs(List<DBSPOperator> newInputs, boolean force) {
        assert newInputs.size() == 2;
        if (force || this.inputsDiffer(newInputs))
            return new DBSPAsofJoinOperator(
                    this.getNode(), this.getOutputZSetType(),
                    this.getFunction(), this.leftTimestamp, this.rightTimestamp,
                    this.comparator, this.isMultiset, this.isLeft,
                    newInputs.get(0), newInputs.get(1))
                    .copyAnnotations(this);
        return this;
    }
}
