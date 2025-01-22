package me.norax.nyaBackup;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Stream;
import me.norax.nyaBackup.methods.Compressor;

import static net.lingala.zip4j.util.Zip4jUtil.createDirectoryIfNotExists;
import static org.codehaus.plexus.util.FileUtils.deleteDirectory;

public class BackupManager {
    private final NyaBackup plugin;
    private final ConfigManager config;
    private final Compressor compressor;
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
        this.compressor = new Compressor(plugin);
        initializeCache();
    }

    private void initializeCache() {
        try {
            createDirectoryIfNotExists(cachePath.toFile());
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

    public void createBackup(String name) {
        try {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().getWorlds().forEach(world -> {
                    world.setAutoSave(false);
                    world.save();
                });
                plugin.getServer().getOnlinePlayers().forEach(Player::saveData);
            });

            createDirectoryIfNotExists(backupDir.toFile());

            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                try {
                    String backupFileName = name.isBlank() ?
                            "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + ".zip" :
                            name.endsWith(".zip") ? name : name + ".zip";

                    Path backupFile = backupDir.resolve(backupFileName);
                    List<Path> filesToBackup = getFilesToBackup();
                    compressor.createZipBackup(backupFile, filesToBackup, name);

                    cleanOldBackups();

                    plugin.getLogger().info("Backup created: " + backupFile);
                } catch (IOException | NoSuchAlgorithmException e) {
                    logError("Backup failed", e);
                }
            });
        } catch (Exception e) {
            logError("Backup initialization failed", e);
        } finally {
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(true));
            });
        }
    }

    public void loadBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile)) {
            throw new FileNotFoundException("Backup file not found: " + backupFile);
        }

        Path tempDir = Files.createTempDirectory("backup_restore");

        try {
            compressor.extractZipBackup(backupFile, tempDir);

            if (config.isKickEnabled()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.kick(Component.text(Objects.requireNonNull(config.getKickMessage())));
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
                                createDirectoryIfNotExists(targetPath.getParent().toFile());
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

    private List<Path> getFilesToBackup() throws IOException {
        List<String> exclusions = ConfigManager.getExclusions();
        List<PathMatcher> matchers = exclusions.stream()
                .map(pattern -> {
                    pattern = pattern.replace("/*", "/**");
                    return FileSystems.getDefault().getPathMatcher("glob:" + pattern);
                })
                .toList();
        try (Stream<Path> stream = Files.walk(serverDir)) {
            return stream.filter(Files::isRegularFile)
                    .filter(file -> {
                        Path relativePath = serverDir.relativize(file);
                        String pathStr = relativePath.toString().replace('\\', '/');
                        boolean excluded = matchers.stream()
                                .anyMatch(matcher -> matcher.matches(relativePath));

                        return !excluded;
                    })
                    .toList();
        }
    }



    private void cleanOldBackups() throws IOException {
        int maxBackups = config.getMaxBackups();
        try (Stream<Path> backups = Files.list(backupDir)
                .filter(path -> path.toString().endsWith(".zip") || path.toString().endsWith(".7z"))
                .sorted((a, b) -> b.toString().compareTo(a.toString()))) {

            List<Path> backupsList = backups.toList();
            if (backupsList.size() > maxBackups) {
                for (int i = maxBackups; i < backupsList.size(); i++) {
                    Files.delete(backupsList.get(i));
                }
            }
        }
    }

    private void logError(String message, Exception e) {
        plugin.getLogger().severe(message + ": " + e.getMessage());
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

    public void shutdown() {
        if (backupTask != null) {
            backupTask.cancel();
        }
    }
}
