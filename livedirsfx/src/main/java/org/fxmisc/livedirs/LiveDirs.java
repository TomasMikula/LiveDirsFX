package org.fxmisc.livedirs;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.function.Function;

import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

/**
 * LiveDirs combines a directory watcher, a directory-tree model and a simple
 * I/O facility. The added value of this combination is:
 * <ol>
 *   <li>the directory-tree model is updated automatically to reflect the
 *     current state of the file-system;</li>
 *   <li>the application can distinguish file-system changes made via the
 *     I/O facility from external changes.</li>
 * </ol>
 *
 * <p>The directory model can be used directly as a model for {@link TreeView}.
 *
 * @param <I> type of the initiator of I/O actions.
 * @param <T> type for {@link TreeItem#getValue()}
 */
public class LiveDirs<I, T> {

    /**
     * Creates a LiveDirs instance to be used from the JavaFX application
     * thread.
     *
     * @param externalInitiator object to represent an initiator of an external
     * file-system change.
     * @throws IOException
     */
    public static <I> LiveDirs<I, Path> getInstance(I externalInitiator) throws IOException {
        return getInstance(externalInitiator, Platform::runLater);
    }

    /**
     * Creates a LiveDirs instance to be used from a designated thread.
     *
     * @param externalInitiator object to represent an initiator of an external
     * file-system change.
     * @param clientThreadExecutor executor to execute actions on the caller
     * thread. Used to publish updates and errors on the caller thread.
     * @throws IOException
     */
    public static <I> LiveDirs<I, Path> getInstance(I externalInitiator, Executor clientThreadExecutor) throws IOException {
        return new LiveDirs<>(externalInitiator, Function.identity(), Function.identity(), clientThreadExecutor);
    }

    private final EventSource<Throwable> localErrors = new EventSource<>();
    private final EventStream<Throwable> errors;
    private final Executor clientThreadExecutor;
    private final DirWatcher dirWatcher;
    private final LiveDirsModel<I, T> model;
    private final LiveDirsIO<I> io;
    private final I externalInitiator;

    /**
     * Creates a LiveDirs instance to be used from a designated thread.
     * @param projector converts the ({@link T}) {@link TreeItem#getValue()} into a {@link Path} object
     * @param injector converts a given {@link Path} object into {@link T}. The reverse of {@code projector}
     * @param externalInitiator object to represent an initiator of an external
     * file-system change.
     * @param clientThreadExecutor executor to execute actions on the caller
     * thread. Used to publish updates and errors on the caller thread.
     * @throws IOException
     */
    public LiveDirs(I externalInitiator, Function<T, Path> projector, Function<Path, T> injector, Executor clientThreadExecutor) throws IOException {
        this.externalInitiator = externalInitiator;
        this.clientThreadExecutor = clientThreadExecutor;
        this.dirWatcher = new DirWatcher(clientThreadExecutor);
        this.model = new LiveDirsModel<>(externalInitiator, projector, injector);
        this.io = new LiveDirsIO<>(dirWatcher, model, clientThreadExecutor);

        this.dirWatcher.signalledKeys().subscribe(this::processKey);
        this.errors = EventStreams.merge(dirWatcher.errors(), model.errors(), localErrors);
    }

    /**
     * Stream of asynchronously encountered errors.
     */
    public EventStream<Throwable> errors() { return errors; }

    /**
     * Observable directory model.
     */
    public DirectoryModel<I, T> model() { return model; }

    /**
     * Asynchronous I/O facility. All I/O operations performed by this facility
     * are performed on a single thread. It is the same thread that is used to
     * watch the file-system for changes.
     */
    public InitiatorTrackingIOFacility<I> io() { return io; }

    /**
     * Adds a directory to watch. The directory will be added to the directory
     * model and watched for changes.
     */
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

    /**
     * Used to refresh the given subtree of the directory model in case
     * automatic synchronization failed for any reason.
     *
     * <p>Guarantees given by {@link WatchService} are weak and the behavior
     * may vary on different operating systems. It is possible that the
     * automatic synchronization is not 100% reliable. This method provides a
     * way to request synchronization in case any inconsistencies are observed.
     */
    public CompletionStage<Void> refresh(Path path) {
        return wrap(dirWatcher.getTree(path))
                .thenAcceptAsync(tree -> {
                    model.sync(tree);
                    watchTree(tree);
                }, clientThreadExecutor);
    }

    /**
     * Releases resources used by this LiveDirs instance. In particular, stops
     * the I/O thread (used for I/O operations as well as directory watching).
     */
    public void dispose() {
        dirWatcher.shutdown();
    }

    private void processKey(WatchKey key) {
        Path dir = (Path) key.watchable();
        if(!model.containsPrefixOf(dir)) {
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
                model.delete(dir, externalInitiator);
            }
        }
    }

    private void processEvent(Path dir, WatchEvent<Path> event) {
        // Context for directory entry event is the file name of entry
        Path relChild = event.context();
        Path child = dir.resolve(relChild);

        Kind<Path> kind = event.kind();

        if(kind == ENTRY_MODIFY) {
            handleModification(child, externalInitiator);
        } else if(kind == ENTRY_CREATE) {
            handleCreation(child, externalInitiator);
        } else if(kind == ENTRY_DELETE) {
            model.delete(child, externalInitiator);
        } else {
            throw new AssertionError("unreachable code");
        }
    }

    private void handleCreation(Path path, I initiator) {
        if(Files.isDirectory(path)) {
            handleDirCreation(path, initiator);
        } else {
            handleFileCreation(path, initiator);
        }
    }

    private void handleFileCreation(Path path, I initiator) {
        try {
            FileTime timestamp = Files.getLastModifiedTime(path);
            model.addFile(path, initiator, timestamp);
        } catch (IOException e) {
            localErrors.push(e);
        }
    }

    private void handleDirCreation(Path path, I initiator) {
        if(model.containsPrefixOf(path)) {
            model.addDirectory(path, initiator);
            dirWatcher.watchOrLogError(path);
        }
        refreshOrLogError(path);
    }

    private void handleModification(Path path, I initiator) {
        try {
            FileTime timestamp = Files.getLastModifiedTime(path);
            model.updateModificationTime(path, timestamp, initiator);
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

    private <U> CompletionStage<U> wrap(CompletionStage<U> stage) {
        return new CompletionStageWithDefaultExecutor<>(stage, clientThreadExecutor);
    }
}