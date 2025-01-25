package me.norax.nyaBackup.commands;

import me.norax.nyaBackup.NyaBackup;
import me.norax.nyaBackup.helpers.Logger;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Objects;

public class Snapshot implements CommandExecutor, TabCompleter {
    private final NyaBackup plugin;
    private final File backupFolder;

    public Snapshot(NyaBackup plugin) {
        this.plugin = plugin;
        this.backupFolder = new File(plugin.getDataFolder(), "backups");
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("nyabackup.manager")) {
            Logger.error("You don't have permission to use this command!");
            return true;
        }

        Logger.setSender(sender);

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "new":
                Logger.info("Starting backup...");
                plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
                    try {
                        if (args.length < 2 || args[1].isBlank()) {
                            plugin.getBackupManager().createBackup("");
                        } else {
                            String backupName = args[1];
                            plugin.getBackupManager().createBackup(backupName);
                        }
                    } catch (Exception e) {
                        Logger.error("Backup failed: " + e.getMessage());
                    }
                });
                break;

            case "load":
                if (args.length < 2) {
                    Logger.error("Usage: /backup load <backupfile>");
                    return true;
                }
                String backupFile = args[1];
                File backup = new File(plugin.getDataFolder(), "backups/" + backupFile);
                if (!backup.exists()) {
                    Logger.error("Backup file not found: " + backupFile);
                    return true;
                }
                Logger.info("Loading backup...");
                try {
                    plugin.getBackupManager().loadBackup(backup.toPath());
                } catch (IOException e) {
                    Logger.error("Failed to load the backup: " + e.getMessage());
                }
                Logger.success("Backup loaded successfully!");
                break;

            case "info":
                Logger.info("==== Server informations ====");
                Logger.info("Running version: " + plugin.getDescription().getVersion());
                try {
                    FileStore fileStore = Files.getFileStore(Paths.get("."));
                    long totalSpace = fileStore.getTotalSpace() / (1024 * 1024 * 1024);
                    long usableSpace = fileStore.getUsableSpace() / (1024 * 1024 * 1024);
                    Logger.info("Available storage: " + totalSpace + " GB");
                    Logger.info("Usable storage: " + usableSpace + " GB");
                } catch (Exception e) {
                    Logger.error("Failed to retrieve storage information.");
                }


                long backupSize = 0;
                try {
                    backupSize = calculateFolderSize(backupFolder) / (1024 * 1024);
                } catch (IOException e) {
                    Logger.error("Failed to calculate backup folder size.");
                }
                Logger.info("Backups used storage: " + backupSize + " MB");

                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024 * 1024);
                Logger.info("Memory: " + usedMemory + " MB / " + maxMemory + " GB");
                break;
            case "list":
                Logger.info("==== Available backups ====");
                if (backupFolder.exists() && backupFolder.isDirectory()) {
                    File[] files = backupFolder.listFiles();
                    if (files != null && files.length > 0) {
                        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
                        for (File file : files) {
                            if (file.isFile()) {
                                String name = file.getName();
                                long size = file.length() / (1024 * 1024) ;
                                String creationDate = sdf.format(file.lastModified());
                                Logger.info(String.format("Name: %s, Size: %dmb, Created at: %s", name, size, creationDate));
                            }
                        }
                    } else {
                        Logger.info("It seems like you dont have any backups!.");
                    }
                }
                break;
            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        Logger.error("== Invalid usage ====");
        Logger.info("/nyabackup new <name>");
        Logger.info("/nyabackup delete <name>");
        Logger.info("/nyabackup load <backupfile>");
        Logger.info("/nyabackup list");
        Logger.info("/nyabackup info");
    }

    private long calculateFolderSize(File folder) throws IOException {
        if (!folder.exists() || !folder.isDirectory()) {
            return 0;
        }
        return Files.walk(folder.toPath())
                .filter(Files::isRegularFile)
                .mapToLong(path -> path.toFile().length())
                .sum();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        if (args.length == 1) {
            return List.of("new", "load", "list", "info");
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("load")) {
            File backupFolder = new File(plugin.getDataFolder(), "backups");
            if (backupFolder.exists() && backupFolder.isDirectory()) {
                return List.of(Objects.requireNonNull(backupFolder.list()));
            }
        }

        return List.of();
    }
}
