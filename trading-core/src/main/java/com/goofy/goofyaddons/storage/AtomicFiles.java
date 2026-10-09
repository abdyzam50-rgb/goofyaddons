package com.goofy.goofyaddons.storage;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;

/**
 * The one way trading state reaches disk.
 *
 * <p>Order journals, the profit ledger and execution history each wrote a temporary file and
 * moved it into place, but without forcing it to disk or asking for an atomic move, unlike the
 * production job journal. A power loss could then leave an empty or partial file where the
 * last good one used to be. Every replacement now flushes the new content first and moves it
 * atomically where the file system allows; an append is flushed before it is reported done.
 */
public final class AtomicFiles {
    private AtomicFiles() {}

    /** Replaces the file with this text, never leaving a partly written file in its place. */
    public static void replace(Path path, String text, String tempPrefix) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temp = Files.createTempFile(parent, tempPrefix, ".tmp");
        try {
            try (FileChannel channel = FileChannel.open(temp, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING)) {
                write(channel, text);
                channel.force(true);
            }
            try {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(temp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    /** Appends one line and returns only once it is on disk. */
    public static void appendLine(Path path, String line) throws IOException {
        if (line.indexOf('\n') >= 0 || line.indexOf('\r') >= 0) throw new IllegalArgumentException("A record must be one line");
        Files.createDirectories(path.toAbsolutePath().getParent());
        try (FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND)) {
            write(channel, line + "\n");
            channel.force(true);
        }
    }

    private static void write(FileChannel channel, String text) throws IOException {
        ByteBuffer buffer = ByteBuffer.wrap(text.getBytes(StandardCharsets.UTF_8));
        while (buffer.hasRemaining()) channel.write(buffer);
    }
}
