package com.goofy.goofyaddons.features.marketanalysis;

import com.goofy.goofyaddons.config.GoofyConfig;
import com.goofy.goofyaddons.features.TradingMode;
import com.goofy.goofyaddons.features.TradingSafety;
import com.google.gson.*;
import java.util.*;

/** Plain-data bridge contract; responses are observations and have no execution authority. */
public final class MarketAnalysisProtocol {
    public static final String VERSION = "goofy-bazaar-shadow/1";
    private static final Gson GSON = new Gson();
    private MarketAnalysisProtocol() {}

    public record Recommendation(String kind, String routeKey, String inputId, String outputId,
                                 int inputUnits, int batch, double profitPerBatch, double capitalUsed,
                                 double outputsPerHour, double coinsPerHour, double cycleSeconds,
                                 String confidence, boolean configured, String limitedBy, String priceBasis, Map<String,Object> executionEvidence) {
        public Recommendation(String kind,String routeKey,String inputId,String outputId,int inputUnits,int batch,
                double profitPerBatch,double capitalUsed,double outputsPerHour,double coinsPerHour,double cycleSeconds,
                String confidence,boolean configured,String limitedBy,String priceBasis) {
            this(kind,routeKey,inputId,outputId,inputUnits,batch,profitPerBatch,capitalUsed,outputsPerHour,coinsPerHour,cycleSeconds,
                    confidence,configured,limitedBy,priceBasis,Map.of());
        }
    }
    public record Report(long marketAt, long dataAt, long generatedAt, boolean historyUsed,
                         String historyStatus, String upstreamCommit, int total, Map<String,Integer> counts, List<Recommendation> rows) {}

    public static JsonObject request(String id, JsonObject market, GoofyConfig config, TradingMode mode,
                                     double available, int capacity, int bookSlots, int generalSlots, Set<String> excluded) {
        var root = new JsonObject(); root.addProperty("protocol", VERSION); root.addProperty("requestId", id);
        root.add("market", market);
        var c = new JsonObject();
        c.addProperty("mode", mode.name()); c.addProperty("availableCapital", Math.max(0, available));
        c.addProperty("automaticSelection",config.marketAnalysis.automaticSelection);
        c.addProperty("inventoryCapacity", Math.max(0, capacity));
        c.addProperty("bookSlots", Math.max(0, bookSlots)); c.addProperty("generalSlots", Math.max(0, generalSlots));
        c.addProperty("taxPercentage", config.bazaarTaxPercentage); c.addProperty("bookMinProfit", config.minNetProfit);
        c.addProperty("checkSeconds", config.general.refreshSeconds);
        c.addProperty("bookCheckSeconds", Math.max(config.bookRepriceCooldownSeconds, config.bookOrderRecheckSeconds));
        c.addProperty("clickDelayMs", (config.minActionDelay + config.maxActionDelay) / 2.0);
        c.addProperty("maxRecommendations", config.marketAnalysis.maxRecommendations);
        c.addProperty("maxHistoryAgeHours", config.marketAnalysis.maxHistoryAgeHours);
        c.add("general", GSON.toJsonTree(config.general));
        c.add("excludedProducts", GSON.toJsonTree(new TreeSet<>(excluded)));
        c.add("configuredGeneralItems", GSON.toJsonTree(config.general.items.stream().map(i -> i.id()).toList()));
        c.add("configuredBookRoutes", GSON.toJsonTree(config.books.stream()
                .map(b -> b.id() + ":" + b.level() + ":" + b.sellLevel()).toList()));
        root.add("constraints", c);
        return root;
    }

