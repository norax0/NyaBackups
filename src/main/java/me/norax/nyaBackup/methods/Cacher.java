package me.norax.nyaBackup.methods;

import me.norax.nyaBackup.NyaBackup;
import me.norax.nyaBackup.ConfigManager;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;

public class Cacher {
    private final NyaBackup plugin;
    private final Path serverDir;
    private final Path cachePath;
    private final Map<String, String> cachedFileHashes;

    public Cacher(NyaBackup plugin) {
        this.plugin = plugin;
        this.serverDir = plugin.getServer().getWorldContainer().toPath();
        this.cachePath = plugin.getDataFolder().toPath().resolve("cached");
        this.cachedFileHashes = new HashMap<>();
        initializeCache();
    }

    private void initializeCache() {
        try {
            Files.createDirectories(cachePath);

            List<String> filesToCache = ConfigManager.getCaches();
            filesToCache.add(ConfigManager.getServerJar());

            for (String filename : filesToCache) {
                Path sourceFile = serverDir.resolve(filename);
                if (Files.exists(sourceFile)) {
                    cacheFile(sourceFile);
                }
            }
        } catch (IOException | NoSuchAlgorithmException e) {
            plugin.getLogger().severe("Failed to initialize cache: " + e.getMessage());
        }
    }

    public void cacheFile(Path sourceFile) throws IOException, NoSuchAlgorithmException {
        String filename = sourceFile.getFileName().toString();
        Path cachedFile = cachePath.resolve(filename);

        String fileHash = calculateFileHash(sourceFile);

        if (!cachedFileHashes.containsKey(filename) ||
                !cachedFileHashes.get(filename).equals(fileHash)) {

            Files.copy(sourceFile, cachedFile, StandardCopyOption.REPLACE_EXISTING);

            cachedFileHashes.put(filename, fileHash);

            Path hashFile = cachePath.resolve(filename + ".hash");
            Files.writeString(hashFile, fileHash);
        }
    }

    public void scheduleCacheCheck() {
        long interval = 20L * 60 * 60;

        BukkitRunnable cacheCheckTask = new BukkitRunnable() {
            @Override
            public void run() {
                checkAndUpdateCache();
            }
        };

        cacheCheckTask.runTaskTimer(plugin, interval, interval);
    }

    private void checkAndUpdateCache() {
        List<String> filesToCache = ConfigManager.getCaches();
        filesToCache.add(ConfigManager.getServerJar());

        for (String filename : filesToCache) {
            try {
                Path sourceFile = serverDir.resolve(filename);
                if (Files.exists(sourceFile)) {
                    cacheFile(sourceFile);
                }
            } catch (IOException | NoSuchAlgorithmException e) {
                plugin.getLogger().severe("Failed to update cache for " + filename + ": " + e.getMessage());
            }
        }
    }

    public void restoreCachedFile(String filename) throws IOException {
        Path cachedFile = cachePath.resolve(filename);
        Path targetFile = serverDir.resolve(filename);

        if (Files.exists(cachedFile)) {
            Files.copy(cachedFile, targetFile, StandardCopyOption.REPLACE_EXISTING);
        } else {
            throw new IOException("Cached file not found: " + filename);
        }
    }

    private String calculateFileHash(Path file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] fileBytes = Files.readAllBytes(file);
        byte[] hashBytes = digest.digest(fileBytes);
        return Base64.getEncoder().encodeToString(hashBytes);
    }

}