package org.dbsp.sqlCompiler.compiler.visitors.inner;

import org.dbsp.sqlCompiler.compiler.IErrorReporter;
import org.dbsp.sqlCompiler.compiler.errors.UnimplementedException;
import org.dbsp.sqlCompiler.compiler.visitors.VisitDecision;
import org.dbsp.sqlCompiler.ir.expression.DBSPBinaryExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPCastExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPOpcode;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPMapLiteral;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPStringLiteral;
import org.dbsp.sqlCompiler.ir.type.DBSPType;
import org.dbsp.sqlCompiler.ir.type.derived.DBSPTypeTuple;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeString;
import org.dbsp.sqlCompiler.ir.type.primitive.DBSPTypeVariant;
import org.dbsp.sqlCompiler.ir.type.user.DBSPTypeMap;
import org.dbsp.util.Linq;

import java.util.ArrayList;
import java.util.List;

/** Rewrites a cast(struct as variant) into a map constructor, recursively */
public class ExpandVariantCast extends InnerRewriteVisitor {
    public ExpandVariantCast(IErrorReporter reporter) {
        super(reporter);
    }

    static DBSPExpression convertToVariant(DBSPExpression source, boolean mayBeNull) {
        // Convert a tuple to a VARIANT MAP indexed by the field names
        DBSPExpression expression = source;
        if (source.type.is(DBSPTypeTuple.class)) {
            DBSPTypeTuple tuple = source.getType().to(DBSPTypeTuple.class);
            DBSPTypeMap type = new DBSPTypeMap(
                    DBSPTypeString.varchar(false),
                    new DBSPTypeVariant(false),
                    source.getType().mayBeNull);

            if (tuple.originalStruct == null) {
                throw new UnimplementedException("Cast between Tuple type and " +
                        tuple.asSqlString() + " not implemented", source.getNode());
            }
            List<DBSPExpression> keys = new ArrayList<>();
            List<DBSPExpression> values = new ArrayList<>();
            List<String> names = Linq.list(tuple.originalStruct.getFieldNames());
            for (int i = 0; i < tuple.size(); i++) {
                String fieldName = names.get(i);
                keys.add(new DBSPStringLiteral(fieldName));

                DBSPExpression field = source.field(i).simplify();
                DBSPExpression rec = convertToVariant(field, false);
                values.add(rec);
            }
            expression = new DBSPMapLiteral(type, keys, values);
        }
        return new DBSPCastExpression(source.getNode(), expression, new DBSPTypeVariant(mayBeNull));
    }

    static DBSPExpression convertToStruct(DBSPExpression source, DBSPTypeTuple type) {
        // Convert a tuple to a VARIANT MAP indexed by the field names
        List<DBSPExpression> fields = new ArrayList<>();
        assert type.originalStruct != null;
        List<String> names = Linq.list(type.originalStruct.getFieldNames());
        for (int i = 0; i < type.size(); i++) {
            String fieldName = names.get(i);
            DBSPType fieldType = type.getFieldType(i);
            DBSPExpression index = new DBSPBinaryExpression(
                    // Result of index is always nullable
                    source.getNode(), new DBSPTypeVariant(true),
                    DBSPOpcode.VARIANT_INDEX, source, new DBSPStringLiteral(fieldName));
            DBSPExpression expression;
            if (fieldType.is(DBSPTypeTuple.class)) {
                expression = convertToStruct(index, fieldType.to(DBSPTypeTuple.class));
            } else {
                expression = index.cast(fieldType);
            }
            fields.add(expression);
        }
        return new DBSPTupleExpression(source.getNode(), type, fields);
    }

    @Override
    public VisitDecision preorder(DBSPCastExpression expression) {
        this.push(expression);
        DBSPExpression source = this.transform(expression.source);
        DBSPType type = this.transform(expression.getType());
        boolean structToVariant = type.is(DBSPTypeVariant.class);
        structToVariant = structToVariant && expression.source.getType().is(DBSPTypeTuple.class);
        DBSPExpression result = source.cast(type);
        if (structToVariant) {
            result = convertToVariant(source, type.mayBeNull);
        }

        boolean variantToStruct = type.is(DBSPTypeTuple.class);
        variantToStruct = variantToStruct && expression.source.getType().is(DBSPTypeVariant.class);
        if (variantToStruct) {
            result = convertToStruct(source, type.to(DBSPTypeTuple.class));
        }

        this.pop(expression);
        this.map(expression, result);
        return VisitDecision.STOP;
    }
}
