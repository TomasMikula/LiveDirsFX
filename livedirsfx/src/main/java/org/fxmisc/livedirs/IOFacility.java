package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

/**
 * Simple API for asynchronous file-system operations.
 */
public interface IOFacility {

    /**
     * Creates an empty regular file.
     * If file already exists or an I/O error occurs, the returned completion
     * stage is completed exceptionally with the encountered error.
     */
    CompletionStage<Void> createFile(Path file);

    /**
     * Creates a directory.
     * If the directory already exists, or its parent directory does not exist,
     * or another I/O error occurs, the returned completion stage is completed
     * exceptionally with the encountered error.
     */
    CompletionStage<Void> createDirectory(Path dir);

    /**
     * Writes textual file to disk.
     */
    CompletionStage<Void> saveTextFile(
            Path file,
            String content,
            Charset charset);

    /**
     * Writes binary file to disk.
     */
    CompletionStage<Void> saveBinaryFile(Path file, byte[] content);

    /**
     * Deletes a file or an empty directory.
     */
    CompletionStage<Void> delete(Path fileOrDir);

    /**
     * Deletes file tree.
     */
    CompletionStage<Void> deleteTree(Path root);

    /**
     * Reads the contents of a text file.
     */
    CompletionStage<String> loadTextFile(Path file, Charset charset);

    /**
     * Reads the contents of a binary file.
     */
    CompletionStage<byte[]> loadBinaryFile(Path file);

    /**
     * Writes UTF8-encoded text to disk.
     */
    default CompletionStage<Void> saveUTF8File(Path file, String content) {
        Charset utf8 = Charset.forName("UTF-8");
        return saveTextFile(file, content, utf8);
    }

    /**
     * Reads the contents of an UTF8-encoded file.
     */
    default CompletionStage<String> loadUTF8File(Path file) {
        Charset utf8 = Charset.forName("UTF-8");
        return loadTextFile(file, utf8);
    }
}
