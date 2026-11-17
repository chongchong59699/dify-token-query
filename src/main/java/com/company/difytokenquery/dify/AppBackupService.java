package com.company.difytokenquery.dify;

import com.company.difytokenquery.config.ConfigService;
import com.company.difytokenquery.model.ModelSlotChange;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Persists Dify app DSL YAML backups and raw JSON snapshots before any model replacement write. */
public class AppBackupService {
    private final Path backupRootDir;
    private final ObjectMapper objectMapper = new ObjectMapper().enable(SerializationFeature.INDENT_OUTPUT);

    public AppBackupService() {
        this(defaultBackupRootDir());
    }

    public AppBackupService(Path backupRootDir) {
        this.backupRootDir = backupRootDir;
    }

    public static Path defaultBackupRootDir() {
        return ConfigService.defaultConfigFile().getParent().resolve("backups");
    }

    public Path getBackupRootDir() {
        return backupRootDir;
    }

    public Path createBatchDirectory() throws IOException {
        String stamp = new SimpleDateFormat("yyyyMMdd-HHmmss").format(new Date());
        Path batchDir = backupRootDir.resolve(stamp);
        if (!Files.exists(batchDir)) {
            Files.createDirectories(batchDir);
        }
        return batchDir;
    }

    public BackupRecord backupApp(Path batchDir, String appId, String appName, String mode,
                                  String mainYamlContent, String publishedYamlContent,
                                  JsonNode rawJsonSnapshot, List<ModelSlotChange> plannedChanges) throws IOException {
        if (batchDir == null) {
            throw new IOException("备份目录为空");
        }
        if (!Files.exists(batchDir)) {
            Files.createDirectories(batchDir);
        }
        if (mainYamlContent == null || mainYamlContent.trim().isEmpty()) {
            throw new IOException("导出的 DSL YAML 内容为空，已中止保存以防数据丢失");
        }

        String prefix = sanitizeFileName(appName) + "_" + sanitizeFileName(appId);
        Path ymlFile = batchDir.resolve(prefix + ".yml");
        writeUtf8(ymlFile, mainYamlContent);
        verifyNonEmptyFile(ymlFile);

        Path publishedYmlFile = null;
        if (publishedYamlContent != null && !publishedYamlContent.trim().isEmpty()) {
            publishedYmlFile = batchDir.resolve(prefix + "_published.yml");
            writeUtf8(publishedYmlFile, publishedYamlContent);
            verifyNonEmptyFile(publishedYmlFile);
        }

        Path rawJsonFile = null;
        if (rawJsonSnapshot != null) {
            rawJsonFile = batchDir.resolve(prefix + "_raw.json");
            writeUtf8(rawJsonFile, objectMapper.writeValueAsString(rawJsonSnapshot));
        }

        BackupRecord record = new BackupRecord(
                appId, appName, mode, ymlFile, publishedYmlFile, rawJsonFile, plannedChanges
        );
        appendManifest(batchDir, record);
        return record;
    }

    public static String sanitizeFileName(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "app";
        }
        String cleaned = raw.trim().replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]+", "_");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            cleaned = "app";
        }
        if (cleaned.length() > 60) {
            cleaned = cleaned.substring(0, 60);
        }
        return cleaned;
    }

    private synchronized void appendManifest(Path batchDir, BackupRecord record) throws IOException {
        Path manifestFile = batchDir.resolve("manifest.json");
        List<Map<String, Object>> items = new ArrayList<Map<String, Object>>();
        if (Files.exists(manifestFile) && Files.size(manifestFile) > 0) {
            JsonNode existing = objectMapper.readTree(manifestFile.toFile());
            if (existing != null && existing.isArray()) {
                for (JsonNode item : existing) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> map = objectMapper.convertValue(item, Map.class);
                    items.add(map);
                }
            }
        }

        Map<String, Object> entry = new LinkedHashMap<String, Object>();
        entry.put("timestamp", new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
        entry.put("appId", record.getAppId());
        entry.put("appName", record.getAppName());
        entry.put("mode", record.getMode());
        entry.put("ymlFile", record.getYmlFile().toAbsolutePath().toString());
        entry.put("publishedYmlFile", record.getPublishedYmlFile() == null ? "" : record.getPublishedYmlFile().toAbsolutePath().toString());
        entry.put("rawJsonFile", record.getRawJsonFile() == null ? "" : record.getRawJsonFile().toAbsolutePath().toString());
        entry.put("plannedChanges", record.getPlannedChanges());
        items.add(entry);
        objectMapper.writeValue(manifestFile.toFile(), items);
    }

    private void writeUtf8(Path target, String content) throws IOException {
        Files.write(target, content.getBytes(StandardCharsets.UTF_8));
    }

    private void verifyNonEmptyFile(Path file) throws IOException {
        if (!Files.exists(file) || Files.size(file) <= 0) {
            throw new IOException("备份文件写入校验失败：" + file);
        }
    }

    public static class BackupRecord {
        private final String appId;
        private final String appName;
        private final String mode;
        private final Path ymlFile;
        private final Path publishedYmlFile;
        private final Path rawJsonFile;
        private final List<ModelSlotChange> plannedChanges;

        public BackupRecord(String appId, String appName, String mode, Path ymlFile,
                            Path publishedYmlFile, Path rawJsonFile, List<ModelSlotChange> plannedChanges) {
            this.appId = appId;
            this.appName = appName;
            this.mode = mode;
            this.ymlFile = ymlFile;
            this.publishedYmlFile = publishedYmlFile;
            this.rawJsonFile = rawJsonFile;
            this.plannedChanges = plannedChanges;
        }

        public String getAppId() { return appId; }
        public String getAppName() { return appName; }
        public String getMode() { return mode; }
        public Path getYmlFile() { return ymlFile; }
        public Path getPublishedYmlFile() { return publishedYmlFile; }
        public Path getRawJsonFile() { return rawJsonFile; }
        public List<ModelSlotChange> getPlannedChanges() { return plannedChanges; }
    }
}