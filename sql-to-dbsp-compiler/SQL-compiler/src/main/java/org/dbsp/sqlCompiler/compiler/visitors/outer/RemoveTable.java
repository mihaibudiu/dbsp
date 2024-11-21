package org.dbsp.sqlCompiler.compiler.visitors.outer;

import org.dbsp.sqlCompiler.circuit.operator.DBSPSourceMultisetOperator;
import org.dbsp.sqlCompiler.compiler.ICompilerComponent;
import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.util.Logger;

/** Remove a specified input table */
public class RemoveTable extends CircuitCloneVisitor {
    final ICompilerComponent compiler;
    final String tableName;

    RemoveTable(String tableName, DBSPCompiler compiler) {
        super(compiler, false);
        this.compiler = compiler;
        this.tableName = tableName;
    }

    @Override
    public void postorder(DBSPSourceMultisetOperator map) {
        if (map.tableName.equalsIgnoreCase(this.tableName)) {
            // Return without adding it to the circuit.
            Logger.INSTANCE.belowLevel(this, 1)
                    .append("Removing table ")
                    .append(this.tableName)
                    .newline();
            this.compiler.compiler().removeTable(this.tableName);
            return;
        }
        super.postorder(map);
    }
}
