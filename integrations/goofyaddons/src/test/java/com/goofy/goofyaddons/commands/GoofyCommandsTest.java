package com.goofy.goofyaddons.commands;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;

class GoofyCommandsTest {
    private static CommandDispatcher<FabricClientCommandSource> commands;
    @BeforeAll static void registerActualFeatureCommands() {
        com.goofy.goofyaddons.config.ConfigReload.register();
        com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.register();
        com.goofy.goofyaddons.features.production.ProductionCommands.register();
        com.goofy.goofyaddons.features.production.AuctionCommands.registerCommands();
        com.goofy.goofyaddons.features.profit.ProfitHud.registerCommands();
        com.goofy.goofyaddons.diagnostics.Diagnostics.registerCommands(java.util.Map.of());
        commands=new CommandDispatcher<>();commands.register(GoofyCommands.root());
    }
    @Test void everyFeatureLivesUnderOneNamespace() {
        assertEquals(Set.of("goofyaddon"),commands.getRoot().getChildren().stream().map(n->n.getName()).collect(Collectors.toSet()));
        assertEquals(Set.of("start","stop","toggle","status","mode","reload","debug","profit","schedule","craft","production","auction","profiles"),
            commands.getRoot().getChild("goofyaddon").getChildren().stream().map(n->n.getName()).collect(Collectors.toSet()));
    }
    @Test void realFeatureArgumentsStillParseAndInvalidAmountsFailClosed() {
        for(String suffix:new String[]{"debug export","profit scale 1.5","profit reset","schedule on","schedule off","craft ENCHANTED_COAL 2",
                "production recipes ENCHANTED_COAL","production jobs","auction inspect","auction prepare ENCHANTED_COAL 1000",
                "auction sell ENCHANTED_COAL 1000 50","mode both","toggle","reload","profiles","profiles adopt","profiles setaside","production run ENCHANTED_COAL 2","production run ENCHANTED_COAL 2 5000 100","production forge REFINED_DIAMOND 1","production kat BLUE_WHALE;4","production claim abc123 5000 100","production status","production test ENCHANTED_COAL","production test ENCHANTED_COAL 5000","debug version"}) {
            var parse=commands.parse("goofyaddon "+suffix,null);
            assertFalse(parse.getReader().canRead(),suffix);assertTrue(parse.getExceptions().isEmpty(),suffix);
        }
        assertTrue(commands.parse("goofyaddon auction sell ENCHANTED_COAL -1 50",null).getReader().canRead());
        assertTrue(commands.parse("goofyaddon craft ENCHANTED_COAL 17",null).getReader().canRead());
    }
    @Test void nestedCommandsParticipateInAstarTabCompletion()throws Exception {
        var parse=commands.parse("goofyaddon auction ",null);
        assertEquals(Set.of("inspect","prepare","sell"),commands.getCompletionSuggestions(parse).get().getList().stream().map(s->s.getText()).collect(Collectors.toSet()));
    }
}
