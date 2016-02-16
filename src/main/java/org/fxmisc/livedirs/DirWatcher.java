package org.fxmisc.livedirs;

import static java.nio.file.StandardOpenOption.*;
import static java.nio.file.StandardWatchEventKinds.*;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.file.DirectoryStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.reactfx.EventSource;
import org.reactfx.EventStream;

class DirWatcher {
    private final LinkedBlockingQueue<Runnable> executorQueue = new LinkedBlockingQueue<>();
    private final EventSource<WatchKey> signalledKeys = new EventSource<>();
    private final EventSource<Throwable> errors = new EventSource<>();
    private final WatchService watcher;
    private final Thread ioThread;
    private final Executor eventThreadExecutor;

    private volatile boolean shutdown = false;
    private boolean mayInterrupt = false;
    private boolean interrupted = false;

    public DirWatcher(Executor eventThreadExecutor) throws IOException {
        this.watcher = FileSystems.getDefault().newWatchService();
        this.ioThread = new Thread(this::loop, "DirWatchIO");
        this.eventThreadExecutor = eventThreadExecutor;
        this.ioThread.start();
    }

    public EventStream<WatchKey> signalledKeys() {
        return signalledKeys;
    }

    public EventStream<Throwable> errors() {
        return errors;
    }

    public void shutdown() {
        shutdown = true;
        interrupt();
    }

    public void watch(Path dir) throws IOException {
        dir.register(watcher, ENTRY_CREATE, ENTRY_DELETE, ENTRY_MODIFY);
    }

    public void watchOrLogError(Path dir) {
        try {
            watch(dir);
        } catch (IOException e) {
            errors.push(e);
        }
    }

    public CompletionStage<PathNode> getTree(Path root) {
        CompletableFuture<PathNode> res = new CompletableFuture<>();
        executeOnIOThread(() -> {
            try {
                res.complete(PathNode.getTree(root));
            } catch (IOException e) {
                res.completeExceptionally(e);
            }
        });
        return res;
    }

    public void createFile(Path file, Consumer<FileTime> onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(() -> createFile(file), onSuccess, onError);
    }

