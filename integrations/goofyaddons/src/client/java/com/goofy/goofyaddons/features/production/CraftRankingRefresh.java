package com.goofy.goofyaddons.features.production;

/** Fresh data/config changes bypass periodic polling, including a late first response. */
final class CraftRankingRefresh {
    private long next, source;
    private Object config;
    boolean due(long now,long marketSource,Object settings) {
        return now>=next || marketSource!=source || settings!=config;
    }
    void attempted(long now,long marketSource,Object settings) {
        next=now+20000;source=marketSource;config=settings;
    }
    void clear(){next=0;source=0;config=null;}
}
