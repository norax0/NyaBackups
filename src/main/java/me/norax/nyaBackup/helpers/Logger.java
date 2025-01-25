package me.norax.nyaBackup.helpers;

import org.bukkit.command.CommandSender;

public class Logger {

    public static Boolean isDebug = false;
    static final String bold = "§l";
    static final String reset = "§r";
    static final String red = "§c";
    static final String green = "§a";
    static final String yellow = "§e";
    static final String blue = "§9";
    static final String gray = "§7";
    private static CommandSender sender;

    public static void setSender(CommandSender newSender) {
        sender = newSender;
    }

    private static void sendMessage(String message) {
        if (sender != null) {
            sender.sendMessage(message);
        } else {
            System.out.println(message);
        }
    }

    public static void cleanScreen() {
        sendMessage("\033[H\033[2J");
    }

    public static void success(String... messages) {
        String combinedMessage = String.join("", messages);
        String message = green + "Nyabackup >> " + reset + combinedMessage;
        sendMessage(message);
    }

    public static void debug(String... messages) {
        if (!isDebug) { return; }
        String combinedMessage = String.join("", messages);
        String message = gray + "Nyabackup >> " + reset + combinedMessage;
        sendMessage(message);
    }

    public static void info(String... messages) {
        String combinedMessage = String.join("", messages);
        String message = blue + "Nyabackup >>> " + reset + combinedMessage;
        sendMessage(message);
    }

    public static void warn(String... messages) {
        String combinedMessage = String.join("", messages);
        String message = yellow + "Nyabackup >> " + reset + combinedMessage;
        sendMessage(message);
    }

    public static void error(String... messages) {
        String combinedMessage = String.join("", messages);
        String message = red + "Nyabackup >> " + reset + combinedMessage;
        sendMessage(message);
    }

    public static void divisor(String title) {
        String string = "----------" + bold + "[" + title + "]" + reset + "----------";
        sendMessage(string);
    }

    public interface Sender {
        void sendMessage(String message);
    }
}