    public void createDirectory(Path dir, Runnable onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> { Files.createDirectory(dir); return null; },
                none -> onSuccess.run(),
                onError);
    }

    public void saveTextFile(Path file, String content, Charset charset,
            Consumer<FileTime> onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> writeTextFile(file, content, charset),
                onSuccess,
                onError);
    }

    public void saveBinaryFile(Path file, byte[] content,
            Consumer<FileTime> onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> writeBinaryFile(file, content),
                onSuccess,
                onError);
    }

    public void deleteFileOrEmptyDirectory(Path fileOrDir,
            Runnable onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> { Files.deleteIfExists(fileOrDir); return null; },
                NULL -> onSuccess.run(),
                onError);
    }

    public void deleteTree(Path root,
            Runnable onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> {
                    if(Files.exists(root)) {
                        deleteRecursively(root);
                    }
                    return null;
                },
                NULL -> onSuccess.run(),
                onError);
    }

    public void loadBinaryFile(Path file,
            Consumer<byte[]> onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> Files.readAllBytes(file),
                onSuccess,
                onError);
    }

    public void loadTextFile(Path file, Charset charset,
            Consumer<String> onSuccess, Consumer<Throwable> onError) {
        executeIOOperation(
                () -> readTextFile(file, charset),
                onSuccess,
                onError);
    }

    private <T> void executeIOOperation(Callable<T> action,
            Consumer<T> onSuccess, Consumer<Throwable> onError) {
        executeOnIOThread(() -> {
            try {
                T res = action.call();
                executeOnEventThread(() -> onSuccess.accept(res));
            } catch(Throwable t) {
                executeOnEventThread(() -> onError.accept(t));
            }
        });
    }

    private FileTime createFile(Path file) throws IOException {
        Files.createFile(file);
        return Files.getLastModifiedTime(file);
    }

    private void deleteRecursively(Path root) throws IOException {
        if(Files.isDirectory(root)) {
            try(DirectoryStream<Path> stream = Files.newDirectoryStream(root)) {
                for(Path path: stream) {
                    deleteRecursively(path);
                }
            }
        }
        Files.delete(root);
    }

    private FileTime writeBinaryFile(Path file, byte[] content) throws IOException {
        Files.write(file, content, CREATE, WRITE, TRUNCATE_EXISTING);
        return Files.getLastModifiedTime(file);
    }

    private FileTime writeTextFile(Path file, String content, Charset charset) throws IOException {
        byte[] bytes = content.getBytes(charset);
        return writeBinaryFile(file, bytes);
    }

    private String readTextFile(Path file, Charset charset) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        CharBuffer chars = charset.decode(ByteBuffer.wrap(bytes));
        return chars.toString();
    }

    private void executeOnIOThread(Runnable action) {
        executorQueue.add(action);
        interrupt();
    }

    private void executeOnEventThread(Runnable action) {
        eventThreadExecutor.execute(action);
    }

    private synchronized void interrupt() {
        if(mayInterrupt) {
            ioThread.interrupt();
        } else {
            interrupted = true;
        }
    }

    private WatchKey take() throws InterruptedException {
        synchronized(this) {
            if(interrupted) {
                interrupted = false;
                throw new InterruptedException();
            } else {
                mayInterrupt = true;
            }
        }

        try {
            return watcher.take();
        } finally {
            synchronized(this) {
                mayInterrupt = false;
            }
        }
    }

    private WatchKey takeOrNullIfInterrupted() {
        try {
            return take();
        } catch(InterruptedException e) {
            return null;
        }
    }

    private void loop() {
        for(;;) {
            WatchKey key = takeOrNullIfInterrupted();
            if(key != null) {
                emitKey(key);
            } else if(shutdown) {
                try {
                    watcher.close();
                } catch (IOException e) {
                    emitError(e);
                }
                break;
            } else {
                processIOQueues();
            }
        }
    }

    private void emitKey(WatchKey key) {
        executeOnEventThread(() -> signalledKeys.push(key));
    }

    private void emitError(Throwable e) {
        executeOnEventThread(() -> errors.push(e));
    }

    private void processIOQueues() {
        Runnable action;
        while((action = executorQueue.poll()) != null) {
            try {
                action.run();
            } catch(Throwable t) {
                errors.push(t);
            }
        }
    }
}

class PathNode {
    public static PathNode getTree(Path root) throws IOException {
        if(Files.isDirectory(root)) {
            Path[] childPaths;
            try(Stream<Path> dirStream = Files.list(root)) {
                childPaths = dirStream
                        .sorted(PATH_COMPARATOR)
                        .toArray(Path[]::new);
            }
            List<PathNode> children = new ArrayList<>(childPaths.length);
            for(Path p: childPaths) {
                children.add(getTree(p));
            }
            return directory(root, children);
        } else {
            return file(root, Files.getLastModifiedTime(root));
        }
    }

    private static final Comparator<Path> PATH_COMPARATOR = (p, q) -> {
        boolean pd = Files.isDirectory(p);
        boolean qd = Files.isDirectory(q);

        if(pd && !qd) {
            return -1;
        } else if(!pd && qd) {
            return 1;
        } else {
            return p.getFileName().toString().compareToIgnoreCase(q.getFileName().toString());
        }
    };

    static PathNode file(Path path, FileTime lastModified) {
        return new PathNode(path, false, Collections.emptyList(), lastModified);
    }

    static PathNode directory(Path path, List<PathNode> children) {
        return new PathNode(path, true, children, null);
    }

    private final Path path;
    private final boolean isDirectory;
    private final List<PathNode> children;
    private final FileTime lastModified;

    private PathNode(Path path, boolean isDirectory, List<PathNode> children, FileTime lastModified) {
        this.path = path;
        this.isDirectory = isDirectory;
        this.children = children;
        this.lastModified = lastModified;
    }

    public Path getPath() {
        return path;
    }

    public boolean isDirectory() {
        return isDirectory;
    }

    public List<PathNode> getChildren() {
        return children;
    }

    public FileTime getLastModified() {
        return lastModified;
    }
}