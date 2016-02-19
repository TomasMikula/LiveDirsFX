package org.fxmisc.livedirs.demo.checkbox;

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

    private boolean locked = false;
    public final boolean isLocked() { return locked; }
    public final void lock() { locked = true; }
    public final void unlock() { locked = false; }

    public CheckBoxContentImpl(Path p) {
        path = p;
    }
}
