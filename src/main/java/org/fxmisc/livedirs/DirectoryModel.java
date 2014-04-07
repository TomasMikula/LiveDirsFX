package org.fxmisc.livedirs;

import java.nio.file.Path;

import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.fxmisc.livedirs.DirectoryModel.GraphicFactory;
import org.reactfx.EventStream;

public interface DirectoryModel<O> {

    interface GraphicFactory {
        Node create(Path path, boolean isDirectory);
    }

    enum UpdateType {
        CREATION,
        DELETION,
        MODIFICATION,
    }

    class Update<O> {
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

    final GraphicFactory NO_GRAPHIC_FACTORY = (path, isDir) -> null;
    final GraphicFactory DEFAULT_GRAPHIC_FACTORY = new DefaultGraphicFactory();


    TreeItem<Path> getRoot();
    EventStream<Update<O>> creations();
    EventStream<Update<O>> deletions();
    EventStream<Update<O>> modifications();
    EventStream<Update<O>> updates();
    EventStream<Throwable> errors();
    void setGraphicFactory(GraphicFactory factory);
    boolean containsPrefixOf(Path path);
}


class DefaultGraphicFactory implements GraphicFactory {
    private static final Image FOLDER_IMAGE = new Image(DefaultGraphicFactory.class.getResource("folder-16.png").toString());
    private static final Image FILE_IMAGE = new Image(DefaultGraphicFactory.class.getResource("file-16.png").toString());

    @Override
    public Node create(Path path, boolean isDirectory) {
        return isDirectory ? new ImageView(FOLDER_IMAGE) : new ImageView(FILE_IMAGE);
    }
}
