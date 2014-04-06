package org.fxmisc.livedirs;

public interface SettableOriginIOFacility extends IOFacility {
    void setOrigin(Object origin);
    Object getOrigin();
}
