package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

class LiveDirsIO<I> implements InitiatorTrackingIOFacility<I> {
    private final DirWatcher dirWatcher;
    private final LiveDirsModel<I, ?> model;
    private final Executor clientThreadExecutor;

    public LiveDirsIO(DirWatcher dirWatcher, LiveDirsModel<I, ?> model, Executor clientThreadExecutor) {
        this.dirWatcher = dirWatcher;
        this.model = model;
        this.clientThreadExecutor = clientThreadExecutor;
    }

    @Override
    public CompletionStage<Void> createFile(Path file, I initiator) {
        CompletableFuture<Void> created = new CompletableFuture<>();
        dirWatcher.createFile(file,
                lastModified -> {
                    model.addFile(file, initiator, lastModified);
                    created.complete(null);
                },
                created::completeExceptionally);
        return wrap(created);
    }

    @Override
    public CompletionStage<Void> createDirectory(Path dir, I initiator) {
        CompletableFuture<Void> created = new CompletableFuture<>();
        dirWatcher.createDirectory(dir,
                () -> {
                    if(model.containsPrefixOf(dir)) {
                        model.addDirectory(dir, initiator);
                        dirWatcher.watchOrLogError(dir);
                    }
                    created.complete(null);
                },
                created::completeExceptionally);
        return wrap(created);
    }

    @Override
    public CompletionStage<Void> saveTextFile(Path file, String content, Charset charset, I initiator) {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        dirWatcher.saveTextFile(file, content, charset,
                lastModified -> {
                    model.updateModificationTime(file, lastModified, initiator);
                    saved.complete(null);
                },
                saved::completeExceptionally);
        return wrap(saved);
    }

    @Override
    public CompletionStage<Void> saveBinaryFile(Path file, byte[] content, I initiator) {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        dirWatcher.saveBinaryFile(file, content,
                lastModified -> {
                    model.updateModificationTime(file, lastModified, initiator);
                    saved.complete(null);
                },
                saved::completeExceptionally);
        return wrap(saved);
    }

    @Override
    public CompletionStage<Void> delete(Path file, I initiator) {
        CompletableFuture<Void> deleted = new CompletableFuture<>();
        dirWatcher.deleteFileOrEmptyDirectory(file,
                () -> {
                    model.delete(file, initiator);
                    deleted.complete(null);
                },
                deleted::completeExceptionally);
        return wrap(deleted);
    }

    @Override
    public CompletionStage<Void> deleteTree(Path root, I initiator) {
        CompletableFuture<Void> deleted = new CompletableFuture<>();
        dirWatcher.deleteTree(root,
                () -> {
                    model.delete(root, initiator);
                    deleted.complete(null);
                },
                deleted::completeExceptionally);
        return wrap(deleted);
    }

    @Override
    public CompletionStage<String> loadTextFile(Path file, Charset charset) {
        CompletableFuture<String> loaded = new CompletableFuture<>();
        dirWatcher.loadTextFile(file, charset,
                loaded::complete,
                loaded::completeExceptionally);
        return wrap(loaded);
    }

    @Override
    public CompletionStage<byte[]> loadBinaryFile(Path file) {
        CompletableFuture<byte[]> loaded = new CompletableFuture<>();
        dirWatcher.loadBinaryFile(file,
                loaded::complete,
                loaded::completeExceptionally);
        return wrap(loaded);
    }

    private <T> CompletionStage<T> wrap(CompletionStage<T> stage) {
        return new CompletionStageWithDefaultExecutor<>(stage, clientThreadExecutor);
    }
}