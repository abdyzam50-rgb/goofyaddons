package com.goofy.goofyaddons.features.marketanalysis;

import com.google.gson.*;
import com.goofy.goofyaddons.features.TradingSafety;
import com.goofy.goofyaddons.features.bookflipper.helper.*;
import com.goofy.goofyaddons.features.generalflipper.*;
import java.util.*;

/** Convert validated, fresh proposals into ordinary trader inputs; never submits a transaction. */
public final class AutomaticSelection {
    private static final JsonObject CATALOG=load();
    private AutomaticSelection() {}
    private static JsonObject load() {
        try(var in=AutomaticSelection.class.getResourceAsStream("/goofyaddons/automatic-products.json")) {
            if(in==null)throw new IllegalStateException("Automatic selection catalog is missing");
            return JsonParser.parseReader(new java.io.InputStreamReader(in,java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
        } catch(java.io.IOException error) {throw new IllegalStateException(error);}
    }
    public static GeneralItem generalItem(String id) {
        if(id==null || id.startsWith("ENCHANTMENT_") || BazaarAccess.MUTATIONS.contains(id))return null;
        var name=CATALOG.getAsJsonObject("products").get(id);
        return name==null?null:new GeneralItem(id,name.getAsString());
    }
    public static Book book(String input,String output) {
        if(input==null || output==null || !input.matches("ENCHANTMENT_[A-Z0-9_]+_[1-9]"))return null;
        String base=input.substring(0,input.lastIndexOf('_'));
        var rules=CATALOG.getAsJsonObject("books").getAsJsonObject(base);
        if(rules==null || !output.matches(java.util.regex.Pattern.quote(base)+"_(?:[2-9]|10)"))return null;
        int from=Integer.parseInt(input.substring(input.lastIndexOf('_')+1));
        int to=Integer.parseInt(output.substring(output.lastIndexOf('_')+1));
        for(var pair:rules.getAsJsonArray("routes"))if(pair.getAsJsonArray().get(0).getAsInt()==from
                && pair.getAsJsonArray().get(1).getAsInt()==to)
            return new Book(base,from,to,rules.get("name").getAsString(),0,0);
        return null;
    }
    public static List<GeneralCalculator.Candidate> general(MarketAnalysisProtocol.Report report,long now,
            JsonObject products,GeneralSettings limits,double tax,double available,int capacity,Set<String> excluded) {
        if(report==null || !TradingSafety.fresh(report.marketAt(),now))return List.of();
        List<GeneralCalculator.Candidate> result=new ArrayList<>();
        for(var r:report.rows()) {
            var item=generalItem(r.inputId());
            if(!r.kind().equals("GENERAL") || !r.configured() || item==null || excluded.contains(item.id()))continue;
            // A narrow settings copy keeps every entry check; the report's chosen batch is an upper bound.
            var scoped=new Gson().fromJson(new Gson().toJson(limits),GeneralSettings.class);
            scoped.items=List.of(item);scoped.maxItemsPerOrder=Math.min(scoped.maxItemsPerOrder,r.batch());
            result.addAll(GeneralCalculator.calculate(products,scoped,tax,available,capacity));
        }
        return List.copyOf(result); // Report order already reflects calibrated coins/hour.
    }
    public static List<FlipItem> books(MarketAnalysisProtocol.Report report,long now,JsonObject products,double tax,double minProfit) {
        return books(report,now,products,tax,minProfit,Map.of());
    }
    public static List<FlipItem> books(MarketAnalysisProtocol.Report report,long now,JsonObject products,double tax,double minProfit,Map<String,Integer> skills) {
        if(report==null || !TradingSafety.fresh(report.marketAt(),now) || products==null)return List.of();
        List<FlipItem> result=new ArrayList<>();
        for(var r:report.rows()) {
            var book=book(r.inputId(),r.outputId());
            if(!r.kind().equals("BOOK") || !r.configured() || book==null || com.goofy.goofyaddons.features.access.RouteRequirements.book(book.id(),skills)!=null)continue;
            result.addAll(FlipCalculator.calculate(products,List.of(book),tax,minProfit));
        }
        return List.copyOf(result);
    }
}
