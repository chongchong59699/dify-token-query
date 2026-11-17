package com.company.difytokenquery.config;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SqlFileServiceTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void saveAndReadShouldKeepUtf8Sql() throws Exception {
        Path directory = temporaryFolder.newFolder("queries").toPath();
        SqlFileService service = new SqlFileService(directory);
        Path file = service.createPath("用户查询");
        service.save(file, "select * from 应用;");
        assertTrue(Files.exists(file));
        assertEquals("select * from 应用;", service.read(file));
        assertEquals(1, service.list().size());
    }
}
