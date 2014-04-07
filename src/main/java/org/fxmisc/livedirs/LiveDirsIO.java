package org.fxmisc.livedirs;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

class LiveDirsIO<O> implements OriginTrackingIOFacility<O> {
    private final DirWatcher dirWatcher;
    private final LiveDirsModel<O> model;
    private final Executor clientThreadExecutor;

    public LiveDirsIO(DirWatcher dirWatcher, LiveDirsModel<O> model, Executor clientThreadExecutor) {
        this.dirWatcher = dirWatcher;
        this.model = model;
        this.clientThreadExecutor = clientThreadExecutor;
    }

    @Override
    public CompletionStage<Void> createFile(Path file, O origin) {
        CompletableFuture<Void> created = new CompletableFuture<>();
        dirWatcher.createFile(file,
                lastModified -> {
                    model.addFile(file, origin, lastModified);
                    created.complete(null);
                },
                error -> created.completeExceptionally(error));
        return wrap(created);
    }

    @Override
    public CompletionStage<Void> createDirectory(Path dir, O origin) {
        CompletableFuture<Void> created = new CompletableFuture<>();
        dirWatcher.createDirectory(dir,
                () -> {
                    if(model.containsPrefixOf(dir)) {
                        model.addDirectory(dir, origin);
                        dirWatcher.watchOrLogError(dir);
                    }
                    created.complete(null);
                },
                error -> created.completeExceptionally(error));
        return wrap(created);
    }

    @Override
    public CompletionStage<Void> saveTextFile(Path file, String content, Charset charset, O origin) {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        dirWatcher.saveTextFile(file, content, charset,
                lastModified -> {
                    model.updateModificationTime(file, lastModified, origin);
                    saved.complete(null);
                },
                error -> saved.completeExceptionally(error));
        return wrap(saved);
    }

    @Override
    public CompletionStage<Void> saveBinaryFile(Path file, byte[] content, O origin) {
        CompletableFuture<Void> saved = new CompletableFuture<>();
        dirWatcher.saveBinaryFile(file, content,
                lastModified -> {
                    model.updateModificationTime(file, lastModified, origin);
                    saved.complete(null);
                },
                error -> saved.completeExceptionally(error));
        return wrap(saved);
    }

    @Override
    public CompletionStage<Void> delete(Path file, O origin) {
        CompletableFuture<Void> deleted = new CompletableFuture<>();
        dirWatcher.deleteFileOrEmptyDirectory(file,
                () -> {
                    model.delete(file, origin);
                    deleted.complete(null);
                },
                error -> deleted.completeExceptionally(error));
        return wrap(deleted);
    }

    @Override
    public CompletionStage<Void> deleteTree(Path root, O origin) {
        CompletableFuture<Void> deleted = new CompletableFuture<>();
        dirWatcher.deleteTree(root,
                () -> {
                    model.delete(root, origin);
                    deleted.complete(null);
                },
                error -> deleted.completeExceptionally(error));
        return wrap(deleted);
    }

    @Override
    public CompletionStage<String> loadTextFile(Path file, Charset charset) {
        CompletableFuture<String> loaded = new CompletableFuture<>();
        dirWatcher.loadTextFile(file, charset,
                content -> loaded.complete(content),
                error -> loaded.completeExceptionally(error));
        return wrap(loaded);
    }

    @Override
    public CompletionStage<byte[]> loadBinaryFile(Path file) {
        CompletableFuture<byte[]> loaded = new CompletableFuture<>();
        dirWatcher.loadBinaryFile(file,
                content -> loaded.complete(content),
                error -> loaded.completeExceptionally(error));
        return wrap(loaded);
    }

    private <T> CompletionStage<T> wrap(CompletionStage<T> stage) {
        return new CompletionStageWithDefaultExecutor<>(stage, clientThreadExecutor);
    }
}