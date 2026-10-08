package com.goofy.goofyaddons.features.discord;

/** Local stops and newer contact always supersede previously queued start/chat commands. */
public final class PlayerCommandPolicy {
    private PlayerCommandPolicy() {}
    public enum Logout { WAITING, DISCONNECT, TIMED_OUT }
    public static Logout logout(long now,long requestedAt,boolean boundary,boolean cursorEmpty) {
        if(now-requestedAt>60000)return Logout.TIMED_OUT;
        return boundary && cursorEmpty?Logout.DISCONNECT:Logout.WAITING;
    }
    public static boolean superseded(String action,long issuedAt,long stoppedAt,long contactAt) {
        return issuedAt<=stoppedAt || java.util.Set.of("start","login","chat").contains(action) && issuedAt<contactAt;
    }
    public static boolean plainChat(String text) {
        return text!=null && !text.isBlank() && text.length()<=256 && !text.matches("(?s).*[\\r\\n\\x00-\\x1f].*")
            && !text.stripLeading().startsWith("/") && !text.stripLeading().startsWith(".");
    }
}
