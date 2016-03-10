/**
 * Created 2016 by Jordan Martinez
 *
 * The author dedicates this to the public domain
 */

package org.fxmisc.livedirs.demo;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TreeView;
import javafx.stage.Stage;
import org.fxmisc.livedirs.LiveDirs;
import org.fxmisc.livedirs.demo.checkbox.CheckBoxContentImpl;
import org.fxmisc.livedirs.demo.checkbox.TreeCellFactories;

import java.io.IOException;
import java.nio.file.Paths;

public class CheckBoxLiveDirs extends Application {

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        TreeView<CheckBoxContentImpl> view = new TreeView<>();
        view.setShowRoot(false);
        view.setCellFactory(TreeCellFactories.checkBoxFactory());

        try {
            // create a LiveDirs instance for use on the JavaFX Application Thread
            // and make it display its items as though they were CheckBoxTreeItems
            LiveDirs<ChangeSource, CheckBoxContentImpl> dirs = new LiveDirs<>(ChangeSource.EXTERNAL,
                    CheckBoxContentImpl::getPath, CheckBoxContentImpl::new, Platform::runLater);

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
