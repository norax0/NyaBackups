package me.norax.nyaBackup;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import java.util.stream.Stream;
import static org.codehaus.plexus.util.FileUtils.deleteDirectory;

public class BackupManager {
    private final NyaBackup plugin;
    private final ConfigManager config;
    private BukkitRunnable backupTask;
    private final Map<String, String> cachedFileHashes;
    private final Path cachePath;
    private final Path serverDir;
    private final Path backupDir;

    public BackupManager(NyaBackup plugin) {
        this.plugin = plugin;
        this.config = plugin.getConfigManager();
        this.serverDir = plugin.getServer().getWorldContainer().toPath();
        this.cachePath = plugin.getDataFolder().toPath().resolve("cache");
        this.backupDir = plugin.getDataFolder().toPath().resolve("backups");
        this.cachedFileHashes = new HashMap<>();
        initializeCache();
    }

    private void initializeCache() {
        try {
            createDirectoryIfNotExists(cachePath);
            List<String> cachedFiles = new ArrayList<>(config.getCached());
            cachedFiles.add(config.getServerJar());

            for (String filename : cachedFiles) {
                Path hashFile = cachePath.resolve(filename + ".hash");
                if (Files.exists(hashFile)) {
                    cachedFileHashes.put(filename, Files.readString(hashFile));
                }
            }
        } catch (IOException e) {
            logError("Failed to initialize cache", e);
        }
    }

