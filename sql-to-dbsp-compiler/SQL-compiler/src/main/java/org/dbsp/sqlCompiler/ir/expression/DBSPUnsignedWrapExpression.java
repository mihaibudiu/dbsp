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

package org.dbsp.sqlCompiler.ir.expression;

import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.IDBSPNode;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeInteger;
import org.dbsp.util.IIndentStream;

/** An unsigned wrap expression just wraps a signed integer into an unsigned one
 * preserving order. */
public class DBSPUnsignedWrapExpression extends DBSPUnaryExpression {
    public DBSPUnsignedWrapExpression(CalciteObject node, DBSPExpression source) {
        super(node, computeType(node, source.getType()), DBSPOpcode.UNSIGNED_WRAP, source);
    }

    static DBSPType computeType(CalciteObject node, DBSPType sourceType) {
        DBSPTypeInteger intType = sourceType.to(DBSPTypeInteger.class);
        int width = intType.getWidth();
        return new DBSPTypeInteger(node, width * 2, false, false);
    }

    @Override
    public void accept(InnerVisitor visitor) {
        VisitDecision decision = visitor.preorder(this);
        if (decision.stop()) return;
        visitor.push(this);
        this.source.accept(visitor);
        visitor.pop(this);
        visitor.postorder(this);
    }

    @Override
    public boolean sameFields(IDBSPNode other) {
        DBSPUnsignedWrapExpression o = other.as(DBSPUnsignedWrapExpression.class);
        if (o == null)
            return false;
        return this.source == o.source;
    }

    public String getMethod() {
        return this.source.getType().mayBeNull ? "from_option" : "from_signed";
    }

    @Override
    public IIndentStream toString(IIndentStream builder) {
        return builder.append("UnsignedWrapper")
                .append("::")
                .append(this.getMethod())
                .append("(")
                .append(source)
                .append(")");
    }

    public DBSPType getSourceType() {
        return this.source.getType();
    }

    public DBSPType getIntermediateType() {
        DBSPTypeInteger intType = this.getSourceType().to(DBSPTypeInteger.class);
        int width = intType.getWidth();
        return new DBSPTypeInteger(this.getNode(), width * 2, true, false);
    }

    @Override
    public DBSPExpression deepCopy() {
        return new DBSPUnsignedWrapExpression(this.getNode(), this.source.deepCopy());
    }
}
