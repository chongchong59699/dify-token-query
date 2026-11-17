package com.company.difytokenquery.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Stores named SQL documents below the same per-user application directory as config.json. */
public class SqlFileService {
    private final Path directory;

    public SqlFileService() { this(defaultDirectory()); }
    public SqlFileService(Path directory) { this.directory = directory; }

    public Path getDirectory() { return directory; }

    public List<Path> list() throws IOException {
        if (!Files.exists(directory)) return new ArrayList<Path>();
        List<Path> files = new ArrayList<Path>();
        try (java.nio.file.DirectoryStream<Path> stream = Files.newDirectoryStream(directory, "*.sql")) {
            for (Path file : stream) if (Files.isRegularFile(file)) files.add(file);
        }
        Collections.sort(files, new Comparator<Path>() {
            public int compare(Path a, Path b) { return a.getFileName().toString().compareToIgnoreCase(b.getFileName().toString()); }
        });
        return files;
    }

    public String read(Path file) throws IOException { return new String(Files.readAllBytes(file), StandardCharsets.UTF_8); }

    public void save(Path file, String sql) throws IOException {
        if (file == null) throw new IOException("SQL 文件路径不能为空");
        Path normalized = file.toAbsolutePath().normalize();
        if (!normalized.startsWith(directory.toAbsolutePath().normalize())) throw new IOException("SQL 文件必须保存到应用目录");
        Files.createDirectories(normalized.getParent());
        Files.write(normalized, (sql == null ? "" : sql).getBytes(StandardCharsets.UTF_8));
    }

    public Path createPath(String name) {
        String safe = name == null ? "query" : name.trim();
        safe = safe.replaceAll("[\\\\/:*?\"<>|]", "_");
        if (safe.isEmpty()) safe = "query";
        if (!safe.toLowerCase().endsWith(".sql")) safe += ".sql";
        return directory.resolve(safe);
    }

    public static Path defaultDirectory() {
        return ConfigService.defaultConfigFile().getParent().resolve("queries");
    }
}
