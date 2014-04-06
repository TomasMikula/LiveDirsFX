package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

public interface OriginTrackingIOFacility {

    CompletionStage<Void> createFile(Path file, Object origin);

    CompletionStage<Void> createDirectory(Path dir, Object origin);

    CompletionStage<Void> saveTextFile(
            Path file,
            String content,
            Charset charset,
            Object origin);

    CompletionStage<Void> saveBinaryFile(
            Path file,
            byte[] content,
            Object origin);

    /**
     * Deletes file or empty directory.
     */
    CompletionStage<Void> delete(Path fileOrDir, Object origin);

    CompletionStage<Void> deleteTree(Path root, Object origin);

    CompletionStage<String> loadTextFile(Path file, Charset charset);

    CompletionStage<byte[]> loadBinaryFile(Path file);

    default CompletionStage<Void> saveUTF8File(
            Path file, String content, Object origin) {
        Charset utf8 = Charset.forName("UTF-8");
        return saveTextFile(file, content, utf8, origin);
    }

    default CompletionStage<String> loadUTF8File(Path file) {
        Charset utf8 = Charset.forName("UTF-8");
        return loadTextFile(file, utf8);
    }

    default SettableOriginIOFacility asIOFacility() {
        return asIOFacility(null);
    }

    default SettableOriginIOFacility asIOFacility(Object initialOrigin) {
        OriginTrackingIOFacility self = this;

        return new SettableOriginIOFacility() {
            private Object origin = initialOrigin;

            @Override
            public void setOrigin(Object origin) {
                this.origin = origin;
            }

            @Override
            public Object getOrigin() {
                return origin;
            }

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
