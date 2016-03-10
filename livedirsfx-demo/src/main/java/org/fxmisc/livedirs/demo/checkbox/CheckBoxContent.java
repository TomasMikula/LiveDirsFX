/**
 * Created 2016 by Jordan Martinez
 *
 * The author dedicates this to the public domain
 */

package org.fxmisc.livedirs.demo.checkbox;

import org.reactfx.value.Var;

import java.nio.file.Path;

public interface CheckBoxContent {

    /** The State of the {@link javafx.scene.control.CheckBox} */
    enum State {
        UNCHECKED,
        UNDEFINED,
        CHECKED
    }

    State getState();
    void setState(State value);
    Var<State> stateProperty();

    Path getPath();
    void setPath(Path p);

    void lock();
    void unlock();
    boolean isLocked();

}
