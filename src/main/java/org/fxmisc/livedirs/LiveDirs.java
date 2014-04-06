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
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

/**
 *
 * @param <O> type of the origin of file changes.
 */
public class LiveDirs<O> implements AutoCloseable, OriginTrackingIOFacility<O> {

    public interface GraphicFactory {
        Node create(Path path, boolean isDirectory);
    }

    public static enum UpdateType {
        CREATION,
        DELETION,
        MODIFICATION,
    }

    public static class Update<O> {
        static <O> Update<O> creation(Path baseDir, Path relPath, O origin) {
            return new Update<>(baseDir, relPath, origin, UpdateType.CREATION);
        }
        static <O> Update<O> deletion(Path baseDir, Path relPath, O origin) {
            return new Update<>(baseDir, relPath, origin, UpdateType.DELETION);
        }
        static <O> Update<O> modification(Path baseDir, Path relPath, O origin) {
            return new Update<>(baseDir, relPath, origin, UpdateType.MODIFICATION);
        }

        private final Path baseDir;
        private final Path relativePath;
        private final O origin;
        private final UpdateType type;
        private Update(Path baseDir, Path relPath, O origin, UpdateType type) {
            this.baseDir = baseDir;
            this.relativePath = relPath;
            this.origin = origin;
            this.type = type;
        }
        public O getOrigin() {
            return origin;
        }
        public Path getBaseDir() {
            return baseDir;
        }
        public Path getRelativePath() {
            return relativePath;
        }
        public Path getPath() {
            return baseDir.resolve(relativePath);
        }
        public UpdateType getType() {
            return type;
        }
    }

    private static final Image FOLDER_IMAGE = new Image(LiveDirs.class.getResource("folder-16.png").toString());
    private static final Image FILE_IMAGE = new Image(LiveDirs.class.getResource("file-16.png").toString());

    public static final GraphicFactory NO_GRAPHIC_FACTORY = (path, isDir) -> null;
    public static final GraphicFactory DEFAULT_GRAPHIC_FACTORY = (path, isDir) ->
            isDir
            ? new ImageView(FOLDER_IMAGE)
            : new ImageView(FILE_IMAGE);

    private final TreeItem<Path> root = new TreeItem<>();
    private final EventSource<Update<O>> creations = new EventSource<>();
    private final EventSource<Update<O>> deletions = new EventSource<>();
    private final EventSource<Update<O>> modifications = new EventSource<>();
    private final EventStream<Update<O>> updates = EventStreams.merge(
            creations, deletions, modifications);
    private final EventSource<Throwable> localErrors = new EventSource<>();
    private final EventStream<Throwable> errors;
    private final Executor clientThreadExecutor;
    private final DirWatcher dirWatcher;
    private final Reporter<O> reporter;
    private final O originExternal;

    private GraphicFactory graphicFactory = DEFAULT_GRAPHIC_FACTORY;

    public LiveDirs(O originExternal) throws IOException {
        this(originExternal, Platform::runLater);
    }

    public LiveDirs(O originExternal, Executor clientThreadExecutor) throws IOException {
        this.originExternal = originExternal;
        this.clientThreadExecutor = clientThreadExecutor;
        this.dirWatcher = new DirWatcher(clientThreadExecutor);
        this.dirWatcher.signalledKeys().subscribe(key -> processKey(key));
        this.errors = EventStreams.merge(dirWatcher.errors(), localErrors);
        this.reporter = new Reporter<O>() {
            @Override
            public void reportCreation(Path baseDir, Path relPath, O origin) {
                creations.push(Update.creation(baseDir, relPath, origin));
            }

            @Override
            public void reportDeletion(Path baseDir, Path relPath, O origin) {
                deletions.push(Update.deletion(baseDir, relPath, origin));
            }

            @Override
            public void reportModification(Path baseDir, Path relPath, O origin) {
                modifications.push(Update.modification(baseDir, relPath, origin));
            }

            @Override
            public void reportError(Throwable error) {
                localErrors.push(error);
            }
        };
    }

