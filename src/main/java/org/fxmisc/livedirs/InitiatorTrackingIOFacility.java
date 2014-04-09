package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletionStage;

/**
 * Simple API for asynchronous file-system operations.
 * The operations are analogous to those of {@link IOFacility}, except that
 * every filesystem-changing operation takes an extra argument&mdash;the
 * initiator of the change.
 * @param <I> type of the initiator of I/O actions
 */
public interface InitiatorTrackingIOFacility<I> {

    CompletionStage<Void> createFile(Path file, I initiator);

    CompletionStage<Void> createDirectory(Path dir, I initiator);

    CompletionStage<Void> saveTextFile(
            Path file,
            String content,
            Charset charset,
            I initiator);

    CompletionStage<Void> saveBinaryFile(
            Path file,
            byte[] content,
            I initiator);

    CompletionStage<Void> delete(Path fileOrDir, I initiator);

    CompletionStage<Void> deleteTree(Path root, I initiator);

    CompletionStage<String> loadTextFile(Path file, Charset charset);

    CompletionStage<byte[]> loadBinaryFile(Path file);

    default CompletionStage<Void> saveUTF8File(
            Path file, String content, I initiator) {
        Charset utf8 = Charset.forName("UTF-8");
        return saveTextFile(file, content, utf8, initiator);
    }

    default CompletionStage<String> loadUTF8File(Path file) {
        Charset utf8 = Charset.forName("UTF-8");
        return loadTextFile(file, utf8);
    }

    /**
     * Returns an IOFacility that delegates all operations to this I/O facility
     * with the preset initiator of changes.
     */
    default IOFacility withInitiator(I initiator) {
        InitiatorTrackingIOFacility<I> self = this;

        return new IOFacility() {

            @Override
            public CompletionStage<Void> createFile(Path file) {
                return self.createFile(file, initiator);
            }

            @Override
            public CompletionStage<Void> createDirectory(Path dir) {
                return self.createDirectory(dir, initiator);
            }

            @Override
            public CompletionStage<Void> saveTextFile(
                    Path file, String content, Charset charset) {
                return self.saveTextFile(file, content, charset, initiator);
            }

            @Override
            public CompletionStage<Void> saveBinaryFile(
                    Path file, byte[] content) {
                return self.saveBinaryFile(file, content, initiator);
            }

            @Override
            public CompletionStage<Void> delete(Path fileOrDir) {
                return self.delete(fileOrDir, initiator);
            }

            @Override
            public CompletionStage<Void> deleteTree(Path root) {
                return self.deleteTree(root, initiator);
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
