package org.dbsp.sqlCompiler.circuit;

import org.dbsp.sqlCompiler.circuit.operator.DBSPOperator;
import org.dbsp.sqlCompiler.circuit.operator.DBSPViewOperator;
import org.dbsp.sqlCompiler.compiler.frontend.calciteCompiler.ProgramIdentifier;
import org.dbsp.sqlCompiler.ir.IDBSPOuterNode;

import javax.annotation.Nullable;

/** Something that looks like a circuit: contains operators, and possibly declarations */
public interface ICircuit extends IDBSPOuterNode {
    void addOperator(DBSPOperator operator);
    @Nullable
    DBSPViewOperator getView(ProgramIdentifier name);
    Iterable<DBSPOperator> getAllOperators();
    /** True if these circuits contain the exact same operators in the exact same order */
    boolean sameCircuit(ICircuit other);
    boolean contains(DBSPOperator node);
}
