package com.goofy.goofyaddons.features.production;

import com.google.gson.JsonObject;
import com.goofy.goofyaddons.features.generalflipper.GeneralCalculator;

/** Execution strategy shared by recipe, book and ordinary-item selection. */
public enum BazaarStrategy {
    ORDER_OFFER(false,false), INSTANT_OFFER(true,false), ORDER_INSTANT(false,true), INSTANT_INSTANT(true,true);
    public final boolean instantBuy,instantSell;
    BazaarStrategy(boolean buy,boolean sell){instantBuy=buy;instantSell=sell;}
    public String label(){return (instantBuy?"instant":"order")+" → "+(instantSell?"instant":"offer");}
    public Double buy(JsonObject product,int units){
        if(product==null||units<1)return null;
        if(instantBuy)return ProductionPlanner.instantBuyCost(product,units);
        double price=GeneralCalculator.topPrice(product,"sell_summary");return price>0 && units>0?price*units:null;
    }
    public Double sell(JsonObject product,int units){
        if(product==null||units<1)return null;
        if(instantSell)return ProductionPlanner.instantSellValue(product,units);
        double price=GeneralCalculator.topPrice(product,"buy_summary");return price>0 && units>0?price*units:null;
    }
    public static BazaarStrategy of(boolean buy,boolean sell){return buy?(sell?INSTANT_INSTANT:INSTANT_OFFER):(sell?ORDER_INSTANT:ORDER_OFFER);}
}
