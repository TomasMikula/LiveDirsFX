package org.fxmisc.livedirs;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javafx.application.Platform;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

/**
 *
 * @param <O> type of the origin of file changes.
 */
public class LiveDirs<O> implements AutoCloseable, OriginTrackingIOFacility<O> {

    private final EventSource<Throwable> localErrors = new EventSource<>();
    private final EventStream<Throwable> errors;
    private final Executor clientThreadExecutor;
    private final DirWatcher dirWatcher;
    private final DirectoryModel<O> model;
    private final O originExternal;

    public LiveDirs(O originExternal) throws IOException {
        this(originExternal, Platform::runLater);
    }

    public LiveDirs(O originExternal, Executor clientThreadExecutor) throws IOException {
        this.originExternal = originExternal;
        this.clientThreadExecutor = clientThreadExecutor;
        this.dirWatcher = new DirWatcher(clientThreadExecutor);
        this.model = new DirectoryModel<>(originExternal);

        this.dirWatcher.signalledKeys().subscribe(key -> processKey(key));
        this.errors = EventStreams.merge(dirWatcher.errors(), model.errors(), localErrors);
    }

    public EventStream<Throwable> errors() { return errors; }

    public DirectoryModel<O> model() { return model; }

    public void addTopLevelDirectory(Path dir) {
        if(!dir.isAbsolute()) {
            throw new IllegalArgumentException(dir + " is not absolute. Only absolute paths may be added as top-level directories.");
        }

        try {
            dirWatcher.watch(dir);
            model.addTopLevelDirectory(dir);
            refresh(dir);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    public CompletionStage<Void> refresh(Path path) {
        return wrap(dirWatcher.getTree(path))
                .thenAcceptAsync(tree -> { model.sync(tree); watchTree(tree); }, clientThreadExecutor);
    }

    @Override
    public void close() {
        dirWatcher.shutdown();
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
                    handleDirCreation(dir, origin);
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

    private void processKey(WatchKey key) {
        Path dir = (Path) key.watchable();
        if(!model.contains(dir)) {
            key.cancel();
        } else {
            List<WatchEvent<?>> events = key.pollEvents();
            if(events.stream().anyMatch(evt -> evt.kind() == OVERFLOW)) {
                refreshOrLogError(dir);
            } else {
                for(WatchEvent<?> evt: key.pollEvents()) {
                    @SuppressWarnings("unchecked")
                    WatchEvent<Path> event = (WatchEvent<Path>) evt;
                    processEvent(dir, event);
                }
            }

            if(!key.reset()) {
                model.delete(dir, originExternal);
            }
        }
    }

    private void processEvent(Path dir, WatchEvent<Path> event) {
        // Context for directory entry event is the file name of entry
        Path relChild = event.context();
        Path child = dir.resolve(relChild);

        Kind<Path> kind = event.kind();

        if(kind == ENTRY_MODIFY) {
            handleModification(child, originExternal);
        } else if(kind == ENTRY_CREATE) {
            handleCreation(child, originExternal);
        } else if(kind == ENTRY_DELETE) {
            model.delete(child, originExternal);
        } else {
            throw new AssertionError("unreachable code");
        }
    }

    private void handleCreation(Path path, O origin) {
        if(Files.isDirectory(path)) {
            handleDirCreation(path, origin);
        } else {
            handleFileCreation(path, origin);
        }
    }

    private void handleFileCreation(Path path, O origin) {
        try {
            FileTime timestamp = Files.getLastModifiedTime(path);
            model.addFile(path, origin, timestamp);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    private void handleDirCreation(Path path, O origin) {
        if(model.contains(path)) {
            model.addDirectory(path, origin);
            dirWatcher.watchOrLogError(path);
        }
        refreshOrLogError(path);
    }

    private void handleModification(Path path, O origin) {
        try {
            FileTime timestamp = Files.getLastModifiedTime(path);
            model.updateModificationTime(path, timestamp, origin);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    private void watchTree(PathNode tree) {
        if(tree.isDirectory()) {
            dirWatcher.watchOrLogError(tree.getPath());
            for(PathNode child: tree.getChildren()) {
                watchTree(child);
            }
        }
    }

    private void refreshOrLogError(Path path) {
        refresh(path).whenComplete((nothing, ex) -> {
            if(ex != null) {
                localErrors.push(ex);
            }
        });
    }

    private <T> CompletionStage<T> wrap(CompletionStage<T> stage) {
        return new CompletionStageWithDefaultExecutor<>(stage, clientThreadExecutor);
    }
}