package com.goofy.goofyaddons.menu;

/** Bounded refresh of unloaded Bazaar action icons, before any irreversible transaction. */
public final class BedrockMenuRecovery {
    public enum Result { READY, WAITING, REOPEN, EXHAUSTED }
    private String operation;
    private long seenAt=-1,nextReopen;
    private int attempts;
    public static boolean hasPlaceholder(MenuSnapshot menu) {
        if(menu==null || menu.title()==null)return false;
        return menu.slots().stream().anyMatch(s->!s.inPlayerInventory() && !s.empty()
                && "minecraft:bedrock".equals(s.metadata().vanillaId()));
    }
    public void reset(){operation=null;seenAt=-1;nextReopen=0;attempts=0;}
    public Result observe(String key,MenuSnapshot menu,long now) {
        if(!java.util.Objects.equals(operation,key)){reset();operation=key;}
        if(!hasPlaceholder(menu)){seenAt=-1;return Result.READY;}
        if(seenAt<0)seenAt=now;
        // Allow normal population first. Never close a menu while holding an item.
        if(!menu.cursorEmpty() || now-seenAt<1500 || now<nextReopen)return Result.WAITING;
        if(attempts>=3)return Result.EXHAUSTED;
        attempts++;seenAt=-1;nextReopen=now+2500;return Result.REOPEN;
    }
}
