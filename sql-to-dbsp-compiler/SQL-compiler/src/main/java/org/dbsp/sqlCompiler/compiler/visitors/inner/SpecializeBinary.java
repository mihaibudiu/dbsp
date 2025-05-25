package org.dbsp.sqlCompiler.compiler.visitors.inner;

import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPBinaryLiteral;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBinary;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeBinary256;

/** Specialize {@link DBSPTypeBinary} with precision 256 into {@link DBSPTypeBinary256} */
public class SpecializeBinary extends InnerRewriteVisitor {
    static final int PRECISION = 256;

    public SpecializeBinary(DBSPCompiler compiler) {
        super(compiler, false);
    }

    static boolean needsReplacement(DBSPType type) {
        DBSPTypeBinary binary = type.as(DBSPTypeBinary.class);
        return binary != null && binary.precision == PRECISION;
    }

    static DBSPType getReplacement(DBSPType type) {
        return new DBSPTypeBinary256(type.getNode(), type.mayBeNull);
    }

    @Override
    public VisitDecision preorder(DBSPTypeBinary type) {
        if (needsReplacement(type)) {
            DBSPType replace = getReplacement(type);
            this.map(type, replace);
            return VisitDecision.STOP;
        }
        return super.preorder(type);
    }

    @Override
    public VisitDecision preorder(DBSPBinaryLiteral literal) {
        DBSPType type = literal.getType();
        if (needsReplacement(type)) {
            DBSPBinaryLiteral replacement = new DBSPBinaryLiteral(
                    literal.getNode(), getReplacement(type), literal.value);
            this.map(literal, replacement);
            return VisitDecision.STOP;
        }
        return super.preorder(literal);
    }
}
