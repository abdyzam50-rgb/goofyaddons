package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.menu.*;
import com.google.gson.*;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class GeneralStaleSellTest {
    @TempDir Path dir;
    @Test void holdingExpiryRestoresSellMaintenanceButKeepsCapCooldownAndDrawdownChecks()throws Exception {
        for(int variant=0;variant<4;variant++) {
            GoofyConfig.INSTANCE=new GoofyConfig();var cfg=GoofyConfig.INSTANCE;
            cfg.marketAnalysis.automaticSelection=false;cfg.general.maxActiveItems=1;cfg.general.maxHoldingSeconds=3600;cfg.general.maxReprices=3;cfg.general.repriceCooldownSeconds=60;
            var world=new FakeWorld().username("curedmc");long now=world.clock();
            var position=new GeneralPosition();position.item=new GeneralItem("ENCHANTED_COAL","Enchanted Coal");
            position.quantity=8;position.unitCost=100;position.sellPrice=130;position.stage=GeneralPosition.Stage.SELL_ORDER;
            position.submitted=true;position.purchasePriceKnown=true;position.tradeId="stale-sell";position.reprices=3;
            position.heldSince=now-(variant==1?600:7200)*1000L;position.placedAt=now-(variant==2?1:120)*1000L;
            Path path=dir.resolve("stale-"+variant+".json");
            var repo=new JsonGeneralOrderRepository(()->path);repo.save(List.of(position));
            double ask=variant==3?70:120;
            var services=new ConfiguredTestServices(){
                public double purse(){return 1000000;}
                public JsonObject latestQuotes(){return JsonParser.parseString("{\"lastUpdated\":"+world.clock()+",\"products\":{\"ENCHANTED_COAL\":{\"sell_summary\":[{\"pricePerUnit\":100,\"amount\":100}],\"buy_summary\":[{\"pricePerUnit\":"+ask+",\"amount\":100}]}}}").getAsJsonObject();}
            };
            var slots=new ArrayList<SlotView>();for(int i=0;i<90;i++)slots.add(SlotView.empty(i,i>=54,i>=54?i-54:i));
            slots.set(11,SlotView.named(11,"SELL Enchanted Coal",List.of("Offer amount: 8","Filled: 0/8","Price per unit: 130 coins","By: [MVP+] curedmc","Click to view options!")));
            slots.set(49,SlotView.named(49,"Close",List.of()));world.showing(new MenuSnapshot(10,"Co-op Bazaar Orders",true,slots));
            var actions=new RecordingActions();var engine=new GeneralFlipper(world,actions,repo,services);engine.start();
            for(int i=0;i<60 && !"CANCEL_DETAIL".equals(engine.diagnosticState().get("step"));i++){engine.poll();engine.onTick();world.advance(60);}
            assertEquals(variant==0,actions.serverEffects().contains("click:11"),"variant "+variant+": "+engine.diagnosticState());
            assertFalse(repo.load().getFirst().instantSell);assertFalse(repo.load().getFirst().completed);
            if(variant==3)assertTrue((Boolean)engine.diagnosticState().get("paused"));
        }
    }
}