    private void createDirectoryIfNotExists(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            Files.createDirectories(dir);
        }
    }

    private String calculateFileHash(Path file) {
        try {
            return Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file))
            );
        } catch (Exception e) {
            logError("Failed to calculate hash for " + file, e);
            return null;
        }
    }

    private void updateFileCache(String filename, Path file) {
        try {
            String newHash = calculateFileHash(file);
            if (newHash == null) return;

            Path hashFile = cachePath.resolve(filename + ".hash");
            Files.writeString(hashFile, newHash);

            if (!newHash.equals(cachedFileHashes.get(filename))) {
                Path cachedFile = cachePath.resolve(filename);
                Files.copy(file, cachedFile, StandardCopyOption.REPLACE_EXISTING);
                cachedFileHashes.put(filename, newHash);
            }
        } catch (IOException e) {
            logError("Failed to update cache for " + filename, e);
        }
    }
    public void createBackup(String name) {
        try {
            plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(false));

            plugin.getServer().getWorlds().forEach(World::save);

            plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(true));
            createDirectoryIfNotExists(backupDir);

            Path backupFile = null;
            if (name.isBlank()) {
                String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss"));
                backupFile = backupDir.resolve("backup_" + timestamp + getBackupExtension());
            } else {
                backupFile = backupDir.resolve(name + getBackupExtension());
            }

            List<Path> filesToBackup = gatherFilesToBackup();
            if (!filesToBackup.isEmpty()) {
                createBackupArchive(backupFile, filesToBackup);
                cleanOldBackups();
            }
        } catch (IOException e) {
            logError("Backup failed", e);
        } finally {
            plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(true));
        }
    }

    private String getBackupExtension() {
        return config.getCompressionMethod().equals("7zp") ? ".7z" : ".zip";
    }

    private List<Path> gatherFilesToBackup() throws IOException {
        List<String> exclusions = getExclusionList();
        List<String> cachedFiles = new ArrayList<>(config.getCached());
        cachedFiles.add(config.getServerJar());

        for (String filename : cachedFiles) {
            Path file = serverDir.resolve(filename);
            if (Files.exists(file)) {
                updateFileCache(filename, file);
                exclusions.add(filename);
            }
        }

        try (Stream<Path> paths = Files.walk(serverDir)) {
            return paths.filter(path -> !Files.isDirectory(path))
                    .filter(path -> !isExcluded(path, exclusions))
                    .toList();
        }
    }

    private List<String> getExclusionList() {
        List<String> exclusions = new ArrayList<>(config.getExclusions());
        String cacheDirPath = plugin.getDataFolder().toPath().toString()
                .replace(File.separator, "/")
                .substring(serverDir.toString().length() + 1);
        exclusions.add(cacheDirPath + "/.*");
        return exclusions;
    }

    private boolean isExcluded(Path path, List<String> exclusions) {
        String relativePath = serverDir.relativize(path).toString().replace(File.separator, "/");
        return exclusions.stream().anyMatch(exclusion ->
                relativePath.matches(exclusion.replace(".", "\\.").replace("*", ".*")));
    }

    private void createBackupArchive(Path backupFile, List<Path> files) throws IOException {
        if (config.getCompressionMethod().equals("7zp")) {
            create7zBackup(backupFile, files);
        } else {
            createZipBackup(backupFile, files);
        }
    }

    private void addReferenceFiles(ZipOutputStream zos) throws IOException {
        for (Map.Entry<String, String> entry : cachedFileHashes.entrySet()) {
            addReferenceEntry(zos, entry.getKey(), entry.getValue());
        }
    }

    private void addReferenceEntry(ZipOutputStream zos, String filename, String hash) throws IOException {
        ZipEntry refEntry = new ZipEntry(filename + ".reference");
        zos.putNextEntry(refEntry);
        String reference = "hash:" + hash + "\noriginal:" + filename;
        zos.write(reference.getBytes());
        zos.closeEntry();
    }

    private void createZipBackup(Path backupFile, List<Path> files) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile.toFile()))) {
            for (Path file : files) {
                String relativePath = serverDir.relativize(file).toString().replace(File.separator, "/");
                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }
            addReferenceFiles(zos);
        }
    }

    private void create7zBackup(Path backupFile, List<Path> files) throws IOException {
        ZipFile zipFile = new ZipFile(backupFile.toFile());
        ZipParameters parameters = new ZipParameters();
        parameters.setCompressionMethod(CompressionMethod.DEFLATE);
        parameters.setCompressionLevel(CompressionLevel.NORMAL);

        for (Path file : files) {
            String relativePath = serverDir.relativize(file).toString();
            parameters.setFileNameInZip(relativePath);
            zipFile.addFile(file.toFile(), parameters);
        }

        for (Map.Entry<String, String> entry : cachedFileHashes.entrySet()) {
            Path tempFile = createTempReferenceFile(entry.getKey(), entry.getValue());
            parameters.setFileNameInZip(entry.getKey() + ".reference");
            zipFile.addFile(tempFile.toFile(), parameters);
            Files.delete(tempFile);
        }
    }

    private Path createTempReferenceFile(String filename, String hash) throws IOException {
        Path tempFile = Files.createTempFile(filename, ".reference");
        Files.writeString(tempFile, "hash:" + hash + "\noriginal:" + filename);
        return tempFile;
    }

    public void loadBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile)) {
            throw new FileNotFoundException("Backup file not found: " + backupFile);
        }

        Path tempDir = Files.createTempDirectory("backup_restore");

        try {
            if (backupFile.toString().endsWith(".7z")) {
                new ZipFile(backupFile.toFile()).extractAll(tempDir.toString());
            } else {
                extractZipBackup(backupFile, tempDir);
            }

            if (config.isKickEnabled()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.kickPlayer(config.getKickMessage());
                }
            }

            plugin.getServer().shutdown();

            try (Stream<Path> paths = Files.walk(tempDir)) {
                paths.filter(path -> !Files.isDirectory(path))
                        .filter(path -> !path.toString().endsWith(".reference"))
                        .forEach(path -> {
                            try {
                                Path relativePath = tempDir.relativize(path);
                                Path targetPath = serverDir.resolve(relativePath);
                                createDirectoryIfNotExists(targetPath.getParent());
                                Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                            } catch (IOException e) {
                                logError("Failed to restore file: " + path, e);
                            }
                        });
            }
        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }

    private void extractZipBackup(Path backupFile, Path targetDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(new FileInputStream(backupFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".reference")) {
                    Path targetPath = targetDir.resolve(entry.getName());
                    createDirectoryIfNotExists(targetPath.getParent());
                    Files.copy(zis, targetPath);
                }
            }
        }
    }

    private void cleanOldBackups() throws IOException {
        int maxBackups = config.getMaxBackups();
        try (Stream<Path> backups = Files.list(backupDir)
                .filter(path -> path.toString().endsWith(".zip") || path.toString().endsWith(".7z"))
                .sorted((a, b) -> b.toString().compareTo(a.toString()))) {

            List<Path> backupsList = backups.toList();
            if (backupsList.size() > maxBackups && !backupsList.isEmpty()) {
                Files.delete(backupsList.get(backupsList.size() - 1));
            }
        }
    }

    public void scheduleAutomaticBackups() {
        String interval = config.getBackupInterval();
        long ticks = parseInterval(interval);

        backupTask = new BukkitRunnable() {
            @Override
            public void run() {
                createBackup("");
            }
        };

        backupTask.runTaskTimer(plugin, ticks, ticks);
    }

    private long parseInterval(String interval) {
        int value = Integer.parseInt(interval.replaceAll("[^0-9]", ""));
        String unit = interval.replaceAll("[0-9]", "");

        return switch (unit.toLowerCase()) {
            case "s" -> value * 20L;
            case "m" -> value * 20L * 60L;
            case "h" -> value * 20L * 60L * 60L;
            default -> value * 20L * 60L * 60L * 24L;
        };
    }

    private void logError(String message, Exception e) {
        plugin.getLogger().severe(message + ": " + e.getMessage());
    }

    public void shutdown() {
        if (backupTask != null) {
            backupTask.cancel();
        }
    }
}