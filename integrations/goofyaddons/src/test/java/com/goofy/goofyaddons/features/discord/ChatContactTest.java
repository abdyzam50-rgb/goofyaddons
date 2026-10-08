package com.goofy.goofyaddons.features.discord;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ChatContactTest {
    @Test void actualContactAndDirectMessagesAreClassified() {
        assertEquals("staff",ChatContact.kind("[GM] Example: Are you there?","Owner"));
        assertEquals("mention",ChatContact.kind("[MVP+] Bob: hello owner","Owner"));
        assertEquals("mention",ChatContact.kind("From Bob: hello","Owner"));
    }
    @Test void quotedStaffRanksSubstringNamesAndOwnMessagesDoNotTriggerAlerts() {
        assertNull(ChatContact.kind("Bob: I saw an [ADMIN] today","Owner"));
        assertNull(ChatContact.kind("Bob: ownership matters","Owner"));
        assertNull(ChatContact.kind("[MVP+] Owner: hello Owner","Owner"));
        assertNull(ChatContact.kind("To Bob: I am Owner","Owner"));
        assertNull(ChatContact.kind("[GoofyAddons] Profit: Owner","Owner"));
    }
}