    public static Report parse(JsonObject response, JsonObject request, long now) {
        if (!VERSION.equals(string(response,"protocol")) || !string(request,"requestId").equals(string(response,"requestId")))
            throw new IllegalArgumentException("Calculator protocol/request mismatch");
        long marketAt = timestamp(response,"marketAt"), generatedAt = timestamp(response,"generatedAt"), dataAt = timestamp(response,"dataAt");
        JsonObject c = request.getAsJsonObject("constraints"), market = request.getAsJsonObject("market");
        if (marketAt != timestamp(market,"lastUpdated") || !TradingSafety.fresh(marketAt, now)
                || !TradingSafety.fresh(generatedAt, now) || dataAt > generatedAt + 5000)
            throw new IllegalArgumentException("Calculator snapshot is stale or does not match the requested market");
        boolean historyUsed = bool(response,"historyUsed");
        String historyStatus = string(response,"historyStatus");
        if (!Set.of("FRESH","STALE","MISSING").contains(historyStatus) || historyUsed != historyStatus.equals("FRESH")
                || historyUsed && (dataAt == 0 || now - dataAt > number(c,"maxHistoryAgeHours") * 3600000))
            throw new IllegalArgumentException("Calculator history status is inconsistent");
        String commit = string(response,"upstreamCommit");
        if (!commit.matches("[a-f0-9]{40}")) throw new IllegalArgumentException("Missing calculator provenance");
        JsonArray raw = response.getAsJsonArray("rows");
        int total = integer(response,"total",0,1_000_000);
        if (raw == null || raw.size() > integer(c,"maxRecommendations",1,50) || raw.size() > total)
            throw new IllegalArgumentException("Calculator row count exceeds request limits");
        List<Recommendation> rows = new ArrayList<>(); Set<String> unique = new HashSet<>();
        Set<String> excluded = strings(c.getAsJsonArray("excludedProducts"));
        double previous = Double.POSITIVE_INFINITY;
        for (JsonElement element : raw) {
            JsonObject r = element.getAsJsonObject();
            String kind = string(r,"kind"), input = string(r,"inputId"), output = string(r,"outputId"), key = string(r,"routeKey");
            JsonObject product = market.getAsJsonObject("products").getAsJsonObject(input);
            if (!Set.of("GENERAL","BOOK").contains(kind) || !input.matches("[A-Z0-9_]+") || !output.matches("[A-Z0-9_]+")
                    || product == null || !market.getAsJsonObject("products").has(output) || !unique.add(kind + ":" + key)
                    || excluded.contains(input) || excluded.contains(output)) throw new IllegalArgumentException("Unsupported or duplicate route");
            int batch = integer(r,"batch",1,4096), n = integer(r,"inputsPerOutput",1,512), units = integer(r,"inputUnits",1,4096);
            if (units != batch * n || units > integer(c,"inventoryCapacity",0,4096)) throw new IllegalArgumentException("Route exceeds inventory capacity");
            double cost = positive(r,"costPerOutput"), buy = positive(r,"buyPrice"), sell = positive(r,"sellPrice");
            double profit = positive(r,"profitPerOutput"), batchProfit = positive(r,"profitPerBatch"), capital = positive(r,"capitalUsed");
            if (!same(cost,buy * n) || !same(profit,sell * (1-number(c,"taxPercentage")/100)-cost) || !same(batchProfit,profit*batch)
                    || capital > number(c,"availableCapital") + 1e-6 || cost * batch > number(c,"availableCapital") + 1e-6)
                throw new IllegalArgumentException("Calculator profit/capital is inconsistent");
            String mode = string(c,"mode"); boolean configured;
            if (kind.equals("GENERAL")) {
                JsonObject general = c.getAsJsonObject("general");
                if (mode.equals("BOOKS") || integer(c,"generalSlots",0,10)==0 || input.startsWith("ENCHANTMENT_") || !input.equals(output) || n!=1
                        || batch > number(general,"maxItemsPerOrder") || cost*batch > number(general,"maxCoinsPerItem") + 1e-6
                        || capital > number(general,"maxCoinsPerItem") + 1e-6 || batchProfit + 1e-6 < number(general,"minProfitPerBatch")
                        || profit/cost*100 + 1e-6 < number(general,"minMarginPercentage") || !key.equals(input))
                    throw new IllegalArgumentException("General route violates execution limits");
                JsonObject quick = product.getAsJsonObject("quick_status");
                if (quick == null || Math.min(number(quick,"sellMovingWeek"),number(quick,"buyMovingWeek")) < number(general,"minWeeklyVolume"))
                    throw new IllegalArgumentException("General route violates volume limits");
                configured = c.has("automaticSelection") && bool(c,"automaticSelection")
                        ? AutomaticSelection.generalItem(input)!=null : strings(c.getAsJsonArray("configuredGeneralItems")).contains(key);
            } else {
                int from = integer(r,"level",1,9), to = integer(r,"sellLevel",2,10);
                if (!input.matches("ENCHANTMENT_[A-Z0-9_]+_[0-9]+")) throw new IllegalArgumentException("Invalid book product");
                String base = input.substring(0, input.lastIndexOf('_'));
                if (mode.equals("GENERAL") || integer(c,"bookSlots",0,10)==0 || !base.startsWith("ENCHANTMENT_") || to <= from
                        || batch!=1 || n != 1 << (to-from) || !input.equals(base+"_"+from) || !output.equals(base+"_"+to)
                        || !key.equals(base+":"+from+":"+to) || excluded.contains(base) || profit + 1e-6 < number(c,"bookMinProfit"))
                    throw new IllegalArgumentException("Book route violates supported combining limits");
                configured = c.has("automaticSelection") && bool(c,"automaticSelection")
                        ? AutomaticSelection.book(input,output)!=null : strings(c.getAsJsonArray("configuredBookRoutes")).contains(key);
            }
            String confidence = string(r,"confidence"), buyBasis = string(r,"buyBasis"), sellBasis = string(r,"sellBasis");
            boolean measured = buyBasis.equals("measured") && sellBasis.equals("measured") && historyUsed;
            if (!Set.of("measured","estimated").contains(buyBasis) || !Set.of("measured","estimated").contains(sellBasis)
                    || !confidence.equals(measured ? "MEASURED" : "ESTIMATED") || bool(r,"configured") != configured)
                throw new IllegalArgumentException("Calculator confidence/configured marker is inconsistent");
            double rate = positive(r,"outputsPerHour"), coins = positive(r,"coinsPerHour"), seconds = positive(r,"cycleSeconds");
            if (!same(coins,rate*profit) || !same(seconds,batch/rate*3600) || coins > previous + 1e-6)
                throw new IllegalArgumentException("Calculator ranking/throughput is inconsistent");
            previous = coins;
            Map<String,Object> evidence=Map.of();
            if(r.has("executionEvidence")) {
                var e=r.getAsJsonObject("executionEvidence");
                int samples=integer(e,"samples",10,2000);double factor=positive(e,"throughputFactor");
                double baseline=positive(e,"marketCycleSeconds"),baselineCoins=positive(e,"marketCoinsPerHour");
                long latest=timestamp(e,"latestAt");
                if(factor<0.5 || factor>1.5 || !same(seconds,baseline/factor) || !same(coins,baselineCoins*factor)
                        || latest>now+5000 || now-latest>86400000)throw new IllegalArgumentException("Invalid personal execution calibration");
                evidence=Map.of("samples",samples,"throughputFactor",factor,"marketCycleSeconds",baseline,
                        "marketCoinsPerHour",baselineCoins,"p75ObservedSeconds",positive(e,"p75ObservedSeconds"),
                        "observedCoinsPerHour",number(e,"observedCoinsPerHour"),"latestAt",latest);
            }
            rows.add(new Recommendation(kind,key,input,output,units,batch,batchProfit,capital,rate,coins,seconds,confidence,configured,
                    string(r,"limitedBy"),string(r,"priceBasis"),evidence));
        }
        Map<String,Integer> counts = new LinkedHashMap<>();
        if (response.has("counts")) {
            JsonObject values = response.getAsJsonObject("counts");
            for (String key : List.of("evaluated","warnings","unsupported","filtered","malformedProducts"))
                if (values.has(key)) counts.put(key,integer(values,key,0,1_000_000));
        }
        return new Report(marketAt,dataAt,generatedAt,historyUsed,historyStatus,commit,total,Map.copyOf(counts),List.copyOf(rows));
    }

