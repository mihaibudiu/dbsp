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

package org.dbsp.sqlCompiler.ir.type.user;

import com.fasterxml.jackson.databind.JsonNode;
import org.dbsp.sqlCompiler.compiler.backend.JsonDecoder;
import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.IDBSPInnerNode;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.util.IIndentStream;
import org.dbsp.util.Utilities;

import java.util.Objects;

import static org.dbsp.sqlCompiler.ir.type.DBSPTypeCode.STREAM;

/** A type of the form 'Stream<Circuit, elementType>' */
public class DBSPTypeStream extends DBSPType {
    /** Currently only 2 levels of nesting are supported */
    public final boolean outerCircuit;
    public final DBSPType elementType;

    public DBSPTypeStream(DBSPType elementType, boolean outerCircuit) {
        super(elementType.getNode(), STREAM, elementType.mayBeNull);
        this.elementType = elementType;
        this.outerCircuit = outerCircuit;
    }

    @Override
    public int getToplevelFieldCount() {
        return this.elementType.getToplevelFieldCount();
    }

    @Override
    public DBSPType withMayBeNull(boolean mayBeNull) {
        throw new UnsupportedOperationException();
    }

    @Override
    public DBSPExpression defaultValue() {
        throw new UnimplementedException();
    }

    @Override
    public boolean sameFields(IDBSPInnerNode other) {
        if (!this.sameNullability(other)) return false;
        DBSPTypeStream s = other.as(DBSPTypeStream.class);
        if (s == null) return false;
        return this.elementType == s.elementType &&
                this.outerCircuit == s.outerCircuit;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        VisitDecision decision = visitor.preorder(this);
        if (decision.stop()) return;
        visitor.push(this);
        visitor.property("elementType");
        this.elementType.accept(visitor);
        visitor.pop(this);
        visitor.postorder(this);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(),
                this.elementType.hashCode(),
                Objects.hash(this.outerCircuit));
    }

    @Override
    public boolean sameType(DBSPType other) {
        if (!super.sameNullability(other))
            return false;
        DBSPTypeStream oRef = other.as(DBSPTypeStream.class);
        if (oRef == null)
            return false;
        return this.elementType.sameType(oRef.elementType) &&
                this.outerCircuit == oRef.outerCircuit;
    }

    @Override
    public IIndentStream toString(IIndentStream builder) {
        return builder.append("Stream<")
                .append(this.elementType)
                .append(">");
    }

    @SuppressWarnings("unused")
    public static DBSPTypeStream fromJson(JsonNode node, JsonDecoder decoder) {
        DBSPType elementType = fromJsonInner(node, "elementType", decoder, DBSPType.class);
        boolean outerCircuit = Utilities.getBooleanProperty(node, "outerCircuit");
        return new DBSPTypeStream(elementType, outerCircuit);
    }
}
