package org.dbsp.sqlCompiler.compiler.sql.quidem;

import org.junit.Test;

/** Based on struct.iq */
public class StructTests extends ScottBaseTests {
    @Test
    public void testRow() {
        this.qs("""
                -- struct.iq - Queries involving structured types
                -- [CALCITE-2677] Struct types with one field are not mapped correctly to Java Classes
                select * from (values
                    (1, ROW(1)),
                    (2, ROW(2))) as v(id,struct);
                +----+--------+
                | ID | STRUCT |
                +----+--------+
                |  1 | {1}    |
                |  2 | {2}    |
                +----+--------+
                (2 rows)
                
                -- [CALCITE-3021] Equality of nested ROWs returns false for identical values
                select distinct * from (values
                    (1, ROW(1,1)),
                    (1, ROW(1,1)),
                    (2, ROW(2,2))) as v(id,struct);
                +----+--------+
                | ID | STRUCT |
                +----+--------+
                |  1 | {1, 1} |
                |  2 | {2, 2} |
                +----+--------+
                (2 rows)
                
                -- [CALCITE-3482] Equality of nested ROWs returns false for identical literal value
                select * from
                (values
                    (1, ROW(1,2)),
                    (2, ROW(2,1)),
                    (3, ROW(1,2)),
                    (4, ROW(2,1))) as t(id,struct)
                where t.struct = ROW(2,1);
                +----+--------+
                | ID | STRUCT |
                +----+--------+
                |  2 | {2, 1} |
                |  4 | {2, 1} |
                +----+--------+
                (2 rows)
                
                -- [CALCITE-3482] Equality of nested ROWs returns false for identical literal value
                select id from
                (values
                    (1, ROW(2, ROW(4,3))),
                    (2, ROW(2, ROW(3,4))),
                    (3, ROW(1, ROW(3,4))),
                    (4, ROW(2, ROW(3,4)))) as t(id,struct)
                where t.struct = ROW(2, ROW(3,4));
                +----+
                | ID |
                +----+
                |  2 |
                |  4 |
                +----+
                (2 rows)
                
                -- [CALCITE-4434] Cannot implement 'CASE row WHEN row ...'
                SELECT deptno, job,
                  CASE (deptno, job)
                  WHEN (20, 'CLERK') THEN 1
                  WHEN (30, 'SALESMAN') THEN 2
                  ELSE 3
                  END AS x
                FROM emp
                WHERE empno < 7600;
                +--------+----------+---+
                | DEPTNO | JOB      | X |
                +--------+----------+---+
                |     20 | CLERK|     1 |
                |     20 | MANAGER|   3 |
                |     30 | SALESMAN|  2 |
                |     30 | SALESMAN|  2 |
                +--------+----------+---+
                (4 rows)
                
                -- Equivalent to previous
                SELECT deptno, job,
                  CASE
                  WHEN deptno = 20 AND job = 'CLERK' THEN 1
                  WHEN deptno = 30 AND job = 'SALESMAN' THEN 2
                  ELSE 3
                  END AS x
                FROM emp
                WHERE empno < 7600;
                +--------+----------+---+
                | DEPTNO | JOB      | X |
                +--------+----------+---+
                |     20 | CLERK|     1 |
                |     20 | MANAGER|   3 |
                |     30 | SALESMAN|  2 |
                |     30 | SALESMAN|  2 |
                +--------+----------+---+
                (4 rows)
                
                -- Here we diverge from the Calcite and standard SQL result: original result is
                -- [CALCITE-3627] Null check if all fields of ROW are null
                -- +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                -- | ALL_NULL_IS_NULL | ALL_NULL_IS_NOT_NULL | EXCEPT_ONE_ALL_NULL_IS_NULL | EXCEPT_ONE_ALL_NULL_IS_NOT_NULL | REVERSE_NULL_CHECK_EXCEPT_ONE_ALL_NULL | ALL_NULL_INCLUDING_NESTED_ROW_IS_NULL | ALL_NULL_EXCEPT_NESTED_ROW_IS_NULL |
                -- +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                -- | true             | false                | false                       | true                            | true                                   | true                                  | false                              |
                -- +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                -- (1 row)
                select
                  ROW(null, null, null) is null AS all_null_is_null,
                  ROW(null, null, null) is not null AS all_null_is_not_null,
                  ROW(null, 1, null) is null AS except_one_all_null_is_null,
                  ROW(null, 1, null) is not null AS except_one_all_null_is_not_null,
                  NOT(ROW(null, 1, null) is null) AS reverse_null_check_except_one_all_null,
                  ROW(null, ROW(null, null), null) is null AS all_null_including_nested_row_is_null,
                  ROW(null, ROW(null, 1), null) is null AS all_null_except_nested_row_is_null;
                +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                | ALL_NULL_IS_NULL | ALL_NULL_IS_NOT_NULL | EXCEPT_ONE_ALL_NULL_IS_NULL | EXCEPT_ONE_ALL_NULL_IS_NOT_NULL | REVERSE_NULL_CHECK_EXCEPT_ONE_ALL_NULL | ALL_NULL_INCLUDING_NESTED_ROW_IS_NULL | ALL_NULL_EXCEPT_NESTED_ROW_IS_NULL |
                +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                | false            | true                 | false                       | true                            | true                                   | false                                 | false                              |
                +------------------+----------------------+-----------------------------+---------------------------------+----------------------------------------+---------------------------------------+------------------------------------+
                (1 row)""");
    }
}
