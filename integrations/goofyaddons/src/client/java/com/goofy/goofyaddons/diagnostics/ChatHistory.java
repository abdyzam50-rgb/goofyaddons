package com.goofy.goofyaddons.diagnostics;

import java.util.*;

/**
 * The last game messages, so an export shows lines like "Your inventory is full" or "This recipe
 * is locked" next to the events. Only [Bazaar] lines were logged before. Messages from or to
 * other players (private, party, guild and co-op chat) are never kept.
 */
public final class ChatHistory {
    public static final int CAPACITY=200;
    private static final java.util.regex.Pattern PLAYER_CHAT=java.util.regex.Pattern.compile(
        "^(?:From|To) .+?: .*|^(?:Party|Guild|Co-op|Officer) > .*|^\\[\\d+\\] .*|^[^:\\[]*\\[[A-Z+]+\\] [^:]+: .*");
    public record Line(long at,String text){}
    private final ArrayDeque<Line> lines=new ArrayDeque<>();
    private final int capacity;
    public ChatHistory(){this(CAPACITY);}
    ChatHistory(int capacity){this.capacity=capacity;}

    public synchronized void add(String text,long at){
        if(text==null || text.isBlank() || PLAYER_CHAT.matcher(text).matches())return;
        String clean=DiagnosticLog.redact(text);
        if(clean.length()>500)clean=clean.substring(0,500)+"…";
        lines.addLast(new Line(at,clean));
        while(lines.size()>capacity)lines.removeFirst();
    }
    /** Newest last. */
    public synchronized List<Line> last(int count){
        var all=new ArrayList<>(lines);
        return List.copyOf(all.subList(Math.max(0,all.size()-count),all.size()));
    }
}
