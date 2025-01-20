package me.norax.nyaBackup.commands;

import me.norax.nyaBackup.NyaBackup;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Paths;

public class Backup implements CommandExecutor {
    private final NyaBackup plugin;

    public Backup(NyaBackup plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, @NotNull Command command, @NotNull String label, String @NotNull [] args) {
        if (!sender.hasPermission("nyabackup.backup")) {
            sender.sendMessage("§cYou don't have permission to use this command!");
            return true;
        }

        if (args.length < 1) {
            sendUsage(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "new":
                sender.sendMessage("§aCreating backup(expect some small overload)...");

                if (args.length < 2 || args[1].isBlank()) {
                    plugin.getBackupManager().createBackup("");
                } else {
                    String backupName = args[1];
                    plugin.getBackupManager().createBackup(backupName);
                }
                sender.sendMessage("§aBackup complete!");
                sender.sendMessage();
                break;

            case "load":
                if (args.length < 2) {
                    sender.sendMessage("§cUsage: /backup load  <backupfile>");
                    return true;
                }
                String backupFile = args[1];
                File backup = new File(plugin.getDataFolder(), "backups/" + backupFile);
                if (!backup.exists()) {
                    sender.sendMessage("§cBackup file not found: " + backupFile);
                    return true;
                }
                sender.sendMessage("§4Loading backup...");
                try {
                    plugin.getBackupManager().loadBackup(backup.toPath());
                } catch (IOException e) {
                    sender.sendMessage("§cFailed to load the backup: " + e.getMessage());
                }
                sender.sendMessage("§aBackup loaded successfully!");
                break;

            case "info":
                sender.sendMessage("§6==== NYABACKUP ====");
                sender.sendMessage("§7Running version: §f" + plugin.getDescription().getVersion());
                try {
                    FileStore fileStore = Files.getFileStore(Paths.get("."));
                    long totalSpace = fileStore.getTotalSpace() / (1024 * 1024 * 1024);
                    long usableSpace = fileStore.getUsableSpace() / (1024 * 1024 * 1024);
                    sender.sendMessage("§7Available storage: §f" + totalSpace + " GB");
                    sender.sendMessage("§7Usable storage: §f" + usableSpace + " GB");
                } catch (Exception e) {
                    sender.sendMessage("§cFailed to retrieve storage information.");
                }

                File backupFolder = new File(plugin.getDataFolder(), "backups");
                long backupSize = 0;
                try {
                    backupSize = calculateFolderSize(backupFolder) / (1024 * 1024);
                } catch (IOException e) {
                    sender.sendMessage("§cFailed to calculate backup folder size.");
                }
                sender.sendMessage("§7Backups used storage: §f" + backupSize + " MB");

                MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
                long usedMemory = memoryBean.getHeapMemoryUsage().getUsed() / (1024 * 1024);
                long maxMemory = memoryBean.getHeapMemoryUsage().getMax() / (1024 * 1024 * 1024);
                sender.sendMessage("§7Memory: §f" + usedMemory + " MB / " + maxMemory + " GB");
                break;

            default:
                sendUsage(sender);
                break;
        }

        return true;
    }

    private void sendUsage(CommandSender sender) {
        sender.sendMessage("§c== Invalid usage ====");
        sender.sendMessage("§e/backup new <name>");
        sender.sendMessage("§e/backup load <backupfile>");
        sender.sendMessage("§e/backup info");
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
}
