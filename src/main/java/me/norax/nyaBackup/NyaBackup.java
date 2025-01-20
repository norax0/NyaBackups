package me.norax.nyaBackup;

import me.norax.nyaBackup.commands.Backup;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.logging.Logger;
import org.bukkit.command.PluginCommand;

public final class NyaBackup extends JavaPlugin {
    private static NyaBackup instance;
    private BackupManager backupManager;
    private ConfigManager configManager;
    private final Logger logger = getLogger();

    @Override
    public void onEnable() {
        instance = this;

        try {
            configManager = new ConfigManager(this);
            configManager.loadConfig();

            backupManager = new BackupManager(this);

            PluginCommand command = getCommand("nyabackup");
            if (command != null) {
                command.setExecutor(new Backup(this));
            }

            if (configManager.isRobotEnabled()) {
                backupManager.scheduleAutomaticBackups();
            }
        } catch (Exception e) {
            logger.severe(e.getMessage());
            e.printStackTrace();
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
    }

    @Override
    public void onDisable() {
        if (backupManager != null) {
            try {
                backupManager.shutdown();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public static NyaBackup getInstance() {
        return instance;
    }

    public BackupManager getBackupManager() {
        return backupManager;
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public Logger getPluginLogger() {
        return logger;
    }
}