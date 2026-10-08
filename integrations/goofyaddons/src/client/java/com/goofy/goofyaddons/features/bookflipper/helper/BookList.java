package com.goofy.goofyaddons.features.bookflipper.helper;

public class BookList {
    public final Book book;
    public final int level;
    public int location;
    /** Inventory slot or storage menu slot; -1 until confirmed by an observation. */
    public int slot = -1;
    /** Found in observed holdings without a purchase; adopt with zero additional acquisition cost. */
    public boolean found;

    public BookList(Book book, int level, int location) {
        this.book = book;
        this.level = level;
        this.location = location;
    }
}
