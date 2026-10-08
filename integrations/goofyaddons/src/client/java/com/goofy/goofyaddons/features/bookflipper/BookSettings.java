package com.goofy.goofyaddons.features.bookflipper;

import com.goofy.goofyaddons.features.bookflipper.helper.Book;
import java.util.List;

/** Policy data supplied by the client; it neither reads nor writes global config. */
public record BookSettings(List<Book> books, double bazaarTaxPercentage, double minNetProfit,
        double maxTradingCapital, int maxActiveBooks, String firstPage, String secondPage,
        boolean automaticSelection, boolean liquidateStaleBooks, int bookStaleSeconds,
        int bookOrderRecheckSeconds, int maxBookHoldingSeconds, double maxBookDrawdownPercentage,
        int maxBookReprices, int bookRepriceCooldownSeconds) {
    public BookSettings { books = List.copyOf(books); }
}
