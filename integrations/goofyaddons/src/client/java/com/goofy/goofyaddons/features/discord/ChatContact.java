package com.goofy.goofyaddons.features.discord;

/** Alerts classify contact, never claim that a staff rank proves a staff check occurred. */
public final class ChatContact {
    private ChatContact() {}
    public static String kind(String text,String player) {
        if(text==null || player==null || player.isBlank())return null;
        String clean=com.goofy.goofyaddons.utils.Chat.strip(text);
        if(clean.startsWith("To "))return null;
        // Only inspect the sender prefix; quoted ranks in a message are not staff identity.
        int colon=clean.indexOf(':');
        if(colon>0 && clean.substring(0,colon).matches(".*\\[(?:GM|ADMIN|MOD|HELPER)\\].*"))return "staff";
        if(clean.startsWith("From ") && colon>5)return "mention";
        if(!clean.contains(":") || clean.startsWith("[GoofyAddons]"))return null;
        if(java.util.regex.Pattern.compile("(?i)(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(player)+"(?![A-Za-z0-9_])").matcher(clean).find()) {
            String sender=colon<0?"":clean.substring(0,colon);
            // Do not notify for the player's own outgoing messages.
            if(!sender.matches(".*(?i)(?<![A-Za-z0-9_])"+java.util.regex.Pattern.quote(player)+"\\s*"))return "mention";
            if(clean.startsWith("From "))return "mention";
        }
        return null;
    }
}
