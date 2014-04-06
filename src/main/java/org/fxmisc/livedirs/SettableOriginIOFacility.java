package org.fxmisc.livedirs;

/**
 *
 * @param <O> origin type
 */
public interface SettableOriginIOFacility<O> extends IOFacility {
    void setOrigin(O origin);
    O getOrigin();
}