    public TreeItem<Path> getRoot() { return root; }
    public EventStream<Update<O>> creations() { return creations; }
    public EventStream<Update<O>> deletions() { return deletions; }
    public EventStream<Update<O>> modifications() { return modifications; }
    public EventStream<Update<O>> updates() { return updates; }
    public EventStream<Throwable> errors() { return errors; }

    public void setGraphicFactory(GraphicFactory factory) {
        graphicFactory = factory != null ? factory : DEFAULT_GRAPHIC_FACTORY;
    }

    public void addTopLevelDirectory(Path dir) {
        if(!dir.isAbsolute()) {
            throw new IllegalArgumentException(dir + " is not absolute. Only absolute paths may be added as top-level directories.");
        }

        try {
            dirWatcher.watch(dir);
            root.getChildren().add(new TopLevelDirItem<>(dir, graphicFactory, reporter));
            refresh(dir);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    public CompletionStage<Void> refresh(Path path) {
        return wrap(dirWatcher.getTree(path))
                .thenAcceptAsync(tree -> sync(tree), clientThreadExecutor);
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
                    addFileToModel(file, origin, lastModified);
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
                    addDirToModel(dir, origin);
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
                    updateModificationTime(file, lastModified, origin);
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
                    updateModificationTime(file, lastModified, origin);
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
                    handleDeletion(file, origin);
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
                    handleDeletion(root, origin);
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
        if(!isInModel(dir)) {
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
                handleDeletion(dir, originExternal);
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
            handleDeletion(child, originExternal);
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
            addFileToModel(path, origin, timestamp);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    private void addFileToModel(Path path, O origin, FileTime lastModified) {
        for(TopLevelDirItem<O> root: getTopLevelAncestors(path)) {
            Path relPath = root.getValue().relativize(path);
            root.addFile(relPath, lastModified, origin);
        }
    }

    private void handleDirCreation(Path path, O origin) {
        addDirToModel(path, origin);
        refreshOrLogError(path);
    }

    private void addDirToModel(Path path, O origin) {
        List<TopLevelDirItem<O>> roots = getTopLevelAncestors(path);
        for(TopLevelDirItem<O> root: roots) {
            Path relPath = root.getValue().relativize(path);
            root.addDirectory(relPath, origin);
        }
        if(!roots.isEmpty()) {
            dirWatcher.watchOrLogError(path);
        }
    }

    private void handleDeletion(Path path, O origin) {
        for(TopLevelDirItem<O> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.remove(relPath, origin);
        }
    }

    private void handleModification(Path path, O origin) {
        try {
            FileTime timestamp = Files.getLastModifiedTime(path);
            updateModificationTime(path, timestamp, origin);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    private void updateModificationTime(Path path, FileTime lastModified, O origin) {
        for(TopLevelDirItem<O> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.updateModificationTime(relPath, lastModified, origin);
        }
    }

    private void sync(PathNode tree) {
        Path path = tree.getPath();
        for(TopLevelDirItem<O> root: getTopLevelAncestors(path)) {
            root.sync(tree, originExternal);
        }
        watchTree(tree);
    }

    private void watchTree(PathNode tree) {
        if(tree.isDirectory()) {
            dirWatcher.watchOrLogError(tree.getPath());
            for(PathNode child: tree.getChildren()) {
                watchTree(child);
            }
        }
    }

    private List<TopLevelDirItem<O>> getTopLevelAncestorsNonEmpty(Path path) {
        List<TopLevelDirItem<O>> roots = getTopLevelAncestors(path);
        assert !roots.isEmpty() : "path resolved against a dir that was reported to be in the model does not have a top-level ancestor in the model";
        return roots;
    }

    private List<TopLevelDirItem<O>> getTopLevelAncestors(Path path) {
        return Arrays.asList(
                root.getChildren().stream()
                .filter(item -> path.startsWith(item.getValue()))
                .map(item -> (TopLevelDirItem<O>) item)
                .toArray(i -> new TopLevelDirItem[i]));
    }

    private boolean isInModel(Path path) {
        return root.getChildren().stream()
                .anyMatch(item -> path.startsWith(item.getValue()));
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
