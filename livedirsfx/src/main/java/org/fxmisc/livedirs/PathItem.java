package org.fxmisc.livedirs;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.Function;

import javafx.collections.ObservableList;
import javafx.scene.Node;
import javafx.scene.control.TreeItem;

import org.fxmisc.livedirs.DirectoryModel.GraphicFactory;

abstract class PathItem<T> extends TreeItem<T> {

    private final Function<T, Path> projector;
    protected final Function<T, Path> getProjector() { return projector; }
    public final Path getPath() { return projector.apply(getValue()); }

    protected PathItem(T path, Node graphic, Function<T, Path> projector) {
        super(path, graphic);
        this.projector = projector;
    }

    @Override
    public final boolean isLeaf() {
        return !isDirectory();
    }

    public abstract boolean isDirectory();

    public FileItem<T> asFileItem() { return (FileItem<T>) this; }
    public DirItem<T> asDirItem() { return (DirItem<T>) this; }

    public PathItem<T> getRelChild(Path relPath) {
        assert relPath.getNameCount() == 1;
        Path childValue = getPath().resolve(relPath);
        for(TreeItem<T> ch: getChildren()) {
            PathItem<T> pathCh = (PathItem<T>) ch;
            if(pathCh.getPath().equals(childValue)) {
                return pathCh;
            }
        }
        return null;
    }

    protected PathItem<T> resolve(Path relPath) {
        int len = relPath.getNameCount();
        if(len == 0) {
            return this;
        } else {
            PathItem<T> child = getRelChild(relPath.getName(0));
            if(child == null) {
                return null;
            } else if(len == 1) {
                return child;
            } else {
                return child.resolve(relPath.subpath(1, len));
            }
        }
    }
}

class FileItem<T> extends PathItem<T> {
    public static <T> FileItem<T> create(T path, FileTime lastModified, GraphicFactory graphicFactory, Function<T, Path> projector) {
        return new FileItem<>(path, lastModified, graphicFactory.createGraphic(projector.apply(path), false), projector);
    }

    private FileTime lastModified;

    private FileItem(T path, FileTime lastModified, Node graphic, Function<T, Path> projector) {
        super(path, graphic, projector);
        this.lastModified = lastModified;
    }

    @Override
    public final boolean isDirectory() {
        return false;
    }

    public boolean updateModificationTime(FileTime lastModified) {
        if(lastModified.compareTo(this.lastModified) > 0) {
            this.lastModified = lastModified;
            return true;
        } else {
            return false;
        }
    }
}

class DirItem<T> extends PathItem<T> {

    private final Function<Path, T> injector;
    protected final Function<Path, T> getInjector() { return injector; }
    public final T inject(Path path) { return injector.apply(path); }

    public static <T> DirItem<T> create(T path, GraphicFactory graphicFactory, Function<T, Path> projector, Function<Path, T> injector) {
        return new DirItem<>(path, graphicFactory.createGraphic(projector.apply(path), true), projector, injector);
    }

    protected DirItem(T path, Node graphic, Function<T, Path> projector, Function<Path, T> injector) {
        super(path, graphic, projector);
        this.injector = injector;
    }

    @Override
    public final boolean isDirectory() {
        return true;
    }

    public FileItem<T> addChildFile(Path fileName, FileTime lastModified, GraphicFactory graphicFactory) {
        assert fileName.getNameCount() == 1;
        int i = getFileInsertionIndex(fileName.toString());

        FileItem<T> child = FileItem.create(inject(getPath().resolve(fileName)), lastModified, graphicFactory, getProjector());
        getChildren().add(i, child);
        return child;
    }

    public DirItem<T> addChildDir(Path dirName, GraphicFactory graphicFactory) {
        assert dirName.getNameCount() == 1;
        int i = getDirInsertionIndex(dirName.toString());

        DirItem<T> child = DirItem.create(inject(getPath().resolve(dirName)), graphicFactory, getProjector(), getInjector());
        getChildren().add(i, child);
        return child;
    }

    private int getFileInsertionIndex(String fileName) {
        ObservableList<TreeItem<T>> children = getChildren();
        int n = children.size();
        for(int i = 0; i < n; ++i) {
            PathItem<T> child = (PathItem<T>) children.get(i);
            if(!child.isDirectory()) {
                String childName = child.getPath().getFileName().toString();
                if(childName.compareToIgnoreCase(fileName) > 0) {
                    return i;
                }
            }
        }
        return n;
    }

    private int getDirInsertionIndex(String dirName) {
        ObservableList<TreeItem<T>> children = getChildren();
        int n = children.size();
        for(int i = 0; i < n; ++i) {
            PathItem<T> child = (PathItem<T>) children.get(i);
            if(child.isDirectory()) {
                String childName = child.getPath().getFileName().toString();
                if(childName.compareToIgnoreCase(dirName) > 0) {
                    return i;
                }
            } else {
                return i;
            }
        }
        return n;
    }
}

class ParentChild<T> {
    private final DirItem<T> parent;
    private final PathItem<T> child;

    public ParentChild(DirItem<T> parent, PathItem<T> child) {
        this.parent = parent;
        this.child = child;
    }

    public DirItem<T> getParent() { return parent; }
    public PathItem<T> getChild() { return child; }
}

interface Reporter<I> {
    void reportCreation(Path baseDir, Path relPath, I initiator);
    void reportDeletion(Path baseDir, Path relPath, I initiator);
    void reportModification(Path baseDir, Path relPath, I initiator);
    void reportError(Throwable error);
}

