package com.goofy.goofyaddons.features.production;

import java.util.*;

/**
 * Keeps a craft that just blocked before spending from being picked again straight away. A
 * blocked route (compactor not in the hotbar, unreadable price, missing quote) used to be
 * reselected every 5 seconds forever. Each repeat doubles the wait, up to {@link #MAX_MS}.
 */
public final class CraftBlockCooldown {
    static final long FIRST_MS=60_000,MAX_MS=30*60_000;
    /** Repeats after which the route is reported as stuck rather than merely cooling down. */
    public static final int STUCK_AFTER=3;
    public record Entry(int blocks,long until,String reason){}
    private final Map<String,Entry> outputs=new LinkedHashMap<>();

    /** Records a block and returns the updated entry. */
    public Entry blocked(String output,String reason,long now){
        int blocks=outputs.containsKey(output)?outputs.get(output).blocks()+1:1;
        long wait=Math.min(MAX_MS,FIRST_MS<<Math.min(20,blocks-1));
        var entry=new Entry(blocks,now+wait,reason);outputs.put(output,entry);return entry;
    }
    public boolean coolingDown(String output,long now){var e=outputs.get(output);return e!=null && now<e.until();}
    /** A finished run proves the route works again. */
    public void finished(String output){outputs.remove(output);}
    public void clear(){outputs.clear();}
    public Map<String,Object> diagnosticState(long now){
        var state=new LinkedHashMap<String,Object>();
        outputs.forEach((id,e)->state.put(id,Map.of("blocks",e.blocks(),"remainingMs",Math.max(0,e.until()-now),"reason",Objects.toString(e.reason(),""))));
        return state;
    }
}
