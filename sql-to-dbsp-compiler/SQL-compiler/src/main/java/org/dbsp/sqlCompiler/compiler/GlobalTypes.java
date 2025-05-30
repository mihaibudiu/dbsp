package org.dbsp.sqlCompiler.compiler;

import org.dbsp.sqlCompiler.compiler.frontend.calciteCompiler.ProgramIdentifier;
import org.dbsp.sqlCompiler.ir.type.derived.DBSPTypeStruct;
import org.dbsp.util.NameGen;
import org.dbsp.util.Utilities;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

/** Holds information about the user-defined struct types */
class GlobalTypes {
    final NameGen structNameGen = new NameGen("struct_");
    final Map<ProgramIdentifier, DBSPTypeStruct> declarations = new HashMap<>();

    public String generateSaneName(ProgramIdentifier name) {
        if (this.declarations.containsKey(name))
            return this.declarations.get(name).sanitizedName;
        // After a sane name is generated, we expect that the structure will be shortly registered.
        return structNameGen.nextName();
    }

    public void register(DBSPTypeStruct struct) {
        Utilities.putNew(this.declarations, struct.name, struct);
    }

    @Nullable
    public DBSPTypeStruct getStructByName(ProgramIdentifier name) {
        return this.declarations.get(name);
    }

    public boolean containsStruct(ProgramIdentifier name) {
        return this.declarations.containsKey(name);
    }
}
