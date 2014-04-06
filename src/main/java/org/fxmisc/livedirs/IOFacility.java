package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

public interface IOFacility {

    CompletionStage<Void> createFile(Path file);

    CompletionStage<Void> createDirectory(Path dir);

    CompletionStage<Void> saveTextFile(
            Path file,
            String content,
            Charset charset);

    CompletionStage<Void> saveBinaryFile(Path file, byte[] content);

    /**
     * Deletes file or empty directory.
     */
    CompletionStage<Void> delete(Path fileOrDir);

    CompletionStage<Void> deleteTree(Path root);

    CompletionStage<String> loadTextFile(Path file, Charset charset);

    CompletionStage<byte[]> loadBinaryFile(Path file);

    default CompletionStage<Void> saveUTF8File(Path file, String content) {
        Charset utf8 = Charset.forName("UTF-8");
        return saveTextFile(file, content, utf8);
    }

    default CompletionStage<String> loadUTF8File(Path file) {
        Charset utf8 = Charset.forName("UTF-8");
        return loadTextFile(file, utf8);
    }
}
