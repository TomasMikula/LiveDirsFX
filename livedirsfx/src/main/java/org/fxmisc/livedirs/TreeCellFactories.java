package org.fxmisc.livedirs;

import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeView;
import javafx.util.Callback;

import java.util.function.Function;

/**
 * A class that provides cell factories to help display a {@link PathItem}
 * as though it extended a subclass of {@link javafx.scene.control.TreeItem}
 * or to provide additional functionality not present in the normal cell factory.
 */
public class TreeCellFactories {

    public static <C extends CheckBoxContent> Callback<TreeView<C>, TreeCell<C>> checkBoxFactory() {
        return (view) -> new CheckBoxTreeCell();
    }

    public static <C extends CheckBoxContent> Callback<TreeView<C>, TreeCell<C>> checkBoxFactory(
            Function<CheckBoxContent, String> stringConverter
    ) {
        return (view) -> new CheckBoxTreeCell(stringConverter);
    }

}
