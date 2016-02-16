package org.fxmisc.livedirs;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Stream;

import javafx.scene.control.TreeItem;

import org.reactfx.EventSource;
import org.reactfx.EventStream;

class LiveDirsModel<I> implements DirectoryModel<I> {

    private final TreeItem<Path> root = new TreeItem<>();
    private final EventSource<Update<I>> creations = new EventSource<>();
    private final EventSource<Update<I>> deletions = new EventSource<>();
    private final EventSource<Update<I>> modifications = new EventSource<>();
    private final EventSource<Throwable> errors = new EventSource<>();
    private final Reporter<I> reporter;
    private final I defaultInitiator;

    private GraphicFactory graphicFactory = DEFAULT_GRAPHIC_FACTORY;

    public LiveDirsModel(I defaultInitiator) {
        this.defaultInitiator = defaultInitiator;
        this.reporter = new Reporter<I>() {
            @Override
            public void reportCreation(Path baseDir, Path relPath, I initiator) {
                creations.push(Update.creation(baseDir, relPath, initiator));
            }

            @Override
            public void reportDeletion(Path baseDir, Path relPath, I initiator) {
                deletions.push(Update.deletion(baseDir, relPath, initiator));
            }

            @Override
            public void reportModification(Path baseDir, Path relPath, I initiator) {
                modifications.push(Update.modification(baseDir, relPath, initiator));
            }

            @Override
            public void reportError(Throwable error) {
                errors.push(error);
            }
        };
    }

    @Override public TreeItem<Path> getRoot() { return root; }
    @Override public EventStream<Update<I>> creations() { return creations; }
    @Override public EventStream<Update<I>> deletions() { return deletions; }
    @Override public EventStream<Update<I>> modifications() { return modifications; }

    public EventStream<Throwable> errors() { return errors; }

    @Override
    public void setGraphicFactory(GraphicFactory factory) {
        graphicFactory = factory != null ? factory : DEFAULT_GRAPHIC_FACTORY;
    }

    @Override
    public boolean contains(Path path) {
        return topLevelAncestorStream(path).anyMatch(root ->
                root.contains(root.getValue().relativize(path)));
    }

    public boolean containsPrefixOf(Path path) {
        return root.getChildren().stream()
                .anyMatch(item -> path.startsWith(item.getValue()));
    }

    void addTopLevelDirectory(Path dir) {
        root.getChildren().add(new TopLevelDirItem<>(dir, graphicFactory, reporter));
    }

    void updateModificationTime(Path path, FileTime lastModified, I initiator) {
        for(TopLevelDirItem<I> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.updateModificationTime(relPath, lastModified, initiator);
        }
    }

    void addDirectory(Path path, I initiator) {
        topLevelAncestorStream(path).forEach(root -> {
            Path relPath = root.getValue().relativize(path);
            root.addDirectory(relPath, initiator);
        });
    }

    void addFile(Path path, I initiator, FileTime lastModified) {
        topLevelAncestorStream(path).forEach(root -> {
            Path relPath = root.getValue().relativize(path);
            root.addFile(relPath, lastModified, initiator);
        });
    }

    void delete(Path path, I initiator) {
        for(TopLevelDirItem<I> root: getTopLevelAncestorsNonEmpty(path)) {
            Path relPath = root.getValue().relativize(path);
            root.remove(relPath, initiator);
        }
    }

    void sync(PathNode tree) {
        Path path = tree.getPath();
        topLevelAncestorStream(path)
                .forEach(root -> root.sync(tree, defaultInitiator));
    }

    private Stream<TopLevelDirItem<I>> topLevelAncestorStream(Path path) {
        return root.getChildren().stream()
                .filter(item -> path.startsWith(item.getValue()))
                .map(item -> (TopLevelDirItem<I>) item);
    }

    private List<TopLevelDirItem<I>> getTopLevelAncestors(Path path) {
        return Arrays.asList(topLevelAncestorStream(path)
                .<TopLevelDirItem<I>>toArray(TopLevelDirItem[]::new));
    }

    private List<TopLevelDirItem<I>> getTopLevelAncestorsNonEmpty(Path path) {
        List<TopLevelDirItem<I>> roots = getTopLevelAncestors(path);
        assert !roots.isEmpty() : "path resolved against a dir that was reported to be in the model does not have a top-level ancestor in the model";
        return roots;
    }
}