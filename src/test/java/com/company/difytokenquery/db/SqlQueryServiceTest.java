package com.company.difytokenquery.db;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SqlQueryServiceTest {
    @Test
    public void normalizeSqlRemovesOnlyTrailingSemicolons() {
        assertEquals("select ';' from test", SqlQueryService.normalizeSql(" select ';' from test; "));
    }

    @Test
    public void queryDetectionSupportsCommonPostgresReadStatements() {
        assertTrue(SqlQueryService.isQuery("SELECT\n1"));
        assertTrue(SqlQueryService.isQuery("with x as (select 1) select * from x"));
        assertTrue(SqlQueryService.isQuery("values (1)"));
        assertFalse(SqlQueryService.isQuery("update test set value = 1"));
    }
}
