package com.goofy.goofyaddons.features.generalflipper;

import com.goofy.goofyaddons.features.CapitalManager;
import com.goofy.goofyaddons.menu.GameActions;
import com.goofy.goofyaddons.menu.MenuSnapshot;
import com.google.gson.JsonObject;

import java.util.List;

/**
 * What a single general-trading operation may see and do.
 *
 * <p>The engine implements this. Operations receive the tick's one menu observation and
 * the engine's checkpoint, failure and transition rules through it, so moving a step into
 * its own class cannot quietly change how it observes, persists or fails.
 */
interface GeneralContext {
    GeneralPosition active();
    GeneralTrade trade();
    GeneralClaim claim();
    List<GeneralPosition> positions();

    MenuSnapshot view();
    long now();
    String username();
    GameActions actions();
    GeneralFlipper.Services services();
    CapitalManager capital();
    GeneralSettings settings();

    GeneralFlipper.Step step();
    long stepSince();
    void transition(GeneralFlipper.Step next);
    void click(int slot);
    void command(String text);
    void fail(String message);
    boolean save();

    boolean menu(String title);
    boolean priceMenu();
    boolean loadedSlot(int slot);
    int find(String text, boolean exact);
    String lore(int slot);
    double unitPrice(int slot);
    int itemCount(String id);
    int capacityFor(String id);

    boolean ordersReady();
    boolean ambiguousOrders();
    int findOrder(boolean sell);
    boolean orderMatchesPosition(int slot);
    boolean recheckOrders(String reason);

    boolean freshQuotes();
    double currentAsk();
    JsonObject products();
    boolean shouldReprice(boolean sell);
    boolean purchasePurseReady(double purse);
    boolean saleAllowed(double price);

    void restoreFunding(GeneralPosition position);
    boolean recordAcquisition();
    boolean recordSale(int units, Double proceeds);
    void skipUnavailable(String reason);
    void finishWork();
    void completePosition();
}
