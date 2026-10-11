package com.goofy.goofyaddons.features.production;

import java.util.Objects;

/** Capture one long-wait incident per unchanged state; never decide whether an order failed. */
final class ProductionWaitEvidence {
    private String previous;
    private long since;
    private boolean reported;
    boolean due(String signature,long now) {
        if(!Objects.equals(previous,signature)){previous=signature;since=now;reported=false;}
        if(reported||now-since<120000)return false;
        reported=true;return true;
    }
}
