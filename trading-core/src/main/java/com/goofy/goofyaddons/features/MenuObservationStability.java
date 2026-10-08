package com.goofy.goofyaddons.features;

import java.util.Objects;

/** A loaded preview must remain unchanged in the same container before evaluation. */
public final class MenuObservationStability {
    private int container=-1;
    private String fingerprint;
    private long since;
    public boolean ready(int current,String contents,boolean loaded,long now) {
        if(!loaded) {reset();return false;}
        if(current!=container || !Objects.equals(contents,fingerprint)) {
            container=current;fingerprint=contents;since=now;return false;
        }
        return now>=since && now-since>=750;
    }
    public void reset() {container=-1;fingerprint=null;since=0;}
}
