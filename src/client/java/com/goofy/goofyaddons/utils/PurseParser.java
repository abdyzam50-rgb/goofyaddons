package com.goofy.goofyaddons.utils;

import java.util.regex.Pattern;

/** Parse one complete amount. Extra numeric annotations and abbreviations are ambiguous. */
public final class PurseParser {
    private static final Pattern LINE=Pattern.compile("^Purse:\\s*((?:\\d{1,3}(?:,\\d{3})+|\\d+)(?:\\.\\d+)?)\\s*(?:coins)?$",Pattern.CASE_INSENSITIVE);
    private PurseParser() {}
    public static double parse(String line) {
        if(line==null) return -1;
        var match=LINE.matcher(line.replaceAll("§.","").replace('\u00a0',' ').strip());
        if(!match.matches()) return -1;
        try {
            double amount=Double.parseDouble(match.group(1).replace(",",""));
            return Double.isFinite(amount) && amount>=0 ? amount : -1;
        } catch(NumberFormatException invalid) { return -1; }
    }
}
