package com.goofy.goofyaddons.utils;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PurseParserTest {
    @Test void exactFormattedAmountIsParsed() {
        assertEquals(80000000,PurseParser.parse("§6Purse: §f80,000,000"));
        assertEquals(1234.5,PurseParser.parse(" Purse: 1,234.5 coins "));
        assertEquals(0,PurseParser.parse("Purse: 0"));
    }
    @Test void additionalNumbersAbbreviationsAndMalformedGroupingAreUnavailable() {
        for(String text:new String[]{"Purse: 80,000,000 (+123)","Purse: 80m","Purse: 12,34","Purse: -1","Purse: 1.2.3","Purse: NaN","Purse: 1,000 purse: 999","Piggy: 100","Loading..."})
            assertEquals(-1,PurseParser.parse(text),text);
        assertEquals(-1,PurseParser.parse(null));
    }
}
