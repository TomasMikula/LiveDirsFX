package org.fxmisc.livedirs;

import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;

import javafx.application.Platform;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

/**
 *
 * @param <I> type of the initiator of I/O actions.
 */
public class LiveDirs<I> {

    private final EventSource<Throwable> localErrors = new EventSource<>();
    private final EventStream<Throwable> errors;
    private final Executor clientThreadExecutor;
    private final DirWatcher dirWatcher;
    private final LiveDirsModel<I> model;
    private final LiveDirsIO<I> io;
    private final I externalInitiator;

    public LiveDirs(I externalInitiator) throws IOException {
        this(externalInitiator, Platform::runLater);
    }

    public LiveDirs(I externalInitiator, Executor clientThreadExecutor) throws IOException {
        this.externalInitiator = externalInitiator;
        this.clientThreadExecutor = clientThreadExecutor;
        this.dirWatcher = new DirWatcher(clientThreadExecutor);
        this.model = new LiveDirsModel<>(externalInitiator);
        this.io = new LiveDirsIO<>(dirWatcher, model, clientThreadExecutor);

        this.dirWatcher.signalledKeys().subscribe(key -> processKey(key));
        this.errors = EventStreams.merge(dirWatcher.errors(), model.errors(), localErrors);
    }

    public EventStream<Throwable> errors() { return errors; }

    public DirectoryModel<I> model() { return model; }

    public InitiatorTrackingIOFacility<I> io() { return io; }

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
                .thenAcceptAsync(tree -> {
                    model.sync(tree);
                    watchTree(tree);
                }, clientThreadExecutor);
    }

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

    private <T> CompletionStage<T> wrap(CompletionStage<T> stage) {
        return new CompletionStageWithDefaultExecutor<>(stage, clientThreadExecutor);
    }
}