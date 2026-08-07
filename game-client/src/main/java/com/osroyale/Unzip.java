package com.osroyale;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class Unzip {

    public static void unZipIt(String zipFile, String outputFolder, boolean deleteAfter) {
        Path zipPath = Paths.get(zipFile);
        Path outputPath = Paths.get(outputFolder);

        try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            System.err.println("Could not create output directory: " + e.getMessage());
            return;
        }

        try (FileSystem zipFs = FileSystems.newFileSystem(zipPath, (ClassLoader) null)) {
            Path zipRoot = zipFs.getPath("/");
            String stripPrefix = detectStripPrefix(zipRoot);

            Files.walkFileTree(zipRoot, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    String rel = zipRoot.relativize(dir).toString();
                    if (rel.isEmpty()) return FileVisitResult.CONTINUE;
                    if (rel.startsWith("__MACOSX")) return FileVisitResult.SKIP_SUBTREE;
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    String entryName = zipRoot.relativize(file).toString();

                    if (entryName.startsWith("__MACOSX")) return FileVisitResult.CONTINUE;

                    if (stripPrefix != null && entryName.startsWith(stripPrefix)) {
                        entryName = entryName.substring(stripPrefix.length());
                    }

                    if (entryName.isEmpty()) return FileVisitResult.CONTINUE;

                    Path destFile = outputPath.resolve(entryName.replace('/', File.separatorChar));

                    // Zip slip protection
                    if (!destFile.normalize().startsWith(outputPath.normalize())) {
                        System.err.println("Skipping unsafe zip entry: " + entryName);
                        return FileVisitResult.CONTINUE;
                    }

                    Files.createDirectories(destFile.getParent());

                    System.out.println("Unzipping: " + destFile + " (" + attrs.size() + " bytes)");
                    if (Client.instance != null) {
                        Client.instance.drawLoadingText(100, "Unzipping: " + destFile.getFileName());
                    }

                    Files.copy(file, destFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }
            });

        } catch (Exception e) {
            System.err.println("Error unzipping cache: " + e.getMessage());
            e.printStackTrace();
        }

        if (deleteAfter) {
            try {
                Files.deleteIfExists(zipPath);
            } catch (IOException e) {
                System.err.println("Could not delete zip file: " + e.getMessage());
            }
        }
    }

    private static String detectStripPrefix(Path zipRoot) throws IOException {
        final String[] prefix = {null};
        final boolean[] noPrefix = {false};

        Files.walkFileTree(zipRoot, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                String name = zipRoot.relativize(file).toString();
                if (name.startsWith("__MACOSX")) return FileVisitResult.CONTINUE;

                int sep = name.indexOf('/');
                if (sep < 0) {
                    noPrefix[0] = true;
                    return FileVisitResult.TERMINATE;
                }

                String candidate = name.substring(0, sep + 1);
                if (prefix[0] == null) {
                    prefix[0] = candidate;
                } else if (!prefix[0].equals(candidate)) {
                    noPrefix[0] = true;
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        });

        return noPrefix[0] ? null : prefix[0];
    }
}
