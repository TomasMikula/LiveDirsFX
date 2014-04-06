package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

/**
 *
 * @param <O> origin type
 */
public interface OriginTrackingIOFacility<O> {

    CompletionStage<Void> createFile(Path file, O origin);

    CompletionStage<Void> createDirectory(Path dir, O origin);

    CompletionStage<Void> saveTextFile(
            Path file,
            String content,
            Charset charset,
            O origin);

    CompletionStage<Void> saveBinaryFile(
            Path file,
            byte[] content,
            O origin);

    /**
     * Deletes file or empty directory.
     */
    CompletionStage<Void> delete(Path fileOrDir, O origin);

    CompletionStage<Void> deleteTree(Path root, O origin);

    CompletionStage<String> loadTextFile(Path file, Charset charset);

    CompletionStage<byte[]> loadBinaryFile(Path file);

    default CompletionStage<Void> saveUTF8File(
            Path file, String content, O origin) {
        Charset utf8 = Charset.forName("UTF-8");
        return saveTextFile(file, content, utf8, origin);
    }

    default CompletionStage<String> loadUTF8File(Path file) {
        Charset utf8 = Charset.forName("UTF-8");
        return loadTextFile(file, utf8);
    }

    default IOFacility withOrigin(O origin) {
        OriginTrackingIOFacility<O> self = this;

        return new IOFacility() {

            @Override
            public CompletionStage<Void> createFile(Path file) {
                return self.createFile(file, origin);
            }

            @Override
            public CompletionStage<Void> createDirectory(Path dir) {
                return self.createDirectory(dir, origin);
            }

            @Override
            public CompletionStage<Void> saveTextFile(
                    Path file, String content, Charset charset) {
                return self.saveTextFile(file, content, charset, origin);
            }

            @Override
            public CompletionStage<Void> saveBinaryFile(
                    Path file, byte[] content) {
                return self.saveBinaryFile(file, content, origin);
            }

            @Override
            public CompletionStage<Void> delete(Path fileOrDir) {
                return self.delete(fileOrDir, origin);
            }

            @Override
            public CompletionStage<Void> deleteTree(Path root) {
                return self.deleteTree(root, origin);
            }

            @Override
            public CompletionStage<String> loadTextFile(
                    Path file, Charset charset) {
                return self.loadTextFile(file, charset);
            }

            @Override
            public CompletionStage<byte[]> loadBinaryFile(Path file) {
                return self.loadBinaryFile(file);
            }
        };
    }
}
