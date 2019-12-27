package ca.viaware.reflow;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Directory;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.ExifImageDirectory;
import com.drew.metadata.avi.AviDirectory;
import com.drew.metadata.eps.EpsDirectory;
import com.drew.metadata.iptc.IptcDirectory;
import com.drew.metadata.mov.QuickTimeDirectory;
import com.drew.metadata.mp4.Mp4Directory;
import com.drew.metadata.wav.WavDirectory;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;

public class PhotoReflow {

    private static final Logger log = LogManager.getLogger(PhotoReflow.class);

    private static Date tryTag(Date curr, Directory directory, int tagName) {
        if (curr != null) {
            return curr;
        }

        if (directory.containsTag(tagName)) {
            return directory.getDate(tagName);
        }

        return null;
    }

    public static void main(String[] args) throws ParseException, IOException {
        log.info("Photo Reflow v0.0.1");

        var options = new Options();
        options.addOption("i", true, "Input photo library folder");
        options.addOption("o", true, "Output reflow folder must exist");
        options.addOption("f", true, "Date format string");

        var parser = new DefaultParser();
        var cmd = parser.parse(options, args);

        if (!cmd.hasOption("i") || !cmd.hasOption("o")) {
            var fmt = new HelpFormatter();
            fmt.printHelp("photo-reflow", options);
            return;
        }

        String dateFormat = "yyyy-MM-dd";
        if (cmd.hasOption("f")) {
            dateFormat = cmd.getOptionValue("f");
        }

        var inFolder = new File(cmd.getOptionValue("i"));
        var outFolder = new File(cmd.getOptionValue("o"));

        if (!inFolder.isDirectory() || !outFolder.isDirectory()) {
            log.error("Input and output folders must be directories");
            return;
        }

        var outList = outFolder.listFiles();
        if (outList != null && outList.length > 0) {
            log.error("Output folder must be empty");
            return;
        }

        final var hashes = new HashMap<String, Path>();

        Files.walkFileTree(inFolder.toPath(), new FileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path path, BasicFileAttributes basicFileAttributes) throws IOException {
                var hash = FileUtils.getFileHashId(path.toFile());
                if (hashes.containsKey(hash)) {
                    log.info("Found duplicated {}", path);
                } else {
                    hashes.put(hash, path);
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Path path, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path path, IOException e) throws IOException {
                return FileVisitResult.CONTINUE;
            }
        });

        log.info("Built list of {} unique files", hashes.size());

        var errPath = outFolder.toPath().resolve("unknown");
        Files.createDirectory(errPath);

        SimpleDateFormat format = new SimpleDateFormat(dateFormat);

        for (var path : hashes.values()) {
            var file = path.toFile();

            try {
                Metadata meta = ImageMetadataReader.readMetadata(file);

                Date date = null;

                for (var f : meta.getDirectoriesOfType(AviDirectory.class)) {
                    date = tryTag(date, f, AviDirectory.TAG_DATETIME_ORIGINAL);
                }

                for (var f : meta.getDirectoriesOfType(EpsDirectory.class)) {
                    date = tryTag(date, f, EpsDirectory.TAG_CREATION_DATE);
                }

                for (var f : meta.getDirectoriesOfType(IptcDirectory.class)) {
                    date = tryTag(date, f, IptcDirectory.TAG_DIGITAL_DATE_CREATED);
                    date = tryTag(date, f, IptcDirectory.TAG_DATE_CREATED);
                }

                for (var f : meta.getDirectoriesOfType(QuickTimeDirectory.class)) {
                    date = tryTag(date, f, QuickTimeDirectory.TAG_CREATION_TIME);
                }

                for (var f : meta.getDirectoriesOfType(Mp4Directory.class)) {
                    date = tryTag(date, f, Mp4Directory.TAG_CREATION_TIME);
                }

                for (var f : meta.getDirectoriesOfType(WavDirectory.class)) {
                    date = tryTag(date, f, WavDirectory.TAG_DATE_CREATED);
                }

                for (var f : meta.getDirectoriesOfType(ExifSubIFDDirectory.class)) {
                    date = tryTag(date, f, ExifSubIFDDirectory.TAG_DATETIME);
                    date = tryTag(date, f, ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                }

                for (var f : meta.getDirectoriesOfType(ExifImageDirectory.class)) {
                    date = tryTag(date, f, ExifImageDirectory.TAG_DATETIME);
                    date = tryTag(date, f, ExifImageDirectory.TAG_DATETIME_ORIGINAL);
                }

                if (date != null) {
                    var dateString = format.format(date);
                    var folder = outFolder.toPath().resolve(dateString);
                    if (!Files.exists(folder)) {
                        Files.createDirectory(folder);
                    }

                    log.info("Copying {} to {}", file, folder);
                    FileUtils.copyFileToFolder(file, folder.toFile());
                } else {
                    log.error("Unable to get a date for {}", file);
                    FileUtils.copyFileToFolder(file, errPath.toFile());
                }
            } catch (Exception e) {
                log.error("Unable to read metadata from {}", file);
                FileUtils.copyFileToFolder(file, errPath.toFile());
            }
        }
    }

}
