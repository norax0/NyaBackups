package me.norax.nyaBackup;

import me.norax.nyaBackup.helpers.Logger;
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

import java.util.stream.Collectors;
import java.util.stream.Stream;
import me.norax.nyaBackup.methods.Compressor;
import me.norax.nyaBackup.methods.Cacher;

import static net.lingala.zip4j.util.Zip4jUtil.createDirectoryIfNotExists;
import static org.codehaus.plexus.util.FileUtils.deleteDirectory;

public class BackupManager {
    private final NyaBackup plugin;
    private final ConfigManager config;
    private final Compressor compressor;
    private BukkitRunnable backupTask;
    private final Path serverDir;
    private final Path backupDir;

    private final Cacher cacher;
    public BackupManager(NyaBackup plugin) {
        this.plugin = plugin;
        this.cacher = new Cacher(plugin);
        this.config = plugin.getConfigManager();
        this.serverDir = plugin.getServer().getWorldContainer().toPath();
        this.backupDir = plugin.getDataFolder().toPath().resolve("backups");
        this.compressor = new Compressor(plugin);
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
                            "backup_" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) :
                            name;

                    Path backupFile = backupDir.resolve(backupFileName.endsWith(".zip") || backupFileName.endsWith(".7z") ? backupFileName :
                            config.getCompressionMethod().equalsIgnoreCase("zip") ? backupFileName + ".zip" : backupFileName + ".7z");

                    List<Path> filesToBackup = getFilesToBackup();

                    String compressionMethod = config.getCompressionMethod().toLowerCase();

                    switch (compressionMethod) {
                        case "zip":
                            compressor.createZipBackup(backupFile, filesToBackup, backupFileName);
                            break;
                        case "7z":
                            compressor.create7zBackup(backupFile, filesToBackup, backupFileName);
                            break;
                        default:
                            Logger.warn("Unsupported compression method found(" + config.getCompressionMethod() + ") falling back to 7z");
                            compressor.create7zBackup(backupFile, filesToBackup, backupFileName);
                            break;
                    }

                    getFilesToCache().forEach(file -> {
                        try {
                            cacher.cacheFile(file);
                        } catch (Exception e) {
                            logError("Failed to cache file: " + file, e);
                        }
                    });

                    cleanOldBackups();
                    Logger.success("Created backup at:", backupFileName);

                } catch (IOException | NoSuchAlgorithmException e) {
                    Logger.error("Error creating backup: " + e.getMessage());
                }
            });
        } catch (Exception e) {
            logError("Backup initialization failed", e);
        } finally {
            plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(true));
        }
    }


    public void loadBackup(Path backupFile) throws IOException {
        if (!Files.exists(backupFile)) {
            throw new FileNotFoundException("Backup file not found: " + backupFile);
        }

        Path tempDir = Files.createTempDirectory("backup_restore");

        try {
            compressor.extractZipBackup(backupFile, tempDir);

            try (Stream<Path> paths = Files.walk(tempDir)) {
                List<Path> fileList = paths
                        .filter(path -> !Files.isDirectory(path))
                        .filter(path -> !path.toString().endsWith(".reference"))
                        .filter(path -> !path.toString().endsWith(".dat"))
                        .filter(path -> !path.toString().endsWith(".dat_old"))
                        .toList();

                for (Path path : fileList) {
                    Path relativePath = tempDir.relativize(path);
                    Path targetPath = serverDir.resolve(relativePath);
                    createDirectoryIfNotExists(targetPath.getParent().toFile());

                    String filename = path.getFileName().toString();
                    try {
                        cacher.restoreCachedFile(filename);
                    } catch (IOException cachedFileNotFound) {
                        Files.copy(path, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            }

            if (config.isKickEnabled()) {
                for (Player player : Bukkit.getOnlinePlayers()) {
                    player.kick(Component.text(Objects.requireNonNull(config.getKickMessage())));
                }
            }

            plugin.getServer().shutdown();

        } finally {
            deleteDirectory(tempDir.toFile());
        }
    }


    private List<Path> getFilesToCache() throws IOException {
        List<String> cachePaths = ConfigManager.getCaches();
        return cachePaths.stream()
                .map(serverDir::resolve)
                .filter(Files::exists)
                .filter(Files::isRegularFile)
                .collect(Collectors.toList());
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
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.kick(Component.text("Server is restarting."));
        }

        plugin.getServer().getWorlds().forEach(world -> world.setAutoSave(false));

        if (backupTask != null) {
            backupTask.cancel();
        }
    }
}
