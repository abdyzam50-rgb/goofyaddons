package com.goofy.goofyaddons.utils;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.jupiter.api.Assertions.*;

class ChatTest {
    @Test void strippingIsIdenticalToTheExpressionItReplaced() {
        List<String> cases = new ArrayList<>(List.of(
                "", "plain text", "§aGreen", "§a§lBold", "BUY Ultimate Wise I", "§6§lConfirm Buy Order",
                "§", "§§", "§a§", "a§", "§\n", "line1\n§aline2", "Filled: §a1§7/§a1",
                "By: §b[MVP§c+§b] Player", "§x§F§F§0§0§0§0gradient", "trailing§"));
        Random random = new Random(20261002);
        char[] alphabet = {'§', 'a', 'l', '\n', ' ', 'x', '7', ' ', '/'};
        for (int i = 0; i < 5000; i++) {
            StringBuilder text = new StringBuilder();
            for (int j = 0, length = random.nextInt(12); j < length; j++) {
                text.append(alphabet[random.nextInt(alphabet.length)]);
            }
            cases.add(text.toString());
        }
        for (String input : cases) {
            // The exact expression Chat.strip replaced across the hot paths.
            assertEquals(input.replaceAll("§.", ""), Chat.strip(input), () -> "input: " + input);
        }
    }

    @Test void aFormattingCodeNeverSwallowsALineBreak() {
        // '.' does not match a line terminator, so "§\n" keeps its newline.
        assertEquals("§\n", Chat.strip("§\n"));
        assertEquals("\n", Chat.strip("§a\n"));
    }

    @Test void missingTextIsEmptyRatherThanNull() {
        assertEquals("", Chat.strip(null));
        assertEquals("", Chat.strip(""));
    }
}
