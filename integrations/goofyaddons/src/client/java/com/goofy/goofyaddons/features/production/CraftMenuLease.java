package com.goofy.goofyaddons.features.production;

import java.util.List;
import java.util.function.Predicate;

/** Keeps one transaction's menu until it yields; delegates have priority over every parent job. */
final class CraftMenuLease<T> {
    private T owner;
    T select(List<T> jobs,Predicate<T> wantsMenu,boolean delegated) {
        if(delegated)return null;
        if(owner!=null && jobs.contains(owner) && wantsMenu.test(owner))return owner;
        owner=jobs.stream().filter(wantsMenu).findFirst().orElse(null);return owner;
    }
    void clear(){owner=null;}
}
