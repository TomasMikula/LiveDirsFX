package org.fxmisc.livedirs;

import org.reactfx.value.Var;

import java.nio.file.Path;

/**
 * A basic implementation of {@link CheckBoxContent}
 */
public class CheckBoxContentImpl implements CheckBoxContent {

    private final Var<State> state = Var.newSimpleVar(State.UNCHECKED);
    public final State getState() { return state.getValue(); }
    public final void setState(State value) { state.setValue(value); }
    public final Var<State> stateProperty() { return state; }

    private Path path;
    public final Path getPath() { return path; }
    public final void setPath(Path p) { path = p; }

    public CheckBoxContentImpl(Path p) {
        path = p;
    }
}
