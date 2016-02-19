package org.fxmisc.livedirs.demo;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.fxmisc.livedirs.LiveDirs;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

public class NormalLiveDirs extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        TreeView<Path> view = new TreeView<>();
        view.setShowRoot(false);

        try {
            // create a LiveDirs instance for use on the JavaFX Application Thread
            LiveDirs<ChangeSource, Path> dirs = LiveDirs.getInstance(ChangeSource.EXTERNAL);

            // set directory to watch
            dirs.addTopLevelDirectory(Paths.get(System.getProperty("user.home"), "Documents").toAbsolutePath());
            view.setRoot(dirs.model().getRoot());

            // stop DirWatcher's thread
            primaryStage.setOnCloseRequest(val -> dirs.dispose());
        } catch (IOException e) {
            e.printStackTrace();
        }

        primaryStage.setScene(new Scene(view, 500, 500));
        primaryStage.show();
    }
}
