package com.goofy.goofyaddons.features.bookflipper.helper;

/** Existing journal field contract, separated from its file adapter. */
public record BookPosition(Book book, double cost, String tradeId, boolean retiring, long progressAt, boolean orphanCleanup) {
    public BookPosition(Book book,double cost,String tradeId,boolean retiring,long progressAt){this(book,cost,tradeId,retiring,progressAt,false);}
    public BookPosition(Book book,double cost,String tradeId){this(book,cost,tradeId,false,0,false);}
    public BookPosition(Book book, double cost) {this(book,cost,null,false,0,false);}

    /** Validate a complete ownership batch before any reservation or recovery task is adopted. */
    public static java.util.List<BookPosition> validated(java.util.List<BookPosition> positions,long now) {
        if(positions==null)throw new IllegalStateException("Missing book journal");
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (BookPosition position : positions) {
            if (position == null || position.book() == null || position.book().id() == null
                    || !position.book().id().matches("ENCHANTMENT_[A-Z0-9_]+")
                    || position.book().level() < 1 || position.book().sellLevel() <= position.book().level()
                    || position.book().sellLevel() > 10 || position.book().name() == null
                    || position.book().name().isBlank() || !Double.isFinite(position.cost()) || position.cost() <= 0
                    || position.tradeId()!=null && !position.tradeId().matches("[A-Za-z0-9_-]{1,100}")
                    || position.progressAt()<0 || position.progressAt()>now+5000
                    || !ids.add(position.book().id())) throw new IllegalStateException("Invalid book journal");
        }
        return java.util.List.copyOf(positions);
    }
}
