package astar.client;

import com.goofy.goofyaddons.features.access.HubPathfinder;
import com.goofy.goofyaddons.features.FeatureManager;
import com.goofy.goofyaddons.utils.Chat;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.core.Direction;
import java.util.*;

/** Loaded NPC discovery: never guesses another island's coordinates or owns a user's manual route. */
final class TradingPathfinder implements HubPathfinder {
    private final AstarClient client;
    private Navigator owned;
    private final Set<BlockPos> attemptedGoals=new HashSet<>();
    TradingPathfinder(AstarClient client){this.client=client;}
    boolean owns(){return owned!=null && client.navigator()==owned;}
    public boolean active(){return owns() && owned.state()!=Navigator.State.DONE;}
    public boolean goTo(String landmark) {
        if(!"BAZAAR".equals(landmark))return false;
        if(active())return true;
        var mc=Minecraft.getInstance();if(mc.player==null || mc.level==null)return false;
        var npc=discover(mc);if(npc==null)return false;
        var goals=new ArrayList<BlockPos>();
        for(int dx=-2;dx<=2;dx++)for(int dz=-2;dz<=2;dz++)if(dx*dx+dz*dz<=4 && dx*dx+dz*dz>=1) {
            var p=npc.blockPosition().offset(dx,0,dz);
            if(mc.level.getBlockState(p).getCollisionShape(mc.level,p).isEmpty()
                && mc.level.getBlockState(p.above()).getCollisionShape(mc.level,p.above()).isEmpty()
                && !mc.level.getBlockState(p.below()).getCollisionShape(mc.level,p.below()).isEmpty()
                && mc.level.getBlockState(p.below()).getCollisionShape(mc.level,p.below()).max(Direction.Axis.Y)>=1
                && !attemptedGoals.contains(p)
                && mc.level.clip(new net.minecraft.world.level.ClipContext(
                    new net.minecraft.world.phys.Vec3(p.getX()+.5,p.getY()+1.62,p.getZ()+.5),npc.position().add(0,.9,0),
                    net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,mc.player))
                    .getType()==net.minecraft.world.phys.HitResult.Type.MISS)
                goals.add(p);
        }
        goals.sort(Comparator.comparingDouble(p->mc.player.distanceToSqr(p.getX()+.5,p.getY(),p.getZ()+.5)));
        if(goals.isEmpty() || !client.beginTradingRoute(goals.getFirst()))return false;
        attemptedGoals.add(goals.getFirst());
        owned=client.navigator();return true;
    }
    public void stop(){if(owns())owned.cancel(Minecraft.getInstance(),"trader navigation stopped");owned=null;attemptedGoals.clear();}
    void beforeTick(Minecraft mc) {
        if(!owns())return;
        if(owned.state()==Navigator.State.PAUSED) {FeatureManager.INSTANCE.pause();stop();return;}
        // Server menus must never race movement input. The trader observes the menu on its next tick.
        if(mc.gui.screen()!=null)stop();
    }
    private static Entity discover(Minecraft mc) {
        var candidates=new ArrayList<Entity>();var labels=new ArrayList<Entity>();
        for(var e:mc.level.entitiesForRendering())if(e!=mc.player && Chat.strip(e.getDisplayName().getString()).matches("(?:\\[NPC\\] )?Bazaar")) {
            if(e instanceof ArmorStand)labels.add(e);else candidates.add(e);
        }
        for(var label:labels)for(var e:mc.level.entitiesForRendering())
            if(e instanceof Player && e!=mc.player && e.getUUID().version()==2 && e.distanceToSqr(label)<9)candidates.add(e);
        return candidates.stream().min(Comparator.comparingDouble(e->e.distanceToSqr(mc.player))).orElse(null);
    }
}
