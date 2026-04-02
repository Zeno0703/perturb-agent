package org.utils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.function.Function;

public class FileUtils {

    public static void writeAtomic(Path file, String content) throws IOException {
        Files.createDirectories(file.toAbsolutePath().getParent());

        Path tmp = file.resolveSibling(file.getFileName().toString() + ".tmp");
        Files.writeString(tmp, content, StandardCharsets.UTF_8);

        try {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException e) {
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    public static <T> void writeLinesAtomic(Path file, Iterable<T> items, Function<T, String> lineMapper) throws IOException {
        StringBuilder sb = new StringBuilder();
        for (T item : items) {
            sb.append(lineMapper.apply(item)).append("\n");
        }
        writeAtomic(file, sb.toString());
    }
}