package com.goofy.goofyaddons.features.production;

import com.goofy.goofyaddons.menu.*;
import com.goofy.goofyaddons.utils.Chat;
import java.util.*;
import java.util.regex.Pattern;

/** Reads an exact sack balance through observed controls; never clicks a withdrawal icon. */
final class CompactorSacks {
    private static final Pattern STORED=Pattern.compile("^Stored: ([0-9][0-9,]*)(?:/[0-9][0-9,]*)?(?: items)?$");
    private final String product,name;
    private int step,balance=-1,pages;
    private long started,next;
    private String failure;
    private boolean found,triedBazaar,lookingBazaar;
    private String awaitedSack;
    private MenuSnapshot pageSent,stable;
    private final Set<String> visited=new HashSet<>();
    CompactorSacks(String product,String name){this.product=product;this.name=name;}
    int balance(){return balance;}
    boolean found(){return found;}
    String failure(){return failure;}
    static Integer stored(SlotView slot) {
        Integer value=null;
        for(String line:slot.loreLines()) {
            var match=STORED.matcher(Chat.strip(line));if(!match.matches())continue;
            try {int n=Integer.parseInt(match.group(1).replace(",",""));if(value!=null&&!value.equals(n))return null;value=n;}
            catch(NumberFormatException invalid){return null;}
        }
        return value;
    }
    static Integer bazaarBalance(MenuSnapshot menu,String product,String name) {
        if(menu==null||menu.title()==null||!Chat.strip(menu.title()).startsWith("Bazaar"))return null;
        var aliases=new HashSet<>(List.of(name,RecipeCatalog.instance().name(product)));
        aliases.add(name.replace(" Ingot",""));aliases.add(RecipeCatalog.instance().name(product).replace(" Ingot",""));
        for(var s:menu.slots())if(!s.inPlayerInventory()&&!s.empty()&&"Sell Sacks Now".equals(Chat.strip(s.hoverName()))
                &&s.hasLoreLine("Instantly sell everything in your")&&s.hasLoreLine("sacks on the Bazaar.")) {
            Integer amount=null;
            for(String raw:s.loreLines()) {
                String line=Chat.strip(raw).trim();
                if(Set.of("Your sacks are empty!","You don't have any items in your sacks!").contains(line))return 0;
                var match=Pattern.compile("^([0-9][0-9,]*)x (.+) for [0-9][0-9,.]* coins$").matcher(line);
                if(!match.matches()||!aliases.contains(match.group(2)))continue;
                try{int n=Integer.parseInt(match.group(1).replace(",",""));if(amount!=null&&!amount.equals(n))return null;amount=n;}
                catch(NumberFormatException invalid){return null;}
            }
            if(amount!=null)return amount;
        }
        return null;
    }

    ProductionLoop.Step tick(MenuSnapshot menu,GameActions actions,boolean owns,long now) {
        if(balance>=0)return ProductionLoop.Step.DONE;
        if(failure!=null)return ProductionLoop.Step.UNCERTAIN;
        if(started==0)started=now;
        if(now-started>20000)return fail("Sack balance could not be read; open the enchanted sack and export its GUI");
        if(!owns||now<next||menu==null||menu.loadingContents())return ProductionLoop.Step.PENDING;
        if(!menu.cursorEmpty())return ProductionLoop.Step.PENDING;
        if(step==0) {
            if(menu.title()!=null){actions.closeMenu();next=now+150;return ProductionLoop.Step.PENDING;}
            lookingBazaar=!triedBazaar;triedBazaar=true;actions.command(lookingBazaar?"bz":"sacks");pages=0;step=1;next=now+150;return ProductionLoop.Step.PENDING;
        }
        String title=Chat.strip(menu.title());if(title==null)return ProductionLoop.Step.PENDING;
        if(awaitedSack!=null) {
            if(title.equals("Sack of Sacks")||title.startsWith("Sack of Sacks ("))return ProductionLoop.Step.PENDING;
            awaitedSack=null;
        }
        if(pageSent!=null) {
            if(pageSent.slots().equals(menu.slots()))return ProductionLoop.Step.PENDING;
            pageSent=null;
        }
        if(stable==null||!stable.title().equals(menu.title())||!stable.slots().equals(menu.slots())) {
            stable=menu;next=now+300;return ProductionLoop.Step.PENDING;
        }
        if(lookingBazaar) {
            Integer n=bazaarBalance(menu,product,name);
            if(n!=null){found=true;balance=n;return ProductionLoop.Step.DONE;}
            lookingBazaar=false;actions.closeMenu();step=0;next=now+150;stable=null;return ProductionLoop.Step.PENDING;
        }
        if(title.contains("Enchanted")&&title.endsWith("Sack")) {
            var item=menu.slots().stream().filter(s->!s.inPlayerInventory()&&!s.empty() && (product.equals(s.customId())
                ||name.equals(Chat.strip(s.hoverName()))||name.replace(" Ingot","").equals(Chat.strip(s.hoverName())))).findFirst().orElse(null);
            if(item!=null) {
                Integer n=stored(item);if(n==null)return fail("Exact "+name+" sack count is unreadable; no sack items withdrawn");
                found=true;balance=n;return ProductionLoop.Step.DONE;
            }
            actions.closeMenu();step=0;next=now+150;return ProductionLoop.Step.PENDING;
        }
        if(!title.equals("Sack of Sacks")&&!title.startsWith("Sack of Sacks ("))return fail("Unexpected menu while reading enchanted sacks");
        var candidates=menu.slots().stream().filter(s->!s.inPlayerInventory()&&!s.empty()).filter(s->{
            String label=Chat.strip(s.hoverName());return label!=null&&label.contains("Enchanted")&&label.endsWith("Sack");
        }).toList();
        for(var sack:candidates) {
            String key=pages+":"+Chat.strip(sack.hoverName());if(!visited.add(key))continue;
            actions.click(sack.index(),false);awaitedSack=Chat.strip(sack.hoverName());stable=null;next=now+300;step=2;return ProductionLoop.Step.PENDING;
        }
        var nextPage=menu.slots().stream().filter(s->!s.inPlayerInventory()&&!s.empty()&&"Next Page".equals(Chat.strip(s.hoverName()))).findFirst().orElse(null);
        if(nextPage!=null&&pages<4){actions.click(nextPage.index(),false);pageSent=menu;stable=null;pages++;next=now+300;return ProductionLoop.Step.PENDING;}
        if(step==1||step==2){balance=0;return ProductionLoop.Step.DONE;}
        return ProductionLoop.Step.PENDING;
    }
    private ProductionLoop.Step fail(String reason){failure=reason;return ProductionLoop.Step.UNCERTAIN;}
}
