package org.dbsp.sqlCompiler.compiler.sql;

import org.dbsp.sqlCompiler.compiler.CompilerOptions;
import org.dbsp.sqlCompiler.compiler.DBSPCompiler;
import org.dbsp.sqlCompiler.compiler.sql.tools.Change;
import org.dbsp.sqlCompiler.compiler.sql.tools.CompilerCircuitStream;
import org.dbsp.sqlCompiler.compiler.sql.tools.SqlIoTest;
import org.dbsp.sqlCompiler.compiler.visitors.outer.Passes;
import org.dbsp.sqlCompiler.ir.expression.DBSPExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPTupleExpression;
import org.dbsp.sqlCompiler.ir.expression.DBSPZSetExpression;
import org.dbsp.sqlCompiler.ir.expression.literal.DBSPStringLiteral;
import org.dbsp.util.Logger;
import org.junit.Ignore;
import org.junit.Test;

/** Used for interactive debugging: create here temporary tests. */
@SuppressWarnings("unused")
public class IsolatedTest extends SqlIoTest {
    @Override
    public DBSPCompiler testCompiler() {
        CompilerOptions options = this.testOptions(false, true);
        options.ioOptions.raw = true;
        options.languageOptions.throwOnError = true;
        options.ioOptions.quiet = false;
        return new DBSPCompiler(options);
    }

    @Test
    public void nestedStructTest() {
        this.showFinalVerbose(2);
        Logger.INSTANCE.setLoggingLevel(Passes.class, 2);
        String sql = """
            CREATE TYPE address_typ AS (
               street          VARCHAR(30),
               city            VARCHAR(30),
               state           CHAR(2),
               postal_code     VARCHAR(6));
            CREATE TYPE person_typ AS (
               firstname       VARCHAR(30),
               lastname        VARCHAR(30),
               address         ADDRESS_TYP);
            CREATE TABLE PERS(p0 PERSON_TYP, p1 PERSON_TYP);
            CREATE VIEW V AS
            SELECT PERS.p0.address FROM PERS
            WHERE PERS.p0.firstname = 'Mike'""";
        CompilerCircuitStream ccs = this.getCCS(sql);
        DBSPExpression address0 = new DBSPTupleExpression(true,
                new DBSPStringLiteral("Broadway", true),
                new DBSPStringLiteral("New York", true),
                new DBSPStringLiteral("NY", true),
                new DBSPStringLiteral("10000", true)
        );
        DBSPExpression person0 = new DBSPTupleExpression(true,
                new DBSPStringLiteral("Mike", true),
                new DBSPStringLiteral("John", true),
                address0
        );
        DBSPExpression pair = new DBSPTupleExpression(person0, person0);
        DBSPZSetExpression input = new DBSPZSetExpression(pair);
        DBSPZSetExpression output = new DBSPZSetExpression(new DBSPTupleExpression(address0));
        ccs.addPair(new Change(input), new Change(output));
        this.addRustTestCase(ccs);
    }
}
