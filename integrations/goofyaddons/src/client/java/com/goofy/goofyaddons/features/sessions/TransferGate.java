package com.goofy.goofyaddons.features.sessions;

/** Only a continuous readable world authorizes restart; contact blocks and manual stops win. */
public final class TransferGate {
    public enum Result { WAITING, RESUME, CANCELLED, TIMED_OUT }
    private long since,readySince=-1;
    private boolean pending;
    public boolean pending(){return pending;}
    public void begin(long now){if(!pending){pending=true;since=now;}readySince=-1;}
    public void cancel(){pending=false;readySince=-1;}
    public Result observe(boolean running,boolean blocked,boolean ready,long now) {
        if(!running || blocked){cancel();return Result.CANCELLED;}
        if(now-since>120000){cancel();return Result.TIMED_OUT;}
        if(!ready){readySince=-1;return Result.WAITING;}
        if(readySince<0)readySince=now;
        if(now-readySince<5000)return Result.WAITING;
        cancel();return Result.RESUME;
    }
}
