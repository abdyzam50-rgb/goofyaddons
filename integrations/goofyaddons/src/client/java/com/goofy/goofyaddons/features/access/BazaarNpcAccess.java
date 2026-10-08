package com.goofy.goofyaddons.features.access;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.diagnostics.Diagnostics;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.event.ChatHook;
import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.EntityHitResult;
import java.util.*;

/** AUTO uses commands until the server reports a cookie denial. NPC mode never sends /bz. */
public final class BazaarNpcAccess {
    private static final BazaarNavigation navigation=new BazaarNavigation();
    private static HubPathfinder pathfinder;
    private static String recentCommand,serverKey="";private static long recentAt;private static boolean cookieDenied;
    private BazaarNpcAccess() {}
    public static void installPathfinder(HubPathfinder provider){cancel();pathfinder=Objects.requireNonNull(provider);}
    public static void register(){ChatHook.onMessage("Cookie",BazaarNpcAccess::denied);ChatHook.onMessage("cookie",BazaarNpcAccess::denied);}
    public static boolean route(String text) {
        String lower=text.toLowerCase(Locale.ROOT);
        if(!(lower.equals("bz") || lower.equals("bazaar") || lower.startsWith("bz ") || lower.startsWith("bazaar ") || lower.equals("managebazaarorders")))return false;
        String server=Minecraft.getInstance().getCurrentServer()==null?"":Minecraft.getInstance().getCurrentServer().ip;
        if(!server.equals(serverKey)){serverKey=server;cookieDenied=false;}
        String mode=GoofyConfig.INSTANCE.access.bazaarMode;
        if(mode.equals("NPC") || mode.equals("AUTO") && cookieDenied)return navigation.request(text,System.currentTimeMillis());
        recentCommand=text;recentAt=System.currentTimeMillis();return false;
    }
    public static boolean cookieDenial(String text) {
        return Chat.strip(text).trim().matches("(?i)^(?:you (?:must have|need|require)|this command requires).*(?:booster cookie|cookie buff).*");
    }
    private static void denied(String text) {
        if(!GoofyConfig.INSTANCE.access.bazaarMode.equals("AUTO") || recentCommand==null || System.currentTimeMillis()-recentAt>10000 || !cookieDenial(text))return;
        cookieDenied=true;navigation.request(recentCommand,System.currentTimeMillis());recentCommand=null;
    }
    public static void cancel(){navigation.cancel(port);recentCommand=null;}
    public static boolean busy(){return navigation.busy();}
    public static boolean tick() {
        if(!navigation.busy())return false;
        if(Minecraft.getInstance().player==null || Minecraft.getInstance().level==null){cancel();return false;}
        long now=System.currentTimeMillis();navigation.tick(port,now,GoofyConfig.INSTANCE.access.navigationTimeoutSeconds);
        if(navigation.state()==BazaarNavigation.State.FAILED){FeatureManager.INSTANCE.safetyPause(navigation.failure());return true;}
        if(!navigation.busy())FeatureManager.INSTANCE.navigationResumed(navigation.elapsed(now));
        return navigation.busy();
    }
    private static Entity npc() {
        var mc=Minecraft.getInstance();if(mc.level==null || mc.player==null)return null;
        List<Entity> candidates=new ArrayList<>(),labels=new ArrayList<>();
        for(Entity e:mc.level.entitiesForRendering()) {
            if(e==mc.player || e.distanceToSqr(mc.player)>144)continue;
            String name=Chat.strip(e.getDisplayName().getString());
            if(!name.matches("(?:\\[NPC\\] )?Bazaar"))continue;
            if(e instanceof net.minecraft.world.entity.decoration.ArmorStand)labels.add(e);else candidates.add(e);
        }
        // A hologram identifies the landmark, not an interaction target. Pair only with a fake NPC player.
        for(Entity label:labels)for(Entity e:mc.level.entitiesForRendering())
            if(e instanceof Player && e.getUUID().version()==2 && e!=mc.player && e.distanceToSqr(label)<9)candidates.add(e);
        return candidates.stream().filter(e->mc.player.hasLineOfSight(e)).min(Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).orElse(null);
    }
    private static final BazaarNavigation.Port port=new BazaarNavigation.Port() {
        private final LiveWorld world=new LiveWorld();private final LiveActions actions=new LiveActions();
        public MenuSnapshot menu(){return world.menu();}public boolean signOpen(){return world.signEditorOpen();}
        public boolean nearNpc(){Entity e=npc();return e!=null && e.distanceToSqr(Minecraft.getInstance().player)<=9;}
        public boolean walking(){return pathfinder!=null && pathfinder.active();}
        public boolean walk(){return pathfinder!=null && pathfinder.goTo("BAZAAR");}
        public void stopWalking(){if(pathfinder!=null)pathfinder.stop();}
        public void interact(){var mc=Minecraft.getInstance();Entity e=npc();if(e!=null && mc.gameMode!=null && nearNpc())
            mc.gameMode.interact(mc.player,e,new EntityHitResult(e,e.position().add(0,0.9,0)),InteractionHand.MAIN_HAND);}
        public void click(int slot){actions.click(slot,false);}public boolean writeSearch(String text){return actions.writeSign(text);}
    };
}
