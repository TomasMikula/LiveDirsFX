package org.fxmisc.livedirs;


import javafx.application.Platform;
import javafx.scene.control.TreeItem;
import org.reactfx.EventStreams;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.Executor;
import java.util.function.Function;

/**
 * NormalLiveDirs is the standard {@link LiveDirs} used in most
 * {@link javafx.scene.control.TreeView} cases. It uses {@link TreeItem} (not
 * one of its subclasses) for its {@link LiveDirsModel}'s root and children.
 *
 * <p>To initialize a {@link javafx.scene.control.TreeView} correctly, use the following code:</p>
 * <pre>
 *     {@code
 *     NormalLiveDirs<?> dirs = // creation code;
 *     TreeView<Path> view = new TreeView(dirs.model().getRoot());
 *     view.setShowRoot(false);
 *     }
 * </pre>
 * @param <I> type of the initiator of I/O actions.
 */
public class NormalLiveDirs<I> extends LiveDirs<Path, I> {

    /**
     * Creates a NormalLiveDirs instance to be used from the JavaFX application
     * thread.
     *
     * @param externalInitiator object to represent an initiator of an external
     * file-system change.
     * @throws IOException
     */
    public NormalLiveDirs(I externalInitiator) throws IOException {
        this(externalInitiator, Platform::runLater);
    }

    /**
     * Creates a NormalLiveDirs instance to be used from a designated thread.
     *
     * @param externalInitiator object to represent an initiator of an external
     * file-system change.
     * @param clientThreadExecutor executor to execute actions on the caller
     * thread. Used to publish updates and errors on the caller thread.
     * @throws IOException
     */
    public NormalLiveDirs(I externalInitiator, Executor clientThreadExecutor) throws IOException {
        super(externalInitiator, Function.identity(), Function.identity(), clientThreadExecutor);
    }
}
