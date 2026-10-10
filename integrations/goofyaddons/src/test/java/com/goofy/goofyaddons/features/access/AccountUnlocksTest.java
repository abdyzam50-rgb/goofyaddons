package com.goofy.goofyaddons.features.access;

import com.google.gson.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import static org.junit.jupiter.api.Assertions.*;

class AccountUnlocksTest {
    @Test void publicAccountServiceConnectionRetriesAfterFiveSecondsAndRecovers() {
        var clock=new AtomicLong(1000000);var calls=new AtomicLong();
        var account=new AccountUnlocks(uri->calls.incrementAndGet()==1
            ?CompletableFuture.failedFuture(new java.util.concurrent.CompletionException(new java.net.ConnectException("private network details")))
            :CompletableFuture.completedFuture(new AccountUnlocks.Response(200,profile(clock.get()))),Runnable::run,clock::get);
        account.poll("Tester","Mango",Map.of(),clock.get());
        assertEquals("ACCOUNT_SERVICE_UNREACHABLE",account.diagnosticState(clock.get()).get("failureCode"));
        assertFalse(account.status(clock.get()).contains("private network details"));assertTrue(account.current(clock.get()).isEmpty());
        clock.addAndGet(4999);account.poll("Tester","Mango",Map.of(),clock.get());assertEquals(1,calls.get());
        clock.incrementAndGet();account.poll("Tester","Mango",Map.of(),clock.get());
        assertEquals(2,calls.get());assertEquals(4,account.current(clock.get()).get("goldingot"));
    }
    @Test void repeatedConnectionFailuresBackOffAndDoNotPublishAssumedUnlocks() {
        var clock=new AtomicLong(1000000);
        var account=new AccountUnlocks(uri->CompletableFuture.failedFuture(new java.net.http.HttpTimeoutException("secret")),Runnable::run,clock::get);
        for(long delay:new long[]{5000,15000,30000,60000,60000}) {
            account.poll("Tester","Mango",Map.of(),clock.get());
            assertEquals(delay,account.diagnosticState(clock.get()).get("retryInMs"));
            assertEquals("PROFILE_TIMEOUT",account.diagnosticState(clock.get()).get("failureCode"));assertTrue(account.current(clock.get()).isEmpty());
            clock.addAndGet(delay);
        }
    }
    @Test void malformedResponsesKeepNormalRetryIntervalAndExplainTheResponseFailure() {
        var account=new AccountUnlocks(uri->CompletableFuture.failedFuture(new JsonSyntaxException("private body")),Runnable::run,()->1000000);
        account.poll("Tester","Mango",Map.of(),1000000);
        assertEquals("INVALID_RESPONSE",account.diagnosticState(1000000).get("failureCode"));
        assertEquals(60000L,account.diagnosticState(1000000).get("retryInMs"));assertFalse(account.status(1000000).contains("private body"));
    }
    @Test void prerequisitesUseThePublicWebsiteWithoutAnyLocalCalculatorRequest() {
        var calls=new ArrayList<java.net.URI>();var account=new AccountUnlocks(uri->{calls.add(uri);return CompletableFuture.completedFuture(new AccountUnlocks.Response(200,profile(1000000)));},Runnable::run,()->1000000);
        account.poll("Tester","Mango",Map.of(),1000000);
        assertEquals(List.of(java.net.URI.create("https://goofy-gameplay-collector.abdyzam50.workers.dev/v1/profiles?username=Tester")),calls);
        assertEquals("PUBLIC_WEBSITE",account.diagnosticState(1000000).get("source"));
        assertNull(RouteRequirements.craft("Gold Ingot IV",account.skills(1000000),account.current(1000000)));
        account.poll("Tester","Mango",Map.of(),1001000);assertEquals(1,calls.size(),"Use fresh verified data rather than polling on every craft tick");
    }
    @Test void enderPearlSixIsVerifiedDirectlyAndMissingOrWrongProfileStillBlocksIt() {
        var data=profile(1000000);data.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonObject("stats").getAsJsonObject("collectionIds").addProperty("ENDER_PEARL",6);
        var account=new AccountUnlocks(uri->CompletableFuture.completedFuture(new AccountUnlocks.Response(200,data)),Runnable::run,()->1000000);
        account.poll("Tester","Mango",Map.of(),1000000);assertNull(RouteRequirements.craft("Ender Pearl VI",account.skills(1000000),account.current(1000000)));
        assertTrue(account.current(1300000).isEmpty(),"Expired unlocks must not authorize new purchases");
        account.poll("Tester","Apple",Map.of(),1000000);assertNotNull(RouteRequirements.craft("Ender Pearl VI",account.skills(1000000),account.current(1000000)));
        assertEquals("INVALID_PROFILE",account.diagnosticState(1000000).get("failureCode"));
    }
    JsonObject profile(long at) {
        var root=JsonParser.parseString("""
          {"protocol":"goofy-profile/1","username":"Tester","profiles":[
           {"name":"Mango","selected":true,"unknown":["Heart of the Mountain"],"stats":{
            "skills":{"Enchanting":33},"collections":{},"collectionIds":{"GOLD_INGOT":4},"slayers":{},"reputation":{},"hotmTier":0}}]}
          """).getAsJsonObject();root.addProperty("fetchedAt",at);return root;
    }
    @Test void deniedHypixelAccessIsVisibleAndRetryCanRecoverWithoutOpeningMenus() {
        var clock=new AtomicLong(1000000);var responses=new ArrayDeque<CompletableFuture<AccountUnlocks.Response>>();
        responses.add(CompletableFuture.completedFuture(new AccountUnlocks.Response(502,JsonParser.parseString("""
          {"failureCode":"HYPIXEL_FORBIDDEN","error":"private-api-secret must not be displayed"}
          """).getAsJsonObject())));
        responses.add(CompletableFuture.completedFuture(new AccountUnlocks.Response(200,profile(1060000))));
        var account=new AccountUnlocks(uri->responses.remove(),Runnable::run,clock::get);
        account.poll("Tester","Mango",Map.of(),clock.get());
        assertFalse(account.pending());assertTrue(account.current(clock.get()).isEmpty());assertTrue(account.status(clock.get()).contains("HTTP 403"));
        assertEquals("HYPIXEL_FORBIDDEN",account.diagnosticState(clock.get()).get("failureCode"));
        assertFalse(account.diagnosticState(clock.get()).toString().contains("private-api-secret"));
        clock.set(1060000);account.poll("Tester","Mango",Map.of(),clock.get());
        assertFalse(account.pending());assertEquals(4,account.current(clock.get()).get("goldingot"));
        assertNull(RouteRequirements.craft("Gold Ingot IV",account.skills(clock.get()),account.current(clock.get())));
        assertEquals("VERIFIED",account.diagnosticState(clock.get()).get("failureCode"));
        clock.addAndGet(300000);assertTrue(account.current(clock.get()).isEmpty());
    }
    @Test void switchingProfilesDiscardsPendingResultsAndRequiresNewProof() {
        var requests=new ArrayList<CompletableFuture<AccountUnlocks.Response>>();
        var account=new AccountUnlocks(uri->{var f=new CompletableFuture<AccountUnlocks.Response>();requests.add(f);return f;},Runnable::run,()->1000000);
        account.poll("Tester","Mango",Map.of(),1000000);
        assertTrue(account.pending());account.poll("Tester","Apple",Map.of(),1000001);
        assertTrue(requests.getFirst().isCancelled());
        requests.get(1).complete(new AccountUnlocks.Response(200,profile(1000000)));
        assertTrue(account.current(1000001).isEmpty());assertEquals("INVALID_PROFILE",account.diagnosticState(1000001).get("failureCode"));
        assertTrue(account.status(1000001).contains("profile is unverified"));
    }
    @Test void noProfileAndUnpublishedCollectionsAreExplainedWithoutAssumingAnUnlock() {
        var data=profile(1000000);data.getAsJsonArray("profiles").get(0).getAsJsonObject().getAsJsonArray("unknown").add("collections");
        var account=new AccountUnlocks(uri->CompletableFuture.completedFuture(new AccountUnlocks.Response(200,data)),Runnable::run,()->1000000);
        account.poll("Tester",null,Map.of(),1000000);
        assertTrue(account.status(1000000).contains("Waiting"));assertFalse(account.pending());
        account.poll("Tester","Mango",Map.of(),1000000);
        assertTrue(account.current(1000000).isEmpty());assertTrue(account.status(1000000).contains("Enable Collection API"));
    }
}
