package com.goofy.goofyaddons.features.bookflipper.helper;

import com.goofy.goofyaddons.menu.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class BookOutbidFlowTest {
    private final Book book=new Book("ENCHANTMENT_OVERLOAD",1,5,"Overload",0,0);
    private MenuSnapshot menu(int id,String title,int size,SlotView... content) {
        var slots=new ArrayList<SlotView>();
        for(int i=0;i<size;i++) slots.add(SlotView.empty(i,i>=size-36,i));
        slots.set(size-37,SlotView.named(size-37,"Loaded",List.of()));
        for(var slot:content) slots.set(slot.index(),slot);
        return new MenuSnapshot(id,title,true,slots);
    }
    @Test void itemFirstRouteOpensCorrectLevelThenManagementWithoutRepeatClicks() {
        var flow=new BookOutbidFlow();
        assertEquals("bz Overload",flow.navigate(book,null).command());
        var search=menu(2,"Bazaar ➜ \"Overload\"",90,SlotView.named(11,"Overload II",List.of()),
                SlotView.named(10,"Overload I",List.of()));
        assertEquals(10,flow.navigate(book,search).slot());
        assertNull(flow.navigate(book,search));
        var item=menu(3,"Overload ➜ Overload I",72,SlotView.named(28,"Manage Orders",List.of()));
        assertEquals(28,flow.navigate(book,item).slot());
        assertNull(flow.navigate(book,item));
        assertNull(flow.navigate(book,menu(4,"Co-op Bazaar Orders",72)));
        assertNull(flow.navigate(book,menu(5,"Order options",72)));
    }
    @Test void creationControlsNeverPlaceAnAdditionalOrderAndFallbackIsExplicit() {
        var flow=new BookOutbidFlow();
        var item=menu(3,"Overload ➜ Overload I",72,SlotView.named(15,"Create Buy Order",List.of()));
        var decision=flow.navigate(book,item);
        assertEquals("managebazaarorders",decision.command());
        assertTrue(decision.fallback());assertEquals(-1,decision.slot());
        assertNull(flow.navigate(book,item));
        assertEquals("managebazaarorders",flow.navigate(book,null).command());
        flow.reset();assertEquals("bz Overload",flow.navigate(book,null).command());
    }
    @Test void capturedClosedScreenWithInventorySnapshotStillOpensTheItemGui() {
        var flow=new BookOutbidFlow();
        var inventory=menu(0,null,46);
        var decision=flow.navigate(book,inventory);
        assertEquals("bz Overload",decision.command());
        assertEquals(-1,decision.slot());assertFalse(decision.fallback());
        var search=menu(29,"Bazaar ➜ \"Overload\"",90,SlotView.named(10,"Overload I",List.of()));
        assertEquals(10,flow.navigate(book,search).slot());
        assertNull(flow.navigate(book,search));
    }
    @Test void closedInventorySnapshotReopensTheFallbackAndResetRestoresItemFirstEntry() {
        var flow=new BookOutbidFlow();
        assertTrue(flow.navigate(book,menu(30,"Overload ➜ Overload I",72,
                SlotView.named(15,"Create Buy Order",List.of()))).fallback());
        var inventory=menu(0,null,46);
        assertEquals("managebazaarorders",flow.navigate(book,inventory).command());
        flow.reset();
        assertEquals("bz Overload",flow.navigate(book,inventory).command());
    }
    @Test void wrongLevelAndUnloadedItemMenusCannotAuthorizeNavigation() {
        var flow=new BookOutbidFlow();
        assertNull(flow.navigate(book,menu(3,"Overload ➜ Overload II",72,
                SlotView.named(28,"Manage Orders",List.of()))));
        var unloaded=new MenuSnapshot(3,"Overload ➜ Overload I",true,List.of());
        assertNull(flow.navigate(book,unloaded));
    }
    @Test void ambiguousManagementControlsAreNotGuessed() {
        var flow=new BookOutbidFlow();
        var decision=flow.navigate(book,menu(3,"Overload ➜ Overload I",72,
                SlotView.named(28,"Manage Orders",List.of()),SlotView.named(29,"View Orders",List.of())));
        assertTrue(decision.fallback());assertEquals(-1,decision.slot());
    }
    @Test void onlyFreshListAfterOurCancellationMaySkipMissingOrderReopens() {
        var flow=new BookOutbidFlow();
        assertFalse(flow.freshAfterCancellation(20));
        flow.sentCancellation(19,1000);
        assertTrue(flow.cancellationSent());
        assertFalse(flow.freshAfterCancellation(19));
        assertTrue(flow.freshAfterCancellation(20));
        assertFalse(flow.cancellationTimedOut(30999));
        assertTrue(flow.cancellationTimedOut(31000));
        flow.reset();assertFalse(flow.cancellationSent());
        assertFalse(flow.freshAfterCancellation(20));
    }
}
