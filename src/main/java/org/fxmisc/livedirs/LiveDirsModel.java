package org.fxmisc.livedirs;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

import javafx.scene.control.TreeItem;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

class LiveDirsModel<O> implements DirectoryModel<O> {

    private final TreeItem<Path> root = new TreeItem<>();
    private final EventSource<Update<O>> creations = new EventSource<>();
    private final EventSource<Update<O>> deletions = new EventSource<>();
    private final EventSource<Update<O>> modifications = new EventSource<>();
    private final EventStream<Update<O>> updates = EventStreams.merge(
            creations, deletions, modifications);
    private final EventSource<Throwable> errors = new EventSource<>();
    private final Reporter<O> reporter;
    private final O defaultOrigin;

    private GraphicFactory graphicFactory = DEFAULT_GRAPHIC_FACTORY;

    public LiveDirsModel(O defaultOrigin) {
        this.defaultOrigin = defaultOrigin;
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
                errors.push(error);
            }
        };
    }

    @Override public TreeItem<Path> getRoot() { return root; }
    @Override public EventStream<Update<O>> creations() { return creations; }
    @Override public EventStream<Update<O>> deletions() { return deletions; }
    @Override public EventStream<Update<O>> modifications() { return modifications; }
    @Override public EventStream<Update<O>> updates() { return updates; }
    @Override public EventStream<Throwable> errors() { return errors; }

    @Override
    public void setGraphicFactory(GraphicFactory factory) {
        graphicFactory = factory != null ? factory : DEFAULT_GRAPHIC_FACTORY;
    }

    @Override
    public boolean containsPrefixOf(Path path) {
        return root.getChildren().stream()
                .anyMatch(item -> path.startsWith(item.getValue()));
    }

    void addTopLevelDirectory(Path dir) {
        root.getChildren().add(new TopLevelDirItem<>(dir, graphicFactory, reporter));
    }

    void updateModificationTime(Path path, FileTime lastModified, O origin) {
        for(TopLevelDirItem<O> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.updateModificationTime(relPath, lastModified, origin);
        }
    }

    void addDirectory(Path path, O origin) {
        List<TopLevelDirItem<O>> roots = getTopLevelAncestors(path);
        for(TopLevelDirItem<O> root: roots) {
            Path relPath = root.getValue().relativize(path);
            root.addDirectory(relPath, origin);
        }
    }

    void addFile(Path path, O origin, FileTime lastModified) {
        for(TopLevelDirItem<O> root: getTopLevelAncestors(path)) {
            Path relPath = root.getValue().relativize(path);
            root.addFile(relPath, lastModified, origin);
        }
    }

    void delete(Path path, O origin) {
        for(TopLevelDirItem<O> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.remove(relPath, origin);
        }
    }

    void sync(PathNode tree) {
        Path path = tree.getPath();
        for(TopLevelDirItem<O> root: getTopLevelAncestors(path)) {
            root.sync(tree, defaultOrigin);
        }
    }

    private List<TopLevelDirItem<O>> getTopLevelAncestors(Path path) {
        return Arrays.asList(
                root.getChildren().stream()
                .filter(item -> path.startsWith(item.getValue()))
                .map(item -> (TopLevelDirItem<O>) item)
                .toArray(i -> new TopLevelDirItem[i]));
    }

    private List<TopLevelDirItem<O>> getTopLevelAncestorsNonEmpty(Path path) {
        List<TopLevelDirItem<O>> roots = getTopLevelAncestors(path);
        assert !roots.isEmpty() : "path resolved against a dir that was reported to be in the model does not have a top-level ancestor in the model";
        return roots;
    }
}