    private static Set<String> strings(JsonArray array) {
        if (array == null) throw new IllegalArgumentException("Missing string list");
        Set<String> values = new HashSet<>(); for (var element : array) values.add(element.getAsString()); return values;
    }
    private static boolean same(double a,double b) { return Math.abs(a-b)<=Math.max(1e-6, Math.max(Math.abs(a),Math.abs(b))*1e-9); }
    private static String string(JsonObject o,String key) {
        JsonElement v=o.get(key); if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isString() || v.getAsString().length()>200)
            throw new IllegalArgumentException("Invalid calculator string " + key); return v.getAsString();
    }
    private static boolean bool(JsonObject o,String key) {
        JsonElement v=o.get(key); if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isBoolean()) throw new IllegalArgumentException("Invalid calculator boolean");
        return v.getAsBoolean();
    }
    private static double number(JsonObject o,String key) {
        JsonElement v=o.get(key); if(v==null || !v.isJsonPrimitive() || !v.getAsJsonPrimitive().isNumber() || !Double.isFinite(v.getAsDouble()))
            throw new IllegalArgumentException("Invalid calculator number " + key); return v.getAsDouble();
    }
    private static double positive(JsonObject o,String key) { double n=number(o,key); if(n<=0) throw new IllegalArgumentException("Nonpositive calculator number");return n; }
    private static long timestamp(JsonObject o,String key) {
        double n=number(o,key); if(n<0 || n>9e15 || n!=Math.rint(n)) throw new IllegalArgumentException("Invalid timestamp");return (long)n;
    }
    private static int integer(JsonObject o,String key,int min,int max) {
        double n=number(o,key); if(n<min || n>max || n!=Math.rint(n)) throw new IllegalArgumentException("Invalid integer " + key);return (int)n;
    }
}
