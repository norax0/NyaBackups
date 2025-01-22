package me.norax.nyaBackup.methods;

import me.norax.nyaBackup.ConfigManager;
import me.norax.nyaBackup.NyaBackup;
import net.lingala.zip4j.ZipFile;
import net.lingala.zip4j.model.ZipParameters;
import net.lingala.zip4j.model.enums.CompressionLevel;
import net.lingala.zip4j.model.enums.CompressionMethod;
import org.bukkit.scheduler.BukkitRunnable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static net.lingala.zip4j.util.Zip4jUtil.createDirectoryIfNotExists;

public class Compressor {
    private static final Logger log = LoggerFactory.getLogger(Compressor.class);
    private BukkitRunnable backupTask;
    private final Path serverDir;
    private Set<String> excludedPaths;

    public Compressor(NyaBackup plugin) {
        this.serverDir = plugin.getServer().getWorldContainer().toPath();
    }


    public void extractZipBackup(Path backupFile, Path targetDir) throws IOException {
        try (var zis = new java.util.zip.ZipInputStream(new FileInputStream(backupFile.toFile()))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (!entry.getName().endsWith(".reference")) {
                    Path targetPath = targetDir.resolve(entry.getName());
                    createDirectoryIfNotExists(targetPath.getParent().toFile());
                    Files.copy(zis, targetPath);
                }
            }
        }
    }


    public Path createZipBackup(Path backupFile, List<Path> files, String filename) throws IOException, NoSuchAlgorithmException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(backupFile.toFile()))) {
            for (Path file : files) {
                String relativePath = serverDir.relativize(file).toString().replace(File.separator, "/");

                ZipEntry entry = new ZipEntry(relativePath);
                zos.putNextEntry(entry);
                Files.copy(file, zos);
                zos.closeEntry();
            }

            String newHash = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backupFile))
            );
            String referenceContent = "hash:" + newHash + "\noriginal:" + filename;
            ZipEntry refEntry = new ZipEntry(filename + ".reference");
            zos.putNextEntry(refEntry);
            zos.write(referenceContent.getBytes());
            zos.closeEntry();
        }
        return backupFile;
    }


    public Path create7zBackup(Path backupFile, List<Path> files, Path serverDir, Map<String, String> cachedFileHashes) throws IOException, NoSuchAlgorithmException {
        try (ZipFile zipFile = new ZipFile(backupFile.toFile())) {
            ZipParameters parameters = new ZipParameters();
            parameters.setCompressionMethod(CompressionMethod.DEFLATE);
            parameters.setCompressionLevel(CompressionLevel.NORMAL);

            for (Path file : files) {
                String relativePath = serverDir.relativize(file).toString().replace(File.separator, "/");
                if (excludedPaths.contains(relativePath)) continue;

                parameters.setFileNameInZip(relativePath);
                zipFile.addFile(file.toFile(), parameters);
            }

            String newHash = Base64.getEncoder().encodeToString(
                    MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(backupFile))
            );
            String referenceContent = "hash:" + newHash + "\noriginal:" + backupFile.getFileName();
            Path tempFile = Files.createTempFile("reference", ".txt");
            Files.write(tempFile, referenceContent.getBytes());

            ZipParameters refParameters = new ZipParameters();
            refParameters.setCompressionMethod(CompressionMethod.DEFLATE);
            refParameters.setCompressionLevel(CompressionLevel.NORMAL);
            refParameters.setFileNameInZip("backup.reference");

            zipFile.addFile(tempFile.toFile(), refParameters);
            Files.delete(tempFile);
        }
        return backupFile;
    }
}