package org.dbsp.sqlCompiler.ir.type.primitive;

import com.fasterxml.jackson.databind.JsonNode;
import org.dbsp.sqlCompiler.compiler.backend.JsonDecoder;
import org.dbsp.sqlCompiler.compiler.frontend.calciteObject.CalciteObject;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.compiler.visitors.inner.InnerVisitor;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPBinaryLiteral;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.util.IIndentStream;
import org.dbsp.util.Utilities;

import static org.dbsp.sqlCompiler.ir.type.DBSPTypeCode.BYTES;
import static org.dbsp.sqlCompiler.ir.type.DBSPTypeCode.BYTES256;

/** Specialized TypeBinary implementation for 256 bits. */
public class DBSPTypeBinary256 extends DBSPTypeBaseType implements IHasPrecision {
    public DBSPTypeBinary256(CalciteObject node, boolean mayBeNull) {
        super(node, BYTES256, mayBeNull);
    }

    @Override
    public DBSPType withMayBeNull(boolean mayBeNull) {
        if (this.mayBeNull == mayBeNull)
            return this;
        return new DBSPTypeBinary256(this.getNode(), mayBeNull);
    }

    @Override
    public DBSPExpression defaultValue() {
        if (this.mayBeNull)
            return this.none();
        return new DBSPBinaryLiteral(new byte[] {}, false);
    }

    @Override
    public boolean sameType(DBSPType type) {
        DBSPTypeBinary256 other = type.as(DBSPTypeBinary256.class);
        if (other == null)
            return false;
        return super.sameNullability(type);
    }

    @Override
    public boolean hasCopy() {
        return false;
    }

    @Override
    public void accept(InnerVisitor visitor) {
        VisitDecision decision = visitor.preorder(this);
        if (decision.stop()) return;
        visitor.push(this);
        visitor.pop(this);
        visitor.postorder(this);
    }

    @Override
    public int getPrecision() {
        return 256;
    }

    @SuppressWarnings("unused")
    public static DBSPTypeBinary256 fromJson(JsonNode node, JsonDecoder decoder) {
        boolean mayBeNull = DBSPType.fromJsonMayBeNull(node);
        return new DBSPTypeBinary256(CalciteObject.EMPTY, mayBeNull);
    }
}
