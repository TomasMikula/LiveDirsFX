package org.fxmisc.livedirs;

import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;
import java.util.List;

import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.reactfx.EventSource;
import org.reactfx.EventStream;
import org.reactfx.EventStreams;

public class DirectoryModel<O> {

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
            isDir ? new ImageView(FOLDER_IMAGE) : new ImageView(FILE_IMAGE);

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

    public DirectoryModel(O defaultOrigin) {
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

    public TreeItem<Path> getRoot() { return root; }
    public EventStream<Update<O>> creations() { return creations; }
    public EventStream<Update<O>> deletions() { return deletions; }
    public EventStream<Update<O>> modifications() { return modifications; }
    public EventStream<Update<O>> updates() { return updates; }
    public EventStream<Throwable> errors() { return errors; }

    public void setGraphicFactory(GraphicFactory factory) {
        graphicFactory = factory != null ? factory : DEFAULT_GRAPHIC_FACTORY;
    }

    public boolean contains(Path path) {
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