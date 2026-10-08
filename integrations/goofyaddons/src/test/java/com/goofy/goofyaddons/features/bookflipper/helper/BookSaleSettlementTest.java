package com.goofy.goofyaddons.features.bookflipper.helper;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.goofy.goofyaddons.features.bookflipper.helper.BookSaleSettlement.Result.*;

class BookSaleSettlementTest {
    @Test void matchingReceiptAndDisappearanceFinishWithoutFurtherRechecks() {
        assertEquals(COMPLETE,BookSaleSettlement.check(false,false,true,500,10000));
        assertEquals(COMPLETE,BookSaleSettlement.check(false,false,true,10001,10000));
    }
    @Test void missingOrderWithoutReceiptWaitsThenPreservesOwnership() {
        assertEquals(WAITING,BookSaleSettlement.check(false,false,false,9999,10000));
        assertEquals(UNCONFIRMED,BookSaleSettlement.check(false,false,false,10000,10000));
    }
    @Test void delayedOrderPacketsCannotCompleteOrAuthorizeAnotherClaim() {
        assertEquals(WAITING,BookSaleSettlement.check(true,false,true,500,10000));
        assertEquals(UNCONFIRMED,BookSaleSettlement.check(true,false,true,10000,10000));
    }
    @Test void anInventoryBookStillPresentKeepsTheSaleUnsettled() {
        assertEquals(WAITING,BookSaleSettlement.check(false,true,true,500,10000));
        assertEquals(UNCONFIRMED,BookSaleSettlement.check(false,true,true,10000,10000));
        assertEquals(UNCONFIRMED,BookSaleSettlement.check(false,false,false,-1,10000));
    }
}