class TopLevelDirItem<I, T> extends DirItem<T> {
    private final GraphicFactory graphicFactory;
    private final Reporter<I> reporter;

    TopLevelDirItem(T path, GraphicFactory graphicFactory, Function<T, Path> projector, Function<Path, T> injector, Reporter<I> reporter) {
        super(path, graphicFactory.createGraphic(projector.apply(path), true), projector, injector);
        this.graphicFactory = graphicFactory;
        this.reporter = reporter;
    }

    private ParentChild<T> resolveInParent(Path relPath) {
        int len = relPath.getNameCount();
        if(len == 0) {
            return new ParentChild<>(null, this);
        } else if(len == 1) {
            if(getPath().resolve(relPath).equals(getValue())) {
                return new ParentChild<>(null, this);
            } else {
                return new ParentChild<>(this, getRelChild(relPath.getName(0)));
            }
        } else {
            PathItem<T> parent = resolve(relPath.subpath(0, len - 1));
            if(parent == null || !parent.isDirectory()) {
                return new ParentChild<>(null, null);
            } else {
                PathItem<T> child = parent.getRelChild(relPath.getFileName());
                return new ParentChild<>(parent.asDirItem(), child);
            }
        }
    }

    private void updateFile(Path relPath, FileTime lastModified, I initiator) {
        PathItem<T> item = resolve(relPath);
        if(item == null || item.isDirectory()) {
            sync(PathNode.file(getPath().resolve(relPath), lastModified), initiator);
        }
    }

    public boolean contains(Path relPath) {
        return resolve(relPath) != null;
    }

    public void addFile(Path relPath, FileTime lastModified, I initiator) {
        updateFile(relPath, lastModified, initiator);
    }

    public void updateModificationTime(Path relPath, FileTime lastModified, I initiator) {
        updateFile(relPath, lastModified, initiator);
    }

    public void addDirectory(Path relPath, I initiator) {
        PathItem<T> item = resolve(relPath);
        if(item == null || !item.isDirectory()) {
            sync(PathNode.directory(getPath().resolve(relPath), Collections.emptyList()), initiator);
        }
    }

    public void sync(PathNode tree, I initiator) {
        Path path = tree.getPath();
        Path relPath = getPath().relativize(path);
        ParentChild<T> pc = resolveInParent(relPath);
        DirItem<T> parent = pc.getParent();
        PathItem<T> item = pc.getChild();
        if(parent != null) {
            syncChild(parent, relPath.getFileName(), tree, initiator);
        } else if(item == null) { // neither path nor its parent present in model
            raise(new NoSuchElementException("Parent directory for " + relPath + " does not exist within " + getValue()));
        } else { // resolved to top-level dir
            assert item == this;
            if(tree.isDirectory()) {
                syncContent(this, tree, initiator);
            } else {
                raise(new IllegalArgumentException("Cannot replace top-level directory " + getValue() + " with a file"));
            }
        }
    }

    private void syncContent(DirItem<T> dir, PathNode tree, I initiator) {
        Set<Path> desiredChildren = new HashSet<>();
        for(PathNode ch: tree.getChildren()) {
            desiredChildren.add(ch.getPath());
        }

        ArrayList<TreeItem<T>> actualChildren = new ArrayList<>(dir.getChildren());

        // remove undesired children
        for(TreeItem<T> ch: actualChildren) {
            if(!desiredChildren.contains(getProjector().apply(ch.getValue()))) {
                removeNode(ch, null);
            }
        }

        // synchronize desired children
        for(PathNode ch: tree.getChildren()) {
            sync(ch, initiator);
        }
    }

    private void syncChild(DirItem<T> parent, Path childName, PathNode tree, I initiator) {
        PathItem<T> child = parent.getRelChild(childName);
        if(child != null && child.isDirectory() != tree.isDirectory()) {
            removeNode(child, null);
        }
        if(child == null) {
            if(tree.isDirectory()) {
                DirItem<T> dirChild = parent.addChildDir(childName, graphicFactory);
                reporter.reportCreation(getPath(), getPath().relativize(dirChild.getPath()), initiator);
                syncContent(dirChild, tree, initiator);
            } else {
                FileItem<T> fileChild = parent.addChildFile(childName, tree.getLastModified(), graphicFactory);
                reporter.reportCreation(getPath(), getPath().relativize(fileChild.getPath()), initiator);
            }
        } else {
            if(child.isDirectory()) {
                syncContent(child.asDirItem(), tree, initiator);
            } else {
                if(child.asFileItem().updateModificationTime(tree.getLastModified())) {
                    reporter.reportModification(getPath(), getPath().relativize(child.getPath()), initiator);
                }
            }
        }
    }

    public void remove(Path relPath, I initiator) {
        PathItem<T> item = resolve(relPath);
        if(item != null) {
            removeNode(item, initiator);
        }
    }

    private void removeNode(TreeItem<T> node, I initiator) {
        signalDeletionRecursively(node, initiator);
        node.getParent().getChildren().remove(node);
    }

    private void signalDeletionRecursively(TreeItem<T> node, I initiator) {
        for(TreeItem<T> child: node.getChildren()) {
            signalDeletionRecursively(child, initiator);
        }
        reporter.reportDeletion(getPath(), getPath().relativize(getProjector().apply(node.getValue())), initiator);
    }

    private void raise(Throwable t) {
        try {
            throw t;
        } catch(Throwable e) {
            reporter.reportError(e);
        }
    }
}