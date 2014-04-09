package org.fxmisc.livedirs;

import java.nio.file.Path;
import java.util.function.BiFunction;

import javafx.scene.Node;
import javafx.scene.control.TreeItem;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import org.fxmisc.livedirs.DirectoryModel.GraphicFactory;
import org.reactfx.EventStream;

/**
 * Observable model of multiple directory trees.
 * @param <I> type of the initiator of changes to the model.
 */
public interface DirectoryModel<I> {

    @FunctionalInterface
    interface GraphicFactory extends BiFunction<Path, Boolean, Node> {
        Node createGraphic(Path path, boolean isDirectory);

        @Override
        default Node apply(Path path, Boolean isDirectory) {
            return createGraphic(path, isDirectory);
        }
    }

    enum UpdateType {
        CREATION,
        DELETION,
        MODIFICATION,
    }

    class Update<I> {
        static <I> Update<I> creation(Path baseDir, Path relPath, I initiator) {
            return new Update<>(baseDir, relPath, initiator, UpdateType.CREATION);
        }
        static <I> Update<I> deletion(Path baseDir, Path relPath, I initiator) {
            return new Update<>(baseDir, relPath, initiator, UpdateType.DELETION);
        }
        static <I> Update<I> modification(Path baseDir, Path relPath, I initiator) {
            return new Update<>(baseDir, relPath, initiator, UpdateType.MODIFICATION);
        }

        private final Path baseDir;
        private final Path relativePath;
        private final I initiator;
        private final UpdateType type;
        private Update(Path baseDir, Path relPath, I initiator, UpdateType type) {
            this.baseDir = baseDir;
            this.relativePath = relPath;
            this.initiator = initiator;
            this.type = type;
        }
        public I getInitiator() {
            return initiator;
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
    EventStream<Update<I>> creations();
    EventStream<Update<I>> deletions();
    EventStream<Update<I>> modifications();
    EventStream<Update<I>> updates();
    EventStream<Throwable> errors();
    void setGraphicFactory(GraphicFactory factory);
    boolean containsPrefixOf(Path path);
}


class DefaultGraphicFactory implements GraphicFactory {
    private static final Image FOLDER_IMAGE = new Image(DefaultGraphicFactory.class.getResource("folder-16.png").toString());
    private static final Image FILE_IMAGE = new Image(DefaultGraphicFactory.class.getResource("file-16.png").toString());

    @Override
    public Node createGraphic(Path path, boolean isDirectory) {
        return isDirectory ? new ImageView(FOLDER_IMAGE) : new ImageView(FILE_IMAGE);
    }
}
