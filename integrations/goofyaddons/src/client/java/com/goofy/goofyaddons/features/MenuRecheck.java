package com.goofy.goofyaddons.features;

import java.util.Objects;

/** Retry observations, never the external action that produced them. */
public final class MenuRecheck {
    public enum Decision { WAIT, REOPEN, EXHAUSTED }
    private String operation;
    private long next;
    private int attempts;
    public Decision missing(String key,long now) {
        if(!Objects.equals(operation,key)) {operation=key;attempts=0;next=now+1500;}
        if(now<next) return Decision.WAIT;
        if(attempts>=3) return Decision.EXHAUSTED;
        attempts++;next=now+2500;return Decision.REOPEN;
    }
    public int attempts() {return attempts;}
    public void reset() {operation=null;attempts=0;next=0;}
}
