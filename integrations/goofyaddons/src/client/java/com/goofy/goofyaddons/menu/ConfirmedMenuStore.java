package com.goofy.goofyaddons.menu;

import java.util.*;

/** Packet-fed menu state: local click predictions never mutate this store. */
public final class ConfirmedMenuStore {
    private int container=-1;
    private List<SlotView> slots=List.of();
    private SlotView cursor;
    private long observation;
    public void clear(){container=-1;slots=List.of();cursor=null;observation++;}
    public void content(int id,List<SlotView> actual,SlotView carried){container=id;slots=List.copyOf(actual);cursor=carried;observation++;}
    public void slot(int id,int index,SlotView actual){
        if(id!=container || index<0 || index>=slots.size())return;
        var updated=new ArrayList<>(slots);updated.set(index,actual);slots=List.copyOf(updated);observation++;
    }
    public void inventory(int index,SlotView actual){
        var updated=new ArrayList<>(slots);boolean changed=false;
        for(int i=0;i<slots.size();i++){var old=slots.get(i);if(old.inPlayerInventory() && old.containerSlot()==index){
            updated.set(i,new SlotView(old.index(),true,index,actual.empty(),actual.customName(),actual.hoverName(),actual.loreLines(),actual.customId(),actual.enchantments(),actual.count(),actual.maxStackSize(),actual.metadata()));changed=true;}}
        if(changed){slots=List.copyOf(updated);observation++;}
    }
    public void cursor(SlotView actual){cursor=actual;observation++;}
    public MenuSnapshot read(int id,String title){
        return id!=container || slots.isEmpty() || cursor==null?null:new MenuSnapshot(container,title,cursor.empty(),slots,cursor,observation);
    }
}
