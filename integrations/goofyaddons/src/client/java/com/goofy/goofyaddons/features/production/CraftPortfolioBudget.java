package com.goofy.goofyaddons.features.production;

import java.util.List;

/** Two separate limits: committed capital includes held inputs; liquid purse excludes spent coins. */
final class CraftPortfolioBudget {
    record Allocation(String owner,double budget,double spent,double orderCommitment,double buyCommitment) {}
    static double available(double capitalLeft,double liquidLeft,List<Allocation> jobs,String purchasingOwner) {
        if(!Double.isFinite(capitalLeft)||!Double.isFinite(liquidLeft))return 0;
        for(var job:jobs) {
            boolean own=java.util.Objects.equals(job.owner(),purchasingOwner);
            capitalLeft-=Math.max(0,(own?job.spent():Math.max(job.budget(),job.spent()))-job.orderCommitment());
            if(!own)liquidLeft-=Math.max(0,job.budget()-job.spent()-job.buyCommitment());
        }
        return Math.max(0,Math.min(capitalLeft,liquidLeft));
    }
}
