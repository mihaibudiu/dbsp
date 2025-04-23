package org.dbsp.sqlCompiler.compiler.sql.quidem;

import org.junit.Ignore;
import org.junit.Test;

public class SubQueryTests extends ScottBaseTests {
    @Test
    public void firstValueTests() {
        // All these produce calls to FIRST_VALUE from the expansion
        // Modified and tested on MySQL, since Calcite treats NULLs differently.
        this.qs("""
                SELECT dname FROM dept WHERE 2000 > (SELECT emp.sal FROM emp where
                dept.deptno = emp.deptno ORDER BY year(hiredate), emp.sal limit 1);
                +----------+
                | DNAME    |
                +----------+
                | RESEARCH|
                | SALES|
                +----------+
                (2 rows)
                
                SELECT dname
                FROM dept
                WHERE 2000 > (SELECT emp.sal
                              FROM emp where dept.deptno = emp.deptno and mgr > 8000
                              ORDER BY year(hiredate), emp.sal limit 1);
                +-------+
                | DNAME |
                +-------+
                +-------+
                (0 rows)
                
                SELECT dname,
                     (SELECT emp.comm
                      FROM emp where dept.deptno = emp.deptno
                      ORDER BY emp.comm desc limit 1)
                FROM dept;
                +------------+--------+
                | DNAME      | EXPR$1 |
                +------------+--------+
                | ACCOUNTING|         |
                | OPERATIONS|         |
                | RESEARCH|           |
                | SALES|              |
                +------------+--------+
                (4 rows)
                
                SELECT dname,
                    (SELECT emp.sal
                     FROM emp where dept.deptno = emp.deptno
                     ORDER BY year(hiredate), emp.sal limit 1)
                FROM dept;
                +------------+---------+
                | DNAME      | EXPR$1  |
                +------------+---------+
                | ACCOUNTING|  2450.00 |
                | OPERATIONS|          |
                | RESEARCH|     800.00 |
                | SALES|        950.00 |
                +------------+---------+
                (4 rows)
                
                SELECT dname,
                   (SELECT emp.sal
                    FROM emp where dept.deptno = emp.deptno and mgr > 8000
                    ORDER BY year(hiredate), emp.sal limit 1)
                FROM dept;
                +------------+--------+
                | DNAME      | EXPR$1 |
                +------------+--------+
                | ACCOUNTING|         |
                | OPERATIONS|         |
                | RESEARCH|           |
                | SALES|              |
                +------------+--------+
                (4 rows)""");
    }

    @Test @Ignore("Cannot be decorrelated")
    public void testFirst1() {
        this.qs("""
                select sal from emp e
                where mod(cast(rand() as int), 2) = 3 OR 123 IN (
                    select cast(null as int)
                    from dept d
                    where d.deptno = e.deptno);
                 SAL
                -----
                (0 rows)
                
                select sal from emp e
                where 123 NOT IN (
                    select cast(null as int)
                    from dept d
                    where e.deptno=d.deptno);
                 SAL
                -----
                (0 rows)
                
                select sal from emp e
                where 10 NOT IN (
                    select deptno
                    from dept d
                    where e.deptno=d.deptno);
                 SAL
                ---------
                 1100.00
                 1250.00
                 1250.00
                 1500.00
                 1600.00
                 2850.00
                 2975.00
                 3000.00
                 3000.00
                  800.00
                  950.00
                (11 rows)
                
                select sal from emp e
                where 10 NOT IN (
                    select case when true then deptno else null end
                    from dept d
                    where e.deptno=d.deptno);
                 SAL
                ---------
                 1100.00
                 1250.00
                 1250.00
                 1500.00
                 1600.00
                 2850.00
                 2975.00
                 3000.00
                 3000.00
                  800.00
                  950.00
                (11 rows)""");
    }
}
