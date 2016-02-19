package org.fxmisc.livedirs;

import javafx.beans.InvalidationListener;
import javafx.scene.Node;
import javafx.scene.control.CheckBox;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import org.reactfx.Subscription;
import org.reactfx.value.Var;

import java.util.function.BiConsumer;
import java.util.function.Function;

public class CheckBoxTreeCell<C extends CheckBoxContent> extends TreeCell<C> {

    private final Recursive<BiConsumer<TreeItem<C>, CheckBoxContent.State>> UPDATE_DOWNWARDS = new Recursive<>();
    {
        UPDATE_DOWNWARDS.f = (item, state) -> {
            item.getValue().setState(state);
            item.getChildren().forEach(child -> UPDATE_DOWNWARDS.f.accept(child, state));
        };
    }

    private final CheckBox checkBox = new CheckBox();
    private final Function<C, String> stringConverter;
    private Var<CheckBoxContent.State> state;
    private Var<Boolean> select;
    private Subscription intermediateState;

    private final InvalidationListener stateInvalidations = (obs) -> {
        TreeItem<C> treeItem = getTreeItem();
        if (treeItem != null) {
            final TreeItem<C> parentItem = treeItem.getParent();

            // do upward call first
            if (parentItem != null) {
                CheckBoxContent value = parentItem.getValue();

                if (value != null && !value.isLocked()) {
                    CheckBoxContent.State[] childrenStates = parentItem.getChildren()
                            .stream().map(v -> v.getValue().getState())
                            .distinct()
                            .toArray(CheckBoxContent.State[]::new);

                    /*
                        Due to `distinct()`,
                            if length > 1,
                                then children were 2+ of the 3 CheckBoxContent.State enum values
                                Thus, set to UNDEFINED
                            else
                                then children were all UNCHECKED or CHECKED.
                                Thus, set the current value to that State
                     */
                    value.setState(childrenStates.length > 1
                            ? CheckBoxContent.State.UNDEFINED
                            : childrenStates[0]
                    );
                }
            }

            // then do downward call
            C itemVal = treeItem.getValue();
            // when children's invalidation listeners are called, skip this item's update as it
            //  was the one the initiated the call.
            itemVal.lock();
            CheckBoxContent.State state = itemVal.getState();
            if (state != CheckBoxContent.State.UNDEFINED) {
                treeItem.getChildren().forEach(child -> UPDATE_DOWNWARDS.f.accept(child, state));
            }
            // once finished, unlock so updates via one of its children will propogate through the tree
            itemVal.unlock();
        }
    };

    public CheckBoxTreeCell() {
        this((content) -> content.getPath().toString());
    }

    public CheckBoxTreeCell(Function<C, String> stringConverter) {
        super();
        this.stringConverter = stringConverter;
    }

    @Override
    protected void updateItem(C item, boolean empty) {
        super.updateItem(item, empty);

        if (empty || item == null) {
            setText(null);
            checkBox.setGraphic(null);
            setGraphic(null);
        } else {
            // update the text
            setText(stringConverter.apply(getItem()));

            // update the graphic
            TreeItem<C> treeItem = getTreeItem();
            Node graphic = treeItem.getGraphic();
            checkBox.setGraphic( graphic != null ? graphic : null);
            setGraphic(checkBox);

            // unbind properties
            if (state != null) {
                checkBox.selectedProperty().unbindBidirectional(select);
                intermediateState.unsubscribe();
                state.removeListener(stateInvalidations);
            }

            // rebind properties
            state = treeItem.getValue().stateProperty();
            select = state.mapBidirectional(
                    s -> s == CheckBoxContent.State.CHECKED,
                    val -> val ? CheckBoxContent.State.CHECKED : CheckBoxContent.State.UNCHECKED
            );
            checkBox.selectedProperty().bindBidirectional(select);

            // using checkBox.intermediateProperty().bind(state.map(s -> s == UNDEFINED)); results in a
            // RunTimeException: a bounded property cannot be set
            // So, get around it by feeding state values into it.
            intermediateState = state.values()
                    .map(s -> s == CheckBoxContent.State.UNDEFINED)
                    .feedTo(checkBox.indeterminateProperty());

            state.addListener(stateInvalidations);
        }
    }
}