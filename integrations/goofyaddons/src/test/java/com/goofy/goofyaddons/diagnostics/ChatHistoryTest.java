package com.goofy.goofyaddons.diagnostics;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatHistoryTest {
    @Test void keepsTheNewestGameLinesAndNeverPlayerChat(){
        var history=new ChatHistory(3);
        history.add("Your inventory is full!",1);
        history.add("From [MVP+] Someone: hi there",2);
        history.add("Party > [VIP] Friend: go",3);
        history.add("Guild > Member: hello",4);
        history.add("[VIP] Stranger: buying stuff",5);
        history.add("[Bazaar] Bought 64x Coal for 320 coins!",6);
        history.add("This recipe is locked!",7);
        history.add("You don't have enough space!",8);
        var lines=history.last(10);
        assertEquals(3,lines.size());
        assertEquals("[Bazaar] Bought 64x Coal for 320 coins!",lines.get(0).text());
        assertEquals("You don't have enough space!",lines.get(2).text());
        assertEquals(1,history.last(1).size());
    }
}
