package ca.viaware.reflow;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class FileUtils {
    private static final Logger log = LogManager.getLogger(FileUtils.class);

    private static final int HASH_MAX_READ = 64 * 1024;

    public static String getFileHashId(File file) {
        try (var in = new FileInputStream(file)) {
            var digest = MessageDigest.getInstance("SHA-1");

            byte[] buffer = new byte[4096];

            int read;
            int totalRead = 0;

            while ((read = in.read(buffer)) != -1) {
                int remaining = HASH_MAX_READ - totalRead;

                if (remaining < read) {
                    read = remaining;
                }

                totalRead += read;

                digest.update(buffer, 0, read);

                if (totalRead >= HASH_MAX_READ) {
                    break;
                }
            }

            long len = file.length();
            digest.update((byte) (len & 0xFF));
            digest.update((byte) ((len >> 8) & 0xFF));
            digest.update((byte) ((len >> 16) & 0xFF));
            digest.update((byte) ((len >> 24) & 0xFF));

            byte[] hash = digest.digest();

            var builder = new StringBuilder();
            for (var b : hash) {
                builder.append(Integer.toString((b & 0xFF) + 0x100, 16).substring(1));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException | IOException e) {
            log.error(e);
        }
        return null;
    }

    public static void copyFileToFolder(File file, File folder) {
        if (!folder.exists() && !folder.mkdir()) {
            return;
        }

        var newFile = new File(folder, file.getName());

        int index = 1;
        while (newFile.exists()) {
            newFile = new File(folder, "(" + (index++) + ") " + file.getName());
        }

        var buffer = new byte[4096];
        var read = 0;
        try (var in = new FileInputStream(file); var out = new FileOutputStream(newFile)) {
            while ((read = in.read(buffer)) != -1) {
                out.write(buffer, 0, read);
            }
        } catch (IOException e) {
            log.error(e);
        }
    }
}
