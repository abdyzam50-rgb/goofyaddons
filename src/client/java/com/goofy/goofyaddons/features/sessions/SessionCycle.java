package com.goofy.goofyaddons.features.sessions;

/** Only scheduled rests authorize reconnects. Manual stops and unexpected disconnects cancel them. */
public final class SessionCycle {
    public enum State {DISARMED, ACTIVE, FINISHING, RESTING, CONNECTING, JOINING, RETURNING, BLOCKED}
    public interface Port {
        boolean connected(); boolean connecting(); boolean ready(); boolean transactionBoundary(); boolean blocked();
        void stop(); void disconnect(); void connect(); void command(String command); void start(); void block(String reason);
    }
    private State state=State.DISARMED;
    private long since,nextAttempt,readySince;
    private int attempts,commands;
    private String reason="";
    public State state(){return state;}
    public String reason(){return reason;}
    public boolean armed(){return state!=State.DISARMED && state!=State.BLOCKED;}
    public void arm(long now) {state=State.ACTIVE;since=now;attempts=0;reason="";}
    public void cancel(String message){state=State.DISARMED;reason=message;}
    private void transition(State next,long now){state=next;since=now;readySince=0;}
    private void fail(Port port,String message){state=State.BLOCKED;reason=message;port.block(message);}
    /** True means the normal trading tick must be skipped. */
    public boolean tick(boolean online,long now,RestScheduleSettings settings,Port port) {
        if(!armed())return false;
        if(port.blocked()){fail(port,"Schedule cancelled by a trading safety block");return true;}
        switch(state) {
            case ACTIVE -> {
                if(!port.connected()){fail(port,"Unexpected disconnect; reconnect manually before rearming the schedule");return true;}
                if(online)return false;
                transition(State.FINISHING,now);
            }
            case FINISHING -> {if(online){transition(State.ACTIVE,now);return false;}}
            case RESTING -> {
                if(port.connected()){cancel("Manual connection during rest; schedule cancelled");return true;}
                if(!online || now<nextAttempt)return true;
                attempts=0;transition(State.CONNECTING,now);attempts++;port.connect();return true;
            }
            case CONNECTING -> {
                if(port.connected()) {
                    if(!online){port.stop();transition(State.RESTING,now);port.disconnect();return true;}
                    commands=0;transition(State.JOINING,now);return true;
                }
                if(now-since<settings.connectionTimeoutSeconds*1000L)return true;
                if(port.connecting()){fail(port,"Connection timed out; cancel the connection and reconnect manually");return true;}
                if(!online){transition(State.RESTING,now);return true;}
                if(attempts>=settings.reconnectAttempts){fail(port,"Scheduled reconnect attempts exhausted");return true;}
                if(now-since<(settings.connectionTimeoutSeconds+settings.reconnectDelaySeconds)*1000L)return true;
                attempts++;since=now;port.connect();return true;
            }
            case JOINING,RETURNING -> {
                if(!port.connected()){fail(port,"Disconnected during scheduled login; reconnect manually");return true;}
                if(!online){port.stop();transition(State.RESTING,now);port.disconnect();return true;}
                if(now-since>settings.connectionTimeoutSeconds*1000L){fail(port,"Scheduled login did not reach a readable SkyBlock world");return true;}
                if(now-since<8000)return true;
                if(state==State.JOINING) {
                    if(port.ready()){transition(State.RETURNING,now);port.command(settings.resumeCommand);return true;}
                    if(commands<3 && now-since>=8000+commands*15000L){commands++;port.command(settings.joinCommand);}
                    return true;
                }
                if(!port.ready()){readySince=0;return true;}
                if(readySince==0)readySince=now;
                if(now-readySince<3000)return true;
                port.start();transition(State.ACTIVE,now);return true;
            }
            default -> {return false;}
        }
        if(state==State.FINISHING) {
            if(!port.connected()){fail(port,"Disconnected before scheduled logout completed");return true;}
            if(port.transactionBoundary()) {
                port.stop();transition(State.RESTING,now);nextAttempt=now+30000;port.disconnect();return true;
            }
            if(now-since>settings.transactionWaitSeconds*1000L){fail(port,"Scheduled rest could not reach a verified transaction boundary");return true;}
            return false; // Continue the one transaction already in progress.
        }
        return true;
    }
}
