package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.menu.FakeWorld;
import com.goofy.goofyaddons.menu.RecordingActions;
import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import static org.junit.jupiter.api.Assertions.*;

class GeneralDependencyIsolationTest {
    private static class Services implements GeneralFlipper.Services {
        final CapitalManager capital = new CapitalManager();
        final GeneralSettings settings = new GeneralSettings();
        String pauseReason;
        Services() {capital.configure(10000,0);settings.items=List.of();}
        public CapitalManager capital() {return capital;}
        public GeneralSettings settings() {return settings;}
        public double taxPercentage() {return 1.25;}
        public boolean automaticSelection() {return false;}
        public long actionDelay() {return 50;}
        public double purse() {return 10000;}
        public JsonObject latestQuotes() {return null;}
        public CompletableFuture<JsonObject> fetchQuotes() {return new CompletableFuture<>();}
        public void acquire(GeneralPosition position) {fail("No purchase was confirmed");}
        public void sell(GeneralPosition position,int units,Double proceeds) {fail("No sale was confirmed");}
        public void safetyPause(String reason) {pauseReason=reason;}
    }
    private static class Repository implements GeneralOrderRepository {
        int loads,saves;
        boolean failWrite;
        final GeneralPosition position = position();
        public List<GeneralPosition> load() {loads++;return List.of(position);}
        public void save(List<GeneralPosition> positions) throws Exception {
            saves++;
            if(failWrite)throw new java.io.IOException("Injected persistence failure");
        }
    }
    private static GeneralPosition position() {
        var p=new GeneralPosition();p.item=new GeneralItem("ENCHANTED_COAL","Enchanted Coal");
        p.quantity=16;p.unitCost=100;p.stage=GeneralPosition.Stage.BUY_ORDER;p.submitted=true;
        return p;
    }

    @Test void engineNeedsNoGlobalConfigAndConstructionPerformsNoStorageOrGameEffects() {
        var previous=GoofyConfig.INSTANCE;
        try {
            GoofyConfig.INSTANCE=null;
            var repository=new Repository();var services=new Services();var actions=new RecordingActions();
            var engine=new GeneralFlipper(new FakeWorld().showingNothing(),actions,repository,services);
            assertEquals(0,repository.loads);assertEquals(0,repository.saves);
            engine.start();engine.poll();engine.pause();
            assertEquals(1,repository.loads);assertEquals(1,repository.saves);
            assertTrue(engine.hasRetainedPositions());
            assertEquals(List.of(),actions.serverEffects());
            assertTrue(services.capital.owns("general","ENCHANTED_COAL"));
        } finally {GoofyConfig.INSTANCE=previous;}
    }

    @Test void anInvalidStorageBatchCannotPartiallyAdoptPositionsOrReserveFunds() {
        var valid=position();var invalid=position();invalid.quantity=0;
        var repository=new GeneralOrderRepository() {
            public List<GeneralPosition> load() {return List.of(valid,invalid);}
            public void save(List<GeneralPosition> positions) {fail("Invalid state must not be rewritten");}
        };
        var services=new Services();var actions=new RecordingActions();
        var engine=new GeneralFlipper(new FakeWorld().showingNothing(),actions,repository,services);
        engine.start();
        assertTrue(engine.hasStateError());assertFalse(engine.isRunning());
        assertFalse(engine.hasRetainedPositions());assertEquals(0,services.capital.committed());
        assertEquals(List.of(),actions.serverEffects());
    }

    @Test void oneRepositoriesFailureCannotPauseOrSpendAnotherEnginesBudget() {
        var first=new Repository();var second=new Repository();
        var firstServices=new Services();var secondServices=new Services();
        var firstActions=new RecordingActions();var secondActions=new RecordingActions();
        var a=new GeneralFlipper(new FakeWorld().showingNothing(),firstActions,first,firstServices);
        var b=new GeneralFlipper(new FakeWorld().showingNothing(),secondActions,second,secondServices);
        a.start();b.start();first.failWrite=true;a.pause();
        assertTrue(a.hasStateError());assertNotNull(firstServices.pauseReason);
        assertFalse(b.hasStateError());assertNull(secondServices.pauseReason);assertTrue(b.isRunning());
        assertTrue(b.hasRetainedPositions());assertEquals(1600,secondServices.capital.committed());
        assertEquals(0,second.saves);assertEquals(List.of(),secondActions.serverEffects());
    }
}
