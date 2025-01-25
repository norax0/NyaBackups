package me.norax.nyaBackup;

import org.bukkit.configuration.file.FileConfiguration;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class ConfigManager {
    private final NyaBackup plugin;
    private static FileConfiguration config;

    public ConfigManager(NyaBackup plugin) {
        this.plugin = plugin;
    }

    public void loadConfig() {
        plugin.saveDefaultConfig();
        plugin.reloadConfig();
        config = plugin.getConfig();
    }

    public static String getServerJar() {
        return config.getString("server_jar", "paper.jar");
    }

    public boolean isKickEnabled() {
        return config.getBoolean("kick.enabled", true);
    }
    public @Nullable String getKickMessage() {
        return config.getString("kick.message", "A backup has been loaded,server is restarting!");
    }

    public boolean isRobotEnabled() {
        return config.getBoolean("robot.enabled", false);
    }

    public String getBackupInterval() {
        return config.getString("robot.every", "1d");
    }

    public int getMaxBackups() {
        return config.getInt("robot.max", 5);
    }

    public String getCompressionMethod() {
        return config.getString("compression-method", "7zp");
    }

    public static List<String> getExclusions() {
        return config.getStringList("optimizations.exclude");
    }

    public static List<String> getCaches() {
        return config.getStringList("optimizations.cached");
    }
}