package astar.client;

import astar.client.draw.OverlayColour;
import astar.client.ui.LoadingArt;
import astar.client.ui.MazeBackground;
import astar.client.ui.Panels;
import astar.client.ui.Pixel;
import astar.client.ui.PixelText;
import astar.client.ui.Theme;
import astar.client.ui.Ui;
import astar.pathing.Tuning;
import com.mojang.blaze3d.platform.InputConstants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.resources.Identifier;
import net.minecraft.util.FormattedCharSequence;

/**
 * The mod's window, opened with a key (G unless changed here or in Controls): a sidebar of pages
 * with icons and who's playing at the bottom, a top bar with the page's title, the HUD editor's
 * button and a search over every setting, and each page's cards of settings in two columns. A
 * card folds shut by its title; one with a switch shows its settings only while it's on. Most of
 * it can also be done with {@code .A*}.
 *
 * <p>Drawn entirely by the mod: rounded shapes from the {@code panel} shader ({@link Panels})
 * and pixel fonts for the text, at its own scale of whole screen pixels ({@link #px}), whatever the GUI
 * scale, so it stays sharp. Its controls are its own too ({@link El}), laid out every frame.
 */
final class AstarScreen extends Screen implements com.goofy.goofyaddons.keybinds.BindingCaptureScreen {

    // ---- Pages ----------------------------------------------------------------------------

    static final String ROUTE = "route";
    static final String PATHFINDER = "pathfinder";
    static final String MOVEMENT = "movement";
    static final String MAPS = "maps";
    static final String WIDGETS = "widgets";
    static final String THEMES = "themes";
    static final String MACROS = "macros";
    /** Settings found by what they're called or do, from the box in the top bar. */
    static final String SEARCH = "search";

    /**
     * The page that holds a page of before (drawing, path, camera...) or a section of the
     * settings file, so {@code screen <page>} and old habits still land somewhere.
     */
    static String pageOf(String name) {
        return switch (name) {
            case "drawing", "overlay" -> ROUTE;
            case "path", "teleport" -> PATHFINDER;
            case "walk", "camera", "mouse" -> MOVEMENT;
            case "cache", "caches" -> MAPS;
            case "hud" -> WIDGETS;
            default -> name;
        };
    }


    private final AstarClient client;
    private final RouteOverlay overlay;
    private String page;
    private final Map<String, Integer> scrolls = new LinkedHashMap<>();

    // ---- Size, in units of px screen pixels -------------------------------------------------

    private static final int SIDE_W = 156;
    private static final int HEAD_H = 50;
    private static final int PAD = 12;
    /** The width of a row's box or list, so they line up down a card. */
    private static final int CONTROL_W = 96;
    /** Side-by-side choices all this wide, so they line up. */
    private static final int SEG_W = 152;
    private static final int GAP = 8;

    /** Screen pixels per unit of this window. */
    private int px = 2;
    /** GUI coordinates per unit. */
    private float k = 1;
    private int winX;
    private int winY;
    private int winW;
    private int winH;
    private int areaX;
    private int areaY;
    private int areaW;
    private int areaH;
    private int contentH;

    private FontDescription body;
    private FontDescription strong;
    private FontDescription small;
    private FontDescription title;

    // ---- What's on it --------------------------------------------------------------------

    /** The sidebar, the search box and anything else that doesn't scroll. */
    private final List<El> fixed = new ArrayList<>();
    /** The open page's cards. */
    private final List<Card> cards = new ArrayList<>();
    /** The open page's tiles, on the themes page. */
    private final List<Tile> tiles = new ArrayList<>();
    private Field search;
    private Field focused;
    /** The list a box of choices has open, drawn over everything; none if null. */
    private Mode<?> dropped;
    private El pressed;
    private El hovered;
    /** The mouse, in units, this frame: for controls with parts. */
    private double mouseUX;
    private long hoveredSince;

    private String problem;
    /** What the last change to a setting said, for its line. */
    private final Map<Tuning.Option, String> notes = new LinkedHashMap<>();
    private boolean changedSettings;

    AstarScreen(AstarClient client, RouteOverlay overlay, String page) {
        super(Component.literal("A* Client"));
        this.client = client;
        this.overlay = overlay;
        // Opens on Macros unless a page is asked for.
        this.page = pageOf(page == null ? MACROS : page);
        openedAt = System.currentTimeMillis();
        LoadingArt logo = LoadingArt.logo();
        if (logo != null) {
            logo.restart();
            // It plays once and holds on the route found; with Rainbow, on and on.
            if (Theme.current() != Theme.RAINBOW) {
                logo.finish();
            }
        }
    }

    AstarScreen(AstarClient client, RouteOverlay overlay) {
        this(client, overlay, null);
    }

    /** The logo's size, in units. */
    private static final int LOGO = 36;

    /** The mod's version, under its name. */
    private static final String VERSION = net.fabricmc.loader.api.FabricLoader.getInstance()
            .getModContainer("astar-client")
            .map(m -> m.getMetadata().getVersion().getFriendlyString()).orElse("");

    /**
     * The logo: the loading screen's A, playing its search once as the window opens and then
     * holding on the route it found, in the theme's colours.
     */
    private void logo(GuiGraphicsExtractor g, int x, int y) {
        LoadingArt art = LoadingArt.logo();
        if (art != null) {
            art.draw(g, x, y, LOGO, LOGO, 1);
        }
    }

    private Theme.Colours t() {
        return Theme.shown();
    }

    /** The pages in the sidebar: name and label. */
    private static Map<String, String> pages() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put(MACROS, "Macros");
        m.put(ROUTE, "Route");
        m.put(PATHFINDER, "Pathfinder");
        m.put(MOVEMENT, "Movement");
        m.put(MAPS, "Maps");
        m.put(WIDGETS, "Widgets");
        m.put(THEMES, "Themes");
        return m;
    }

    /** What each page is for, under its title in the top bar. */
    private static String pageAbout(String page) {
        return switch (page) {
            case ROUTE -> "How the route is drawn, and keys";
            case PATHFINDER -> "What moves cost and when to teleport";
            case MOVEMENT -> "How the camera and the mouse move";
            case MAPS -> "Saved maps and what to keep";
            case WIDGETS -> "Panels on the screen as you play";
            case THEMES -> "The window's colours and the route's";
            case MACROS -> "Things to do on their own";
            default -> "Every setting that matches";
        };
    }

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        // Whole screen pixels per unit: 2 on a 1080p screen, 3 at 1440p, 4 at 4K.
        px = Math.clamp(window.getHeight() / 400, 1, 4);
        k = px / (float) window.getGuiScale();
        fonts();
        int screenW = Math.round(width / k);
        int screenH = Math.round(height / k);
        // The whole screen.
        winW = screenW;
        winH = screenH;
        winX = 0;
        winY = 0;
        areaX = winX + SIDE_W + PAD;
        areaY = winY + HEAD_H + 10;
        areaW = winW - SIDE_W - 2 * PAD;
        areaH = winH - HEAD_H - 14;

        fixed.clear();
        if (!page.equals(SEARCH) && !pages().containsKey(page)) {
            page = ROUTE;
        }
        // The top bar: the search box and the HUD editor's button on the right.
        String typed = search == null ? "" : search.text;
        search = new SearchBox(typed);
        int sw = Math.min(130, areaW / 3);
        search.at(winX + winW - PAD - sw, winY + (HEAD_H - 20) / 2, sw, 20);
        fixed.add(search);
        hudButton = new Button("Edit HUD", this::editHud, Button.PLAIN)
                .tip("Move and resize the widgets on the screen.");
        hudButton.at(search.ex - 6 - hudButton.prefW(), search.ey, hudButton.prefW(), 20);
        fixed.add(hudButton);
        int sy = winY + HEAD_H + 8;
        for (var e : pages().entrySet()) {
            fixed.add(new SideItem(e.getKey(), e.getValue(), winX + 8, sy, SIDE_W - 16, 22));
            sy += 25;
        }
        buildPage();
    }

    private Button hudButton;

    /** The lettering the fonts are in, to pick them again when it changes. */
    private Ui.Lettering fontsFor;

    private void fonts() {
        fontsFor = Ui.lettering();
        body = Ui.font("body", px);
        strong = Ui.font("strong", px);
        small = Ui.font("small", px);
        title = Ui.font("title", px);
    }

    /** When the window opened, for its opening bounce. */
    private final long openedAt;
    /** When the page last changed, for its slide in. */
    private long pageAt;

    private void editHud() {
        focus(null);
        minecraft.gui.setScreen(new HudEditor(this));
    }

    /** Types {@code words} into the search box, as the {@code search} test step does. */
    void searchFor(String words) {
        search.text = words;
        page = SEARCH;
        buildPage();
    }

    /** Opens the first list of choices on the page, as the {@code dropdown} test step does. */
    void dropFirst() {
        for (Card c : cards) {
            for (Row r : c.rows) {
                for (El e : r.controls) {
                    if (e instanceof Mode<?> m) {
                        dropped = m;
                        return;
                    }
                }
            }
        }
    }

    private void open(String name) {
        if (!name.equals(page)) {
            pageAt = System.currentTimeMillis();
        }
        page = name;
        problem = null;
        search.text = "";
        focus(null);
        buildPage();
    }

    // ---- Building pages ------------------------------------------------------------------

    private void buildPage() {
        dropped = null;
        fixed.removeIf(e->e instanceof TradingTab);
        areaY=winY+HEAD_H+10;
        areaH=winH-HEAD_H-14;
        if(page.equals(MACROS))tradingTabs();
        cards.clear();
        tiles.clear();
        switch (page) {
            case SEARCH -> buildSearch(search.text.strip().toLowerCase(Locale.ROOT));
            case ROUTE -> buildRoute();
            case PATHFINDER -> {
                buildQuickPathfinder();
                advanced(() -> buildGroups("path", "teleport"));
            }
            case MOVEMENT -> {
                buildQuickMovement();
                advanced(() -> buildGroups("walk", "camera", "mouse"));
            }
            case MAPS -> {
                buildMaps();
                Card keep = card("Keeping maps", "What's saved as you play.");
                keep.option(Tuning.SAVE_CHUNKS);
                keep.option(Tuning.WHOLE_MAP);
                advanced(() -> buildGroups("cache"));
            }
            case WIDGETS -> buildWidgets();
            case MACROS -> buildTrading();
            case THEMES -> {
                Card font = card("Font", "How all the text is lettered.");
                font.row("Lettering", () -> switch (Ui.lettering()) {
                    case CLEAN -> "Inter: plain and clean, made for screens";
                    case HYPERLEGIBLE -> "Atkinson Hyperlegible: every letter easy to tell apart";
                    case WIDE -> "Lexend: wide and open, made to be easy to read";
                    case ROUNDED -> "Nunito: soft and rounded";
                    case SOFT -> "Pixelify Sans: a soft pixel look";
                    case MINECRAFT -> "The game's own letters";
                    case CAPS -> "Spaced-out capitals, like a pixel game";
                }, new Mode<>(List.of(Ui.Lettering.values()), Ui::lettering, l -> l.label,
                        Ui::lettering));
                for (Theme theme : Theme.values()) {
                    tiles.add(new Tile(theme));
                }
            }
            default -> buildGroups(page);
        }
    }

    private String tradingNote;
    private long tradingNoteUntil;
    private void notifyTrading(String note) {tradingNote=note;tradingNoteUntil=System.nanoTime()/1_000_000+4000;}
    private String tradingFeedback() {
        if(draft.dirty())return tradingNote!=null&&tradingNote.startsWith("Not saved:")?tradingNote:draft.summary();
        return System.nanoTime()/1_000_000<tradingNoteUntil && tradingNote!=null && !tradingNote.startsWith("Settings saved")?tradingNote:null;
    }
    /** Validated edits save automatically; text edits are debounced. */
    private final com.goofy.goofyaddons.config.SettingsDraft draft=com.goofy.goofyaddons.config.SettingsDraft.live();
    private final com.goofy.goofyaddons.config.SettingsAutosave autosave = new com.goofy.goofyaddons.config.SettingsAutosave(
        draft, ()->com.goofy.goofyaddons.features.FeatureManager.INSTANCE.canReloadConfig(),
        ()->System.nanoTime()/1_000_000);
    private void saveTrading(boolean retry) {
        String note=autosave.flush(retry); if(note!=null)notifyTrading(note);
    }
    @Override public void tick() {
        super.tick();
        String note=autosave.tick(); if(note!=null)notifyTrading(note);
    }
    private void editTrading(String label,java.util.function.Consumer<com.goofy.goofyaddons.config.GoofyConfig> edit) {
        draft.edit(label,edit);
        if(focused==null)saveTrading(false);
    }
    /** Re-saves the reviewed file as loaded, without any unapplied draft changes. */
    private void commitReviewed() {
        try {
            var gson=new com.google.gson.Gson();
            var candidate=gson.fromJson(gson.toJson(com.goofy.goofyaddons.config.GoofyConfig.INSTANCE),com.goofy.goofyaddons.config.GoofyConfig.class);
            com.goofy.goofyaddons.config.GoofyConfig.commitSettings(candidate);
            notifyTrading("Settings saved.");
        }catch(Exception failure){notifyTrading(failure.getMessage());}
    }
    private Field tradingNumber(String label,java.util.function.DoubleSupplier value, java.util.function.BiConsumer<com.goofy.goofyaddons.config.GoofyConfig,Double> set) {
        Field field=new Field(BigDecimal.valueOf(value.getAsDouble()).toPlainString(),20,"coins",text->draft.decimal(label,text,set),c->Character.isDigit(c)||c=='.');
        field.live=()->draft.error(label)!=null?draft.raw(label):BigDecimal.valueOf(value.getAsDouble()).toPlainString();return field.wide(96);
    }
    private Field tradingWhole(String label,java.util.function.IntSupplier value,int min,int max,java.util.function.BiConsumer<com.goofy.goofyaddons.config.GoofyConfig,Integer> set) {
        Field field=new Field(Integer.toString(value.getAsInt()),6,min+"–"+max,text->draft.whole(label,text,min,max,set),c->Character.isDigit(c));
        field.live=()->draft.error(label)!=null?draft.raw(label):Integer.toString(value.getAsInt());return field.wide(72);
    }
    private net.minecraft.client.KeyMapping bindingCapture;
    public boolean capturingTradingBinding() { return bindingCapture!=null; }
    private int bindingSlot;
    private void captureBinding(net.minecraft.client.KeyMapping binding,int slot) {
        if(!com.goofy.goofyaddons.features.FeatureManager.INSTANCE.canReloadConfig()) {
            notifyTrading("Stop trading before changing keybinds.");return;
        }
        focus(null);bindingCapture=binding;bindingSlot=slot;
        notifyTrading("Press a new key; Escape cancels.");
    }
    private enum TradingSection {
        OVERVIEW("Overview"), LIMITS("Limits"), CRAFTS("Crafts"), CALCULATOR("Calculator"), REST("Rest"), CONNECTIONS("Connections");
        final String label;
        TradingSection(String label){this.label=label;}
    }
    private static TradingSection lastTradingSection=TradingSection.OVERVIEW;
    private TradingSection tradingSection=lastTradingSection;
    private void buildTrading() {
        switch(tradingSection) {
            case OVERVIEW -> buildTradingOverview();
            case LIMITS -> buildTradingLimits();
            case CRAFTS -> buildTradingCrafts();
            case CALCULATOR -> {buildTradingCalculator();buildTradingFiles();}
            case REST -> buildTradingRest();
            case CONNECTIONS -> {buildTradingCommunity();buildTradingDiscord();}
        }
    }
    private void tradingTabs() {
        int left=columnX(), right=left+columnW(), x=left, y=winY+HEAD_H+10;
        for(var section:TradingSection.values()) {
            int w=width(section.label,body)+16;
            if(x>left && x+w>right){x=left;y+=24;}
            fixed.add(new TradingTab(section).at(x,y,w,18));x+=w+4;
        }
        areaY=y+28;
        areaH=winY+winH-14-areaY;
    }
    private final class TradingTab extends El {
        final TradingSection section;
        TradingTab(TradingSection section){this.section=section;}
        @Override void draw(GuiGraphicsExtractor g,boolean hot) {
            Pixel.button(g,ex,ey,ew,eh,section==tradingSection?Pixel.State.ACTIVE:hot?Pixel.State.HOVER:Pixel.State.NORMAL,t());
            text(g,section.label,body,ex+(ew-width(section.label,body))/2,ey+6,section==tradingSection?t().highlight:t().textDim);
        }
        @Override void click(double mx,double my,int button) {
            focus(null);bindingCapture=null;tradingSection=section;lastTradingSection=section;buildPage();
        }
    }
    private void buildTradingOverview() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card status=card("SkyBlock trader","Choose a mode and start trading. Settings save automatically while stopped.");
        java.util.function.BooleanSupplier craftReview=()->"CRAFT".equals(manager.modeLabel())&&manager.production().reviewRequired();
        status.callout(()->craftReview.getAsBoolean()||manager.hasSafetyBlock()||com.goofy.goofyaddons.config.GoofyConfig.loadError()!=null?Callout.ERROR:Callout.INFO,
            ()->com.goofy.goofyaddons.config.GoofyConfig.loadError()!=null?com.goofy.goofyaddons.config.GoofyConfig.loadError():craftReview.getAsBoolean()?"REVIEW REQUIRED · Export debug before clearing saved work":manager.status()+" · "+manager.modeLabel());
        status.row("Activity",()->"CRAFT".equals(manager.modeLabel())?manager.production().displayActivity():manager.taskItem());
        status.row("Debug report",()->"Capture the current menu, saved jobs and failure history before clearing a review.",
            new Button("Export debug",com.goofy.goofyaddons.diagnostics.Diagnostics::exportFromKeybind,Button.PLAIN));
        status.row("Account prerequisites",manager::accountRequirementsStatus);
        status.row("Purse",()->{double purse=new com.goofy.goofyaddons.utils.ScoreboardUtils().getPurse();return purse<0?"Waiting for a SkyBlock balance":String.format(Locale.ROOT,"%,.0f coins",purse);});
        status.row("Confirmed profit",()->String.format(Locale.ROOT,"%,.1f coins",com.goofy.goofyaddons.features.profit.ProfitTracker.INSTANCE.summary().profit()));
        status.row("Trading",()->"The toggle key starts and stops trading. Starting closes this window so transaction menus can open.",
            new Button("Start",()->{
                client.cancelRouteForTrading();
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(null);
                com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStart();
            },Button.ACCENT).when(()->!draft.dirty() && !manager.isMacroRunning() && client.canStartTrading()),
            new Button("Stop",()->com.goofy.goofyaddons.features.sessions.SessionScheduler.INSTANCE.manualStop(),Button.PLAIN));
        status.row("Mode",()->"All flips compares books, general and crafts by estimated net coins/hour. Change while stopped.",new Mode<>(List.of(com.goofy.goofyaddons.features.TradingMode.values()),
            ()->draft.view().tradingMode,com.goofy.goofyaddons.features.TradingMode::label,v->editTrading("Mode",cfg->cfg.tradingMode=v)));
        Card keys=card("Keybinds","Click Change, then press a key. Escape cancels. Bindings also appear in Minecraft Controls.");
        keys.row("Trading on / off",()->com.goofy.goofyaddons.keybinds.GoofyKeybinds.toggleKey.getTranslatedKeyMessage().getString(),
            new Button("Change",()->captureBinding(com.goofy.goofyaddons.keybinds.GoofyKeybinds.toggleKey,0),Button.PLAIN));
        keys.row("Debug export",()->com.goofy.goofyaddons.keybinds.GoofyKeybinds.debugKey.getTranslatedKeyMessage().getString(),
            new Button("Change",()->captureBinding(com.goofy.goofyaddons.keybinds.GoofyKeybinds.debugKey,1),Button.PLAIN));
        keys.row("Reload config",()->com.goofy.goofyaddons.keybinds.GoofyKeybinds.reloadKey.getTranslatedKeyMessage().getString(),
            new Button("Change",()->captureBinding(com.goofy.goofyaddons.keybinds.GoofyKeybinds.reloadKey,2),Button.PLAIN));

    }
    private void buildTradingLimits() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card timing=card("Menu action timing","Navigation timing. Crafting and storage follow this range until their own timing is configured.");
        timing.row("Fastest delay (ms)",()->"Minimum delay between actions. Server updates must still be verified.",
            tradingWhole("Fastest delay",()->draft.view().minActionDelay,1,1999,(cfg,v)->{cfg.minActionDelay=v;cfg.maxActionDelay=Math.max(cfg.maxActionDelay,v+1);}));
        timing.row("Slowest delay (ms)",()->"Maximum randomized delay. Keep the range narrow for consistent speed or widen it for more variation.",
            tradingWhole("Slowest delay",()->draft.view().maxActionDelay,2,2000,(cfg,v)->{cfg.maxActionDelay=v;cfg.minActionDelay=Math.min(cfg.minActionDelay,v-1);}));
        Card workTiming=card("Crafting and storage timing","Crafting and anvil share one range. Storage has its own range. Until edited, both follow navigation timing; server acknowledgements still apply.");
        workTiming.row("Craft / anvil fastest (ms)",()->"Ingredient placement, grid actions and book combining.",
            tradingWhole("Craft fastest",()->draft.view().minCraftDelay==0?draft.view().minActionDelay:draft.view().minCraftDelay,1,1999,(cfg,v)->{cfg.minCraftDelay=v;cfg.maxCraftDelay=Math.max(cfg.maxCraftDelay==0?cfg.maxActionDelay:cfg.maxCraftDelay,v+1);}));
        workTiming.row("Craft / anvil slowest (ms)",()->"Upper end of randomized crafting delay.",
            tradingWhole("Craft slowest",()->draft.view().maxCraftDelay==0?draft.view().maxActionDelay:draft.view().maxCraftDelay,2,2000,(cfg,v)->{cfg.maxCraftDelay=v;cfg.minCraftDelay=Math.min(cfg.minCraftDelay==0?cfg.minActionDelay:cfg.minCraftDelay,v-1);}));
        workTiming.row("Storage fastest (ms)",()->"Deposits, withdrawals and book storage moves.",
            tradingWhole("Storage fastest",()->draft.view().minStorageDelay==0?draft.view().minActionDelay:draft.view().minStorageDelay,1,1999,(cfg,v)->{cfg.minStorageDelay=v;cfg.maxStorageDelay=Math.max(cfg.maxStorageDelay==0?cfg.maxActionDelay:cfg.maxStorageDelay,v+1);}));
        workTiming.row("Storage slowest (ms)",()->"Upper end of randomized storage delay.",
            tradingWhole("Storage slowest",()->draft.view().maxStorageDelay==0?draft.view().maxActionDelay:draft.view().maxStorageDelay,2,2000,(cfg,v)->{cfg.maxStorageDelay=v;cfg.minStorageDelay=Math.min(cfg.minStorageDelay==0?cfg.minActionDelay:cfg.minStorageDelay,v-1);}));
        Card capital=card("Spending limits","These limits apply to every purchase. Changes require stopping the trader.");
        capital.row("Capital limit",()->"Maximum committed trading capital, in coins.",tradingNumber("Capital limit",()->draft.view().maxTradingCapital,(cfg,v)->cfg.maxTradingCapital=v));
        capital.row("Purse reserve",()->"Coins kept outside trading; zero uses all available funds.",tradingNumber("Purse reserve",()->draft.view().purseReserve,(cfg,v)->cfg.purseReserve=v));
        capital.row("Book slots",()->"Maximum active book routes.",tradingWhole("Book slots",()->draft.view().maxActiveBooks,1,10,(cfg,v)->cfg.maxActiveBooks=v));
        capital.row("Item slots",()->"Maximum active ordinary-item routes.",tradingWhole("Item slots",()->draft.view().general.maxActiveItems,1,10,(cfg,v)->cfg.general.maxActiveItems=v));
        capital.row("Coins per item",()->"Ceiling per General route (default 25M). New entries also share spendable funds across remaining item slots.",tradingNumber("Coins per item",()->draft.view().general.maxCoinsPerItem,(cfg,v)->cfg.general.maxCoinsPerItem=v));
        capital.row("Units per item",()->"Maximum units per General order, from 1 to 4096. Inventory and market flow limits still apply.",tradingWhole("Units per item",()->draft.view().general.maxItemsPerOrder,1,4096,(cfg,v)->cfg.general.maxItemsPerOrder=v));
        capital.row("Production buys inputs",()->"Let a queued production run instant-buy missing ingredients within spendable capital. Off until you have tested it in game.",
            new Check(()->draft.view().productionBuysIngredients,v->editTrading("Production buys inputs",cfg->cfg.productionBuysIngredients=v)).toggle());

    }
    private void buildTradingCalculator() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card market=card("Calculator and account","Live prices, player unlocks and the local dashboard.");
        market.row("Background service",()->com.goofy.goofyaddons.features.companion.BundledCalculator.status(),
            new Button("Dashboard",()->com.goofy.goofyaddons.features.companion.BundledCalculator.openDashboard(),Button.PLAIN),
            new Button("Retry / restart",()->com.goofy.goofyaddons.features.companion.BundledCalculator.retry(),Button.PLAIN));
        market.row("Calculator port",()->"Change this if another program occupies the port. Dashboard and trade feed follow this setting.",
            tradingWhole("Calculator port",()->java.net.URI.create(draft.view().marketAnalysis.endpoint).getPort(),1024,65535,
                (cfg,v)->cfg.marketAnalysis.endpoint="http://127.0.0.1:"+v+"/v1/recommendations"));
        market.row("Auto-start",()->"Start the bundled calculator with Minecraft; saved data stays outside the mod folder.",new Check(()->draft.view().marketAnalysis.autoStartCompanion,v->editTrading("Auto-start",cfg->cfg.marketAnalysis.autoStartCompanion=v)).toggle());
        market.row("Calculator",()->"Enable local market analysis.",new Check(()->draft.view().marketAnalysis.enabled,v->editTrading("Calculator",cfg->{cfg.marketAnalysis.enabled=v;if(!v)cfg.marketAnalysis.automaticSelection=false;})).toggle());
        market.row("Automatic routes",()->"Choose supported flips from the calculator while retaining spending and requirement checks.",new Check(()->draft.view().marketAnalysis.automaticSelection,v->editTrading("Automatic routes",cfg->{cfg.marketAnalysis.automaticSelection=v;if(v)cfg.marketAnalysis.enabled=true;})).toggle());
        market.row("Account dashboard",()->"Share local account snapshots with the companion site.",new Check(()->draft.view().marketAnalysis.dashboardEnabled,v->editTrading("Account dashboard",cfg->cfg.marketAnalysis.dashboardEnabled=v)).toggle());
        market.row("Bazaar access",()->"NPC mode uses A* to approach a loaded Bazaar NPC.",new Mode<>(List.of("AUTO","COMMAND","NPC"),()->draft.view().access.bazaarMode,v->v,v->editTrading("Bazaar access",cfg->cfg.access.bazaarMode=v)));
        market.row("Skill checks",()->"Check account levels and skip blocked routes.",new Check(()->draft.view().access.checkSkills,v->editTrading("Skill checks",cfg->cfg.access.checkSkills=v)).toggle());
        market.row("Versions",()->com.goofy.goofyaddons.diagnostics.ReleaseInfo.manifest().summary());
        market.row("Last route decision",()->{String why=manager.lastRouteDecision();return why==null?"No automatic route chosen yet.":why;});
        Card abilities=card("What runs automatically","Research only plans or reads menus, Queued runs steps you start, Automatic chooses and finishes its own work.");
        for(var entry:com.goofy.goofyaddons.features.capability.Capabilities.all())
            abilities.row(entry.feature().label,()->entry.level().name().charAt(0)+entry.level().name().substring(1).toLowerCase(Locale.ROOT)+" · "+entry.boundary());

    }
    private void buildTradingCrafts() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card crafts=card("Craft flips","Buy ingredients, prepare intermediates, craft and sell using the selected Bazaar strategy.");
        crafts.row("Craft flip mode",()->"Select Craft flips, then use the trading toggle for automatic Bazaar crafting.",
            new Button("Use craft mode",()->editTrading("Mode",cfg->cfg.tradingMode=com.goofy.goofyaddons.features.TradingMode.CRAFT),Button.PLAIN));
        crafts.row("Sale market",()->"Choose which market appears in the craft ranking.",new Mode<>(List.of("BOTH","BAZAAR","AH"),()->draft.view().craftFlips.venue,
            v->v,v->editTrading("Craft sale market",cfg->cfg.craftFlips.venue=v)));
        crafts.row("Buy / sell strategy",()->"Auto compares all four strategies; instant → offer matches the website's Hotspot Bait example.",
            new Mode<>(List.of("AUTO","ORDER_OFFER","INSTANT_OFFER","ORDER_INSTANT","INSTANT_INSTANT"),()->draft.view().craftFlips.strategy,
                v->v.equals("AUTO")?"Auto":com.goofy.goofyaddons.features.production.BazaarStrategy.valueOf(v).label(),
                v->editTrading("Craft strategy",cfg->cfg.craftFlips.strategy=v)));
        crafts.row("Batch capital",()->"Maximum coins per craft run; zero uses spendable funds.",tradingNumber("Craft capital",()->draft.view().craftFlips.maximumCapital,(cfg,v)->cfg.craftFlips.maximumCapital=v));
        crafts.row("Minimum net profit",()->"Require this profit after depth, taxes and price movement allowance before buying.",tradingNumber("Craft profit",()->draft.view().craftFlips.minimumProfit,(cfg,v)->cfg.craftFlips.minimumProfit=v));
        crafts.row("Active crafts",()->"1–4 independent jobs. Wait on orders in parallel; GUI actions remain sequential.",tradingWhole("Active crafts",()->draft.view().craftFlips.maxActiveCrafts,1,4,(cfg,v)->cfg.craftFlips.maxActiveCrafts=v));
        crafts.row("Maximum batches",()->"1–1024; purchases and crafting use inventory-sized rounds.",tradingWhole("Craft batches",()->draft.view().craftFlips.maxBatches,1,1024,(cfg,v)->cfg.craftFlips.maxBatches=v));
        crafts.row("Use storage",()->"Buy more than the inventory holds: claim what fits, park it in ender chest pages 1–3 and backpacks 1–4, then claim the rest. Test it in game first.",
            new Check(()->draft.view().craftFlips.useStorage,v->editTrading("Use storage",cfg->cfg.craftFlips.useStorage=v)).toggle());
        crafts.row("Inspect ranking",()->"Show estimated coins/hour, batch profit, strategy and route blockers in chat.",new Button("Best crafts",()->manager.production().showCraftPlans(),Button.PLAIN));
        crafts.row("Current plan",manager.production()::discoveryStatus);
        Card auctionTests=card("Auction House tests","Craft and list one BIN. Automatic sale and claim tracking is still pending.");
        String[] testItem={"ASPECT_OF_THE_END"};
        Field ahTestItem=new Field(testItem[0],120,"ASPECT_OF_THE_END",v->testItem[0]=v,c->Character.isLetterOrDigit(c)||c=='_').wide(200);
        auctionTests.row("AH test item",()->"Craft one item and publish one BIN. Stop trading first; missing ingredients are bought within your spending limits.",ahTestItem);
        auctionTests.row("Test AH craft",()->"Checks the selling price with Coflnet, then crafts and opens Create Auction to list one item. Listing is not a confirmed sale.",
            new Button("Run one test",()->{
                client.cancelRouteForTrading();
                net.minecraft.client.Minecraft.getInstance().gui.setScreen(null);
                com.goofy.goofyaddons.features.production.ProductionCommands.testAuction(testItem[0]);
            },Button.ACCENT).when(()->!draft.dirty()&&manager.canReloadConfig()&&client.canStartTrading()));

    }
    private void buildTradingCommunity() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        var sharing=com.goofy.goofyaddons.features.companion.BundledCalculator.contributor();
        Card community=card("Shared gameplay learning","Learn from the public dataset. Uploads require a private key approved by the collector owner.");
        community.row("Sync status",()->com.goofy.goofyaddons.features.companion.BundledCalculator.sharingStatus());
        community.row("Trade feed",()->com.goofy.goofyaddons.features.companion.ContributorTelemetry.status());
        community.row("Saved key",()->com.goofy.goofyaddons.features.companion.BundledCalculator.contributor().hasKey()?"Private key saved":"No contributor key saved");
        Field collector=new Field(sharing.endpoint(),240,"https://your-collector.workers.dev",v->{},c->!Character.isWhitespace(c)).wide(200);
        Field repository=new Field(sharing.repository(),120,"owner/repo",v->{},c->Character.isLetterOrDigit(c)||c=='/'||c=='_'||c=='-'||c=='.').wide(200);
        Field contributorKey=new Field("",128,"Paste private key",v->{},c->Character.isLetterOrDigit(c)||c=='_'||c=='-').wide(200).masked();
        boolean[] uploads={sharing.enabled()};
        community.row("Collector",()->"The owner's upload service address.",collector);
        community.row("Dataset repository",()->"Public repository used to download shared learning data.",repository);
        community.row("Contributor key",()->"Paste the private key you received. Leave blank to keep a saved key.",contributorKey);
        community.row("Upload gameplay",()->"Share trade timing and profit ratios. Names, inventory, balances and chat stay private.",new Check(()->uploads[0],v->uploads[0]=v).toggle());
        community.row("Apply",()->"Save privately and reload the bundled calculator. Public downloads need no key.",
            new Button("Save sharing",()->{
                com.goofy.goofyaddons.features.companion.BundledCalculator.saveContributor(collector.text,repository.text,contributorKey.text,uploads[0],false);
                contributorKey.text="";focus(null);
            },Button.PLAIN).when(()->!com.goofy.goofyaddons.features.companion.BundledCalculator.savingContributor()),
            new Button("Forget key",()->{
                uploads[0]=false;contributorKey.text="";focus(null);
                com.goofy.goofyaddons.features.companion.BundledCalculator.saveContributor(collector.text,repository.text,"",false,true);
            },Button.PLAIN).when(()->!com.goofy.goofyaddons.features.companion.BundledCalculator.savingContributor()));
        community.callout(()->Callout.INFO,()->com.goofy.goofyaddons.features.companion.BundledCalculator.contributorNote());

    }
    private void buildTradingDiscord() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card discord=card("Discord companion","Status posts, contact alerts and manual player controls through the locally paired calculator. Bot credentials stay on your PC.");
        discord.row("Private settings",()->"Fill in the bot token and channel/user IDs, then restart the background service.",new Button("Open settings",()->com.goofy.goofyaddons.features.companion.BundledCalculator.openDiscordSettings(),Button.PLAIN).when(()->com.goofy.goofyaddons.features.companion.BundledCalculator.discordSettingsReady()));
        discord.row("Webhook interval",()->"Configured in the companion's private discord-settings.json; default five minutes.");
        discord.row("Bridge",()->com.goofy.goofyaddons.features.discord.DiscordRemote.INSTANCE.status());
        discord.row("Enabled",()->"Requires the companion bot and config/goofyaddons-discord.key.",new Check(()->draft.view().discord.enabled,v->editTrading("Discord enabled",cfg->cfg.discord.enabled=v)).toggle());
        discord.row("Pause on contact",()->"Pause trading after a mention or staff message until you review it.",new Check(()->draft.view().discord.pauseOnContact,v->editTrading("Pause on contact",cfg->cfg.discord.pauseOnContact=v)).toggle());
        discord.row("Mention alerts",()->"Send messages mentioning your player name to your Discord channel.",new Check(()->draft.view().discord.alertMentions,v->editTrading("Mention alerts",cfg->cfg.discord.alertMentions=v)).toggle());
        discord.row("Staff-message alerts",()->"Notify on a staff rank in the sender prefix; respond manually.",new Check(()->draft.view().discord.alertStaff,v->editTrading("Staff-message alerts",cfg->cfg.discord.alertStaff=v)).toggle());

    }
    private void buildTradingRest() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card rest=card("Daily rest schedule","Local regional time with daylight saving. Existing session windows are preserved.");
        rest.row("Enabled",()->"Log off and reconnect according to the configured time ranges.",new Check(()->draft.view().restSchedule.enabled,v->editTrading("Rest schedule enabled",cfg->cfg.restSchedule.enabled=v)).toggle());
        Field zone=new Field(draft.view().restSchedule.timeZone,64,"America/New_York",v->editTrading("Time zone",cfg->cfg.restSchedule.timeZone=v),c->Character.isLetterOrDigit(c)||c=='/'||c=='_'||c=='-'||c=='+');
        zone.live=()->draft.view().restSchedule.timeZone;
        rest.row("Time zone",()->"Use a regional name such as America/Toronto.",zone.wide(160));
        for(int index=0;index<draft.view().restSchedule.windows.size();index++) {
            final int i=index;
            String[] names={"Login from","Login until","Logout from","Logout until"};
            for(int field=0;field<4;field++) {
                final int f=field;
                java.util.function.Supplier<String> value=()->{
                    var w=draft.view().restSchedule.windows.get(i);
                    return switch(f){case 0->w.loginFrom;case 1->w.loginUntil;case 2->w.logoutFrom;default->w.logoutUntil;};
                };
                Field time=new Field(value.get(),5,"HH:mm",v->editTrading("Session "+(i+1)+" · "+names[f],cfg->{
                    var w=cfg.restSchedule.windows.get(i);
                    switch(f){case 0->w.loginFrom=v;case 1->w.loginUntil=v;case 2->w.logoutFrom=v;default->w.logoutUntil=v;}
                }),c->Character.isDigit(c)||c==':');time.live=value;
                rest.row("Session "+(i+1)+" · "+names[field],()->"24-hour HH:mm. Invalid or overlapping ranges are not saved.",time.wide(64));
            }
        }

    }
    private void buildTradingFiles() {
        var manager=com.goofy.goofyaddons.features.FeatureManager.INSTANCE;
        Card files=card("Saved settings and sessions","Advanced route settings and regional rest windows remain in goofyaddons.json. Existing data files are retained.");
        files.row("Review / reload",()->com.goofy.goofyaddons.config.GoofyConfig.location(),new Button("Save reviewed",this::commitReviewed,Button.PLAIN).when(()->!draft.dirty()),
            new Button("Reload",()->{if(manager.canReloadConfig())com.goofy.goofyaddons.config.ConfigReload.reload();else notifyTrading("Stop trading before reloading.");},Button.PLAIN));
        files.callout(()->Callout.INFO,()->tradingNote);
    }

    private void buildRoute() {
        Card route = card("Route in the world", Tuning.sections().get("overlay"));
        route.master(overlay::shown, overlay::shown, "Draw the route while going (.A* show).");
        drawingRows(route, words -> true, false);
        for (Tuning.Option o : Tuning.all()) {
            if (o.section.equals("overlay")) {
                route.option(o);
            }
        }
        Card keys = card("Keys", "Keys for the window. Esc or this key closes it.");
        keys.row("Open window", () -> "Click, then press a key", new KeyBind());
    }

    /**
     * The Route page's own rows (not settings from the file) whose words pass {@code keep};
     * {@code shown} adds the on/off line, which the page itself has as its card's switch.
     */
    private void drawingRows(Card route, java.util.function.Predicate<String> keep,
            boolean shown) {
        StringBuilder looks = new StringBuilder();
        for (RouteOverlay.Style s : RouteOverlay.Style.values()) {
            looks.append(' ').append(s.label).append(' ').append(s.id()).append(' ')
                    .append(s.about);
        }
        StringBuilder colours = new StringBuilder();
        for (OverlayColour c : OverlayColour.values()) {
            colours.append(' ').append(c.id());
        }
        if (shown && keep.test("shown show hide draw route")) {
            route.row("Shown", () -> "Draw the route while going (.A* show)",
                new Check(overlay::shown, overlay::shown).toggle());
        }
        if (keep.test("look style render route" + looks)) {
            route.row("Look", () -> overlay.style().about,
                    new Mode<>(List.of(RouteOverlay.Style.values()), overlay::style,
                            s -> s.label, overlay::style));
        }
        if (keep.test("colour color route theme" + colours)) {
            Swatches colour = new Swatches(List.of(OverlayColour.values()), overlay::colour,
                    overlay::colour);
            route.row("Colour", () -> capital(overlay.colour().id())
                    + (overlay.colour() == Theme.current().route ? ", matches the theme" : ""),
                    colour);
        }
    }

    // ---- Quick choices ---------------------------------------------------------------------

    /**
     * One of a few ways to set several settings at once: its name, a line on what it does,
     * and the settings, as "section.name=value".
     */
    private record Preset(String label, String about, String... values) {

        boolean matches() {
            for (String v : values) {
                int eq = v.indexOf('=');
                Tuning.Option o = Tuning.find(v.substring(0, eq));
                String want = v.substring(eq + 1);
                boolean same = o instanceof Tuning.Num n
                        ? Math.abs(n.get() - Double.parseDouble(want)) < 1e-9
                        : o != null && o.text().equals(want);
                if (!same) {
                    return false;
                }
            }
            return true;
        }
    }

    /** A line of quick choices: picking one sets all its settings. */
    private void quick(Card c, String name, Preset... presets) {
        List<String> labels = new ArrayList<>();
        for (Preset p : presets) {
            labels.add(p.label());
        }
        Supplier<Preset> now = () -> {
            for (Preset p : presets) {
                if (p.matches()) {
                    return p;
                }
            }
            return null;
        };
        Segmented<String> mode = new Segmented<>(labels, () -> now.get() == null ? null
                : now.get().label(), v -> v, v -> {
                    for (Preset p : presets) {
                        if (p.label().equals(v)) {
                            for (String set : p.values()) {
                                int eq = set.indexOf('=');
                                apply(Tuning.find(set.substring(0, eq)), set.substring(eq + 1));
                            }
                        }
                    }
                });
        StringBuilder tip = new StringBuilder();
        for (Preset p : presets) {
            tip.append(tip.isEmpty() ? "" : "\n\n").append(p.label()).append(": ")
                    .append(p.about());
        }
        Row r = c.row(name, () -> now.get() == null
                ? "Your own mix, set under Advanced" : now.get().about(), mode);
        r.tip = tip.toString();
        mode.about = v -> {
            for (Preset p : presets) {
                if (p.label().equals(v)) {
                    return p.about();
                }
            }
            return null;
        };
    }

    private void buildQuickPathfinder() {
        Card c = card("Routes", "How .A* picks its way. Changes count from the next route.");
        quick(c, "Route style",
                new Preset("Balanced", "Keeps a little off walls, few needless turns",
                        "path.wall=0.3", "path.turn=0.1", "path.jump=2", "path.drop=2"),
                new Preset("Direct", "The shortest way, close to walls",
                        "path.wall=0", "path.turn=0.05", "path.jump=1.5", "path.drop=1.5"),
                new Preset("Careful", "Well off walls and edges, fewer jumps",
                        "path.wall=1", "path.turn=0.2", "path.jump=3", "path.drop=3"));
        quick(c, "Teleports",
                new Preset("Normal", "Teleports where it saves a good bit of walking",
                        "teleport.etherwarp_cost=8", "teleport.it_cost=5"),
                new Preset("Often", "Teleports whenever it's a little quicker",
                        "teleport.etherwarp_cost=5", "teleport.it_cost=3"),
                new Preset("Rarely", "Walks unless teleporting saves a lot",
                        "teleport.etherwarp_cost=14", "teleport.it_cost=9"));
        quick(c, "Aiming",
                new Preset("Normal", "About a second to aim a teleport",
                        "teleport.aim_time=1", "teleport.aim_variation=0.1"),
                new Preset("Quick", "Aims fast, like a practised player",
                        "teleport.aim_time=0.6", "teleport.aim_variation=0.05"),
                new Preset("Steady", "Takes its time over each aim",
                        "teleport.aim_time=1.5", "teleport.aim_variation=0.15"));
    }

    private void buildQuickMovement() {
        Card c = card("Feel", "How .A* moves the camera and walks.");
        quick(c, "Camera",
                new Preset("Normal", "Smooth turns, looks a little ahead",
                        "camera.look_ahead=2.5", "camera.pitch_speed=90", "camera.hold=2",
                        "camera.drift=0.35", "mouse.stiffness=25", "mouse.turn_speed=540"),
                new Preset("Calm", "Slow, gentle turns, looks further ahead",
                        "camera.look_ahead=3.5", "camera.pitch_speed=60", "camera.hold=3",
                        "camera.drift=0.5", "mouse.stiffness=15", "mouse.turn_speed=360"),
                new Preset("Snappy", "Quick, tight turns",
                        "camera.look_ahead=2", "camera.pitch_speed=150", "camera.hold=1",
                        "camera.drift=0.2", "mouse.stiffness=40", "mouse.turn_speed=900"));
        c.option(Tuning.STRAFE);
        c.option(Tuning.HAND);
        quick(c, "Stopping",
                new Preset("Normal", "Walks up to the goal and lets go",
                        "walk.goal_reach=0.6"),
                new Preset("Exact", "Stops right on the goal block's middle",
                        "walk.goal_reach=0.3"),
                new Preset("Loose", "Anywhere about a block from the goal",
                        "walk.goal_reach=1"));
    }

    /** The pages whose every setting is shown: kept while the game runs. */
    private static final java.util.Set<String> ADVANCED = new java.util.HashSet<>();

    /** A switch for every setting on the page, one by one, and those settings while it's on. */
    private void advanced(Runnable build) {
        Card c = card("Advanced", "Every setting on its own, for fine tuning.");
        String on = page;
        c.master(() -> ADVANCED.contains(on), show -> {
            if (show) {
                ADVANCED.add(on);
            } else {
                ADVANCED.remove(on);
            }
            buildPage();
        }, "Show every setting on this page, one by one.");
        if (ADVANCED.contains(on)) {
            build.run();
        }
    }

    /** A card of settings: its title, what it's for, its switch (if any) and its settings. */
    private record Group(String title, String about, String master, String... options) {}

    /** Each section's settings in cards, in the order they're shown. */
    private static final Map<String, List<Group>> GROUPS = Map.of(
            "path", List.of(
                    new Group("Move costs", "What each kind of step costs. Dearer ones are"
                            + " used less.", null, "walk", "diagonal", "jump", "drop",
                            "drop_per_block", "swim", "climb"),
                    new Group("Route shape", "Keeping off walls and cutting needless turns.",
                            null, "wall", "turn")),
            "teleport", List.of(
                    new Group("Etherwarp", "Teleports to a block you aim at.", null,
                            "etherwarp_cost", "etherwarp_min"),
                    new Group("Instant transmission", "Teleports straight ahead.", null,
                            "it_cost", "it_min"),
                    new Group("Aiming", "How long aiming a teleport takes.", null, "aim_time",
                            "aim_variation")),
            "walk", List.of(
                    new Group("Arriving", "How near the goal counts as there.", null,
                            "goal_reach")),
            "camera", List.of(
                    new Group("Look ahead", "How far down the route the camera looks.", null,
                            "look_ahead", "look_ahead_per_speed", "ridge_look",
                            "ridge_look_per_speed", "view_height"),
                    new Group("Pitch", "How far up and down the camera looks.", null,
                            "pitch_min", "pitch_max", "pitch_speed"),
                    new Group("Strafing", "Walking sideways round corners, looking ahead.",
                            "strafe", "strafe_lead", "strafe_look", "strafe_look_per_speed",
                            "hold_strafing"),
                    new Group("Hold and drift", "Keeping still on straights, and in the air.",
                            null, "hold", "drift", "drift_pitch", "air_turn_accel")),
            "mouse", List.of(
                    new Group("Hand and easing", "Which hand moves the mouse, and how turns"
                            + " ease in and out.", null, "hand", "ease_turns", "ease_flicks"),
                    new Group("Turn speed", "How fast the mouse can turn.", null, "stiffness",
                            "turn_speed", "turn_accel"),
                    new Group("Sweeps and flicks", "Big turns: one sweep, or a quick flick"
                            + " that misses a little.", null, "sweep_over", "flick_over",
                            "sweep_time", "sweep_time_per_doubling", "undershoot",
                            "overshoot_out", "overshoot_in")),
            "cache", List.of(
                    new Group("Saving chunks", "Keeping chunks you've seen, to plan past"
                            + " render distance.", "save_chunks", "keep_files",
                            "bundled_mines"),
                    new Group("Map detection", "Telling which saved map this is.",
                            "auto_detect", "match", "matches_needed", "new_after"),
                    new Group("Whole map", "Planning over the whole saved map at once.",
                            "whole_map", "whole_map_size")));

    /** The sections' settings in their cards; any not in one go in a last card. */
    private void buildGroups(String... sections) {
        for (String section : sections) {
            List<Tuning.Option> left = new ArrayList<>();
            for (Tuning.Option o : Tuning.all()) {
                if (o.section.equals(section)) {
                    left.add(o);
                }
            }
            for (Group gr : GROUPS.getOrDefault(section, List.of())) {
                Card c = card(gr.title(), gr.about());
                List<Tuning.Option> in = new ArrayList<>();
                if (gr.master() != null
                        && Tuning.find(section + "." + gr.master()) instanceof Tuning.Flag f) {
                    c.master(f::get, on -> apply(f, on.toString()), f.help);
                    left.remove(f);
                    in.add(f);
                }
                for (String name : gr.options()) {
                    Tuning.Option o = Tuning.find(section + "." + name);
                    if (o == null || !left.contains(o)) {
                        continue;
                    }
                    if (name.equals("pitch_min")
                            && Tuning.find(section + ".pitch_max") instanceof Tuning.Num hi
                            && o instanceof Tuning.Num lo) {
                        c.range("Pitch range", lo, hi);
                        left.remove(hi);
                        in.add(hi);
                    } else {
                        c.option(o);
                    }
                    left.remove(o);
                    in.add(o);
                }
                c.resetAll(in);
            }
            if (!left.isEmpty()) {
                Card c = card(GROUPS.containsKey(section) ? "More " + section : capital(section),
                        Tuning.sections().get(section));
                for (Tuning.Option o : left) {
                    c.option(o);
                }
                c.resetAll(left);
            }
        }
    }

    /** The HUD's widgets, a card each, and its toasts. */
    private void buildWidgets() {
        Card edit = card("HUD editor", "Drag widgets where you want them, scroll to size them.");
        edit.row("Placing", () -> "Opens the screen with the widgets on it",
                new Button("Edit HUD", this::editHud, Button.ACCENT));
        Card toasts = card("Notifications", "Pop-ups when a trip plans, starts and ends.");
        toasts.master(() -> Hud.toasts, on -> {
            Hud.toasts = on;
            Hud.save();
        }, "Show a toast in the bottom right as trips change.");
        toasts.row("Try it", () -> "Shows one now", new Button("Show", Hud::sample,
                Button.PLAIN));
        for (Hud.Widget w : Hud.WIDGETS) {
            Card c = card(w.label, w.about);
            c.master(() -> w.on, on -> {
                w.on = on;
                Hud.save();
            }, "Show the " + w.label.toLowerCase(Locale.ROOT) + " widget.");
            Mode<Integer> size = new Mode<>(List.of(0, 1, 2, 3, 4), () -> w.size,
                    n -> n == 0 ? "Auto" : n + "x", n -> {
                        w.size = n;
                        Hud.save();
                    });
            c.row("Size", () -> w.size == 0 ? "As big as this window's pixels"
                    : "Screen pixels per pixel of it", size);
            c.row("Place", () -> "Where it is on the screen", new Button("Reset", () -> {
                w.resetPlace();
                Hud.save();
            }, Button.PLAIN).tip("Back to where it starts, at its own size."));
        }
    }

    /** The maps saved for this server and dimension: which one this is, and each one's files. */
    private void buildMaps() {
        Card here = card("Maps here", "Saved for this server and dimension, one per place.");
        Places.Place now = Places.current();
        Field name = new Field(now == null ? "" : now.name, 64, "not sure yet", s -> {},
                c -> Character.isLetterOrDigit(c) || c == '_' || c == '-' || c == '.');
        here.row("This map", () -> now == null ? "Not sure yet" : "Type a name for it",
                name.wide(150));
        Row named = here.row(null, null,
                new Button("Use", () -> said(Places.choose(name.text.strip()), "This is"),
                        Button.PLAIN).tip("Say this is the map with that name (.A* map)."),
                new Button("Rename", () -> said(Places.rename(name.text.strip()), " is called "),
                        Button.PLAIN).when(() -> now != null)
                        .tip("Give the map you're on this name instead."));
        named.spread = true;
        List<Places.MapInfo> maps = Places.maps();
        if (maps.isEmpty()) {
            here.callout(() -> Callout.INFO, () -> "No maps saved here yet.");
        }
        for (Places.MapInfo m : maps) {
            String about = m.chunks() + " chunks, " + megabytes(m.bytes())
                    + (m.current() ? ", here now" : "") + (m.bundled() ? ", bundled" : "");
            Button forget = new Button("Forget", null, Button.PLAIN);
            forget.action = () -> {
                // Deletes files, so it asks once more first.
                if (forget.armedUntil < System.currentTimeMillis()) {
                    forget.armedUntil = System.currentTimeMillis() + 3000;
                    return;
                }
                said(Places.forget(m.name()), "Forgot");
                forget.armedUntil = 0;
            };
            forget.tip("Delete the chunks and files saved for " + m.name()
                    + ". Click twice.");
            here.row(m.name(), () -> about,
                    new Button("Use", () -> said(Places.choose(m.name()), "This is"),
                            Button.PLAIN).when(() -> !m.current()),
                    forget);
        }
        here.callout(() -> problem != null && problem.startsWith("!") ? Callout.ERROR
                : Callout.OK, () -> problem == null ? null : problem.replaceFirst("^!", ""));
    }

    /** Shows what a map action said, as done or as a problem, and reads the maps again. */
    private void said(String text, String okPart) {
        problem = text.contains(okPart) ? text : "!" + text;
        buildPage();
    }

    private static String megabytes(long bytes) {
        double mb = bytes / 1048576.0;
        return mb < 10 ? String.format(Locale.ROOT, "%.1f MB", mb)
                : String.format(Locale.ROOT, "%.0f MB", mb);
    }

    /** Every setting matching what's typed, in a card per page, titled as in the sidebar. */
    private void buildSearch(String words) {
        if (words.isEmpty()) {
            Card c = card("Search", "Finds any setting by its name or what it does.");
            c.callout(() -> Callout.INFO, () -> "Type in the box at the top right.");
            return;
        }
        String[] each = words.split("\\s+");
        java.util.function.Predicate<String> matches = hay -> {
            String h = hay.toLowerCase(Locale.ROOT);
            for (String w : each) {
                if (!h.contains(w)) {
                    return false;
                }
            }
            return true;
        };
        Card drawing = card("Route", null);
        drawingRows(drawing, hay -> matches.test("route drawing " + hay), true);
        // A card per page, the settings in the order the page shows them.
        Map<String, Card> byPage = new LinkedHashMap<>();
        byPage.put(ROUTE, drawing);
        for (Tuning.Option o : Tuning.all()) {
            String page = pageOf(o.section);
            String hay = pages().get(page) + " " + o.section + " " + o.name.replace('_', ' ')
                    + " " + o.help + " " + Tuning.sections().get(o.section);
            for (Group gr : GROUPS.getOrDefault(o.section, List.of())) {
                if (gr.master() != null && gr.master().equals(o.name)
                        || List.of(gr.options()).contains(o.name)) {
                    hay += " " + gr.title() + " " + gr.about();
                }
            }
            if (matches.test(hay)) {
                byPage.computeIfAbsent(page, pg -> card(pages().get(pg), null)).option(o);
            }
        }
        List<Card> existing=List.copyOf(cards);
        buildTradingOverview();buildTradingLimits();buildTradingCrafts();buildTradingCalculator();
        buildTradingRest();buildTradingCommunity();buildTradingDiscord();buildTradingFiles();
        for(Card c:cards)if(!existing.contains(c)) {
            boolean whole=matches.test("trader "+c.title+" "+(c.about==null?"":c.about));
            if(!whole)c.rows.removeIf(r->r.label==null || !matches.test("trader "+c.title+" "+r.label+" "+(r.desc==null?"":r.desc.get())));
        }
        cards.removeIf(c -> c.rows.isEmpty());
        if (cards.isEmpty()) {
            card("Search", null).callout(() -> Callout.INFO,
                    () -> "No setting matches \"" + search.text.strip() + "\".");
        }
    }

    private Card card(String title, String about) {
        Card c = new Card(title, about);
        cards.add(c);
        return c;
    }

    /** The one column the page's cards go down: at most this wide, in the middle. */
    private static final int COLUMN_W = 520;

    private int columnW() {
        return Math.min(areaW, COLUMN_W);
    }

    private int columnX() {
        return areaX + (areaW - columnW()) / 2;
    }

    // ---- Doing things ------------------------------------------------------------------------

    private Navigator.State state() {
        Navigator nav = client.navigator();
        return nav == null ? Navigator.State.DONE : nav.state();
    }

    /** Sets a setting from what was typed or picked, keeping what it said for its line. */
    private void apply(Tuning.Option o, String text) {
        try {
            String note = o.set(text);
            if (note == null || note.isBlank()) {
                notes.remove(o);
            } else {
                notes.put(o, note);
            }
            changedSettings = true;
        } catch (IllegalArgumentException e) {
            // Half typed, most likely: left as it was until it reads as a value.
            notes.put(o, e.getMessage());
        }
    }

    private void focus(Field f) {
        boolean leaving=focused!=null && focused!=f;
        focused = f;
        if(leaving)saveTrading(false);
        // 26.x reads typed letters through SDL, which only sends them while text input is on:
        // without this, keys like backspace work but letters never arrive.
        if (minecraft != null) {
            if (f != null) {
                minecraft.textInputManager().startTextInput(this);
            } else {
                minecraft.textInputManager().stopTextInput(this);
            }
        }
    }

    @Override
    public void removed() {
        focus(null);
        saveTrading(false);
        if(draft.dirty())new com.goofy.goofyaddons.menu.LiveActions().message(tradingNote==null?draft.summary():tradingNote);
        if (changedSettings) {
            AstarConfig.save();
        }
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    // ---- Input -------------------------------------------------------------------------------

    private double ux(double guiX) {
        return guiX / k;
    }

    private double uy(double guiY) {
        return guiY / k;
    }

    private boolean inArea(double mx, double my) {
        return mx >= areaX && my >= areaY && mx < areaX + areaW && my < areaY + areaH;
    }

    /** Every control under the mouse could be, the fixed ones first. */
    private List<El> all() {
        List<El> list = new ArrayList<>(fixed);
        for (Card c : cards) {
            // Its title's own controls before the title line under them.
            if (c.header != null) {
                list.add(c.header);
            }
            if (c.master != null) {
                list.add(c.master);
            }
            list.add(c.head);
            for (Row r : c.rows) {
                list.addAll(r.controls);
                if (r.slider != null) {
                    list.add(r.slider);
                }
                list.add(r.hint);
                if (r.reset != null) {
                    list.add(r.reset);
                }
            }
        }
        list.addAll(tiles);
        return list;
    }

    private El at(double mx, double my) {
        for (El e : all()) {
            boolean scrolled = !fixed.contains(e);
            if (e.shown && e.in(mx, my) && (!scrolled || inArea(mx, my))) {
                return e;
            }
        }
        return null;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mx = ux(event.x());
        double my = uy(event.y());
        if (dropped != null) {
            // An open list takes the click: a value picks it, anywhere else just closes it.
            Mode<?> list = dropped;
            dropped = null;
            int i = list.itemAt(mx, my);
            if (i >= 0) {
                list.pick(i);
                return true;
            }
            if (list.in(mx, my)) {
                return true;
            }
        }
        El e = at(mx, my);
        if (!(e instanceof Field)) {
            focus(null);
        }
        if (!(e instanceof KeyBind)) {
            listening = null;
        }
        if (e != null && e.enabled()) {
            pressed = e;
            e.click(mx, my, event.button());
            return true;
        }
        return true;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        if (pressed != null) {
            pressed.drag(ux(event.x()));
        }
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        pressed = null;
        return true;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        dropped = null;
        int max = Math.max(0, contentH - areaH);
        int s = scrolls.getOrDefault(key(), 0);
        scrolls.put(key(), Math.clamp(s - (int) Math.round(scrollY * 28), 0, max));
        return true;
    }

    private String key() {
        return page.equals(MACROS)?page+":"+tradingSection.name():page;
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if(bindingCapture!=null) {
            var binding=bindingCapture;bindingCapture=null;
            if(event.isEscape()){notifyTrading("Key change cancelled.");return true;}
            try {
                var gson=new com.google.gson.Gson();
                var cfg=gson.fromJson(gson.toJson(com.goofy.goofyaddons.config.GoofyConfig.INSTANCE),com.goofy.goofyaddons.config.GoofyConfig.class);
                int code=event.input();
                if(client.routeKey.matches(event))throw new IllegalArgumentException("That key opens A* settings; choose another key.");
                for(var other:List.of(com.goofy.goofyaddons.keybinds.GoofyKeybinds.toggleKey,
                        com.goofy.goofyaddons.keybinds.GoofyKeybinds.debugKey,com.goofy.goofyaddons.keybinds.GoofyKeybinds.reloadKey)) {
                    if(other!=binding && other.matches(event))throw new IllegalArgumentException("That key is already used by another trading action.");
                }
                switch(bindingSlot){case 0->cfg.toggleKey=code;case 1->cfg.debugKey=code;default->cfg.reloadKey=code;}
                com.goofy.goofyaddons.config.GoofyConfig.commitSettings(cfg);
                binding.setKey(InputConstants.Type.KEYBOARD.getOrCreate(code));
                net.minecraft.client.KeyMapping.resetMapping();minecraft.options.save();
                notifyTrading("Keybind saved.");
            }catch(Exception failure){notifyTrading(failure.getMessage());}
            return true;
        }
        if (dropped != null && event.isEscape()) {
            dropped = null;
            return true;
        }
        if (listening != null) {
            // The window key's new key, unless it's Esc.
            if (!event.isEscape()) {
                client.windowKey().setKey(InputConstants.getKey(event));
                net.minecraft.client.KeyMapping.resetMapping();
                minecraft.options.save();
            }
            listening = null;
            return true;
        }
        if (focused == null && client.windowKey().matches(event)
                && System.currentTimeMillis() - openedAt > 600) {
            // The key that opened it closes it.
            onClose();
            return true;
        }
        if (focused != null) {
            if (event.isEscape() || event.isConfirmation()) {
                focus(null);
            } else if (event.input() == InputConstants.KEY_BACKSPACE) {
                focused.backspace(event.hasControlDownWithQuirk());
            } else if (event.isPaste()) {
                minecraft.keyboardHandler.getClipboard().chars()
                        .forEach(c -> focused.type((char) c));
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        if (focused != null && event.isAllowedChatCharacter()) {
            for (char c : event.codepointAsString().toCharArray()) {
                focused.type(c);
            }
            return true;
        }
        return false;
    }

    // ---- Drawing -----------------------------------------------------------------------------

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        extractTransparentBackground(g);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        super.extractRenderState(g, mouseX, mouseY, a);
        if (fontsFor != Ui.lettering()) {
            // Picked on the Themes page: the page is laid out again in the new letters.
            init();
        }
        double mx = ux(mouseX);
        double my = uy(mouseY);
        mouseUX = mx;
        El hot = dropped != null && (dropped.itemAt(mx, my) >= 0 || dropped.in(mx, my)) ? dropped
                : at(mx, my);
        if (hot != hovered) {
            hovered = hot;
            hoveredSince = System.currentTimeMillis();
        }
        Theme.Colours th = t();
        g.pose().pushMatrix();
        g.pose().scale(k, k);
        // Opening, the window grows from a little smaller with a small bounce.
        double open = Math.min(1, (System.currentTimeMillis() - openedAt) / 220.0);
        if (open < 1) {
            float grow = (float) (0.9 + 0.1 * backOut(open));
            float midX = winX + winW / 2f;
            float midY = winY + winH / 2f;
            g.pose().translate(midX, midY);
            g.pose().scale(grow, grow);
            g.pose().translate(-midX, -midY);
        }

        // The window over the whole screen, and its sidebar.
        Panels.rect(g, winX, winY, winW, winH, 0, th.bg);
        // The whole window is a piece of the loading screen's map: a search solving one random
        // maze after another, with the sidebar and the cards laid over it.
        g.enableScissor(winX + 1, winY + 1, winX + winW - 1, winY + winH - 1);
        MazeBackground.get().draw(g, winX + 1, winY + 1, winW - 2, winH - 2, 0.2f);
        g.disableScissor();
        Panels.rect(g, winX, winY, SIDE_W, winH, 0, th.bgPanel);
        // A double line between the sidebar and the page, and one under the top bar.
        Panels.rect(g, winX + SIDE_W, winY, 1, winH, 0, th.panelBorder);
        Panels.rect(g, winX + SIDE_W + 1, winY, 1, winH, 0, th.bg);
        Panels.rect(g, winX + SIDE_W + PAD, winY + HEAD_H, winW - SIDE_W - 2 * PAD, 1, 0,
                th.panelBorder);
        Panels.rect(g, winX + 8, winY + HEAD_H, SIDE_W - 16, 1, 0, th.panelBorder);
        // The logo in its dark box, then "A* Client" and the version beside it.
        int lx = winX + 8;
        int ly = winY + (HEAD_H - LOGO) / 2;
        Pixel.panel(g, lx - 1, ly - 1, LOGO + 2, LOGO + 2, th.panelBorder, th.bg);
        logo(g, lx, ly);
        int tx = lx + LOGO + 8;
        text(g, "A* Client", strong, tx, winY + HEAD_H / 2 - 9, th.text);
        text(g, "v" + VERSION, small, tx, winY + HEAD_H / 2 + 3, th.textDim);
        userCard(g, th);
        // The page's title and what it's for, in the top bar.
        String heading = page.equals(SEARCH) ? "Search" : pages().get(page);
        int room = hudButton.ex - (winX + SIDE_W + PAD + 4) - 8;
        text(g, fit(heading, title, room), title, winX + SIDE_W + PAD + 4, winY + 13, th.text);
        String feedback=page.equals(MACROS)?tradingFeedback():null;
        text(g, fit(feedback!=null?feedback:pageAbout(page), small, room), small, winX + SIDE_W + PAD + 4, winY + 30,
                feedback!=null?Callout.WARN:th.textDim);

        for (El e : fixed) {
            e.draw(g, e == hot);
        }

        // The page, scrolled, inside its area.
        g.enableScissor(areaX - 4, areaY - 4, areaX + areaW + 4, areaY + areaH);
        int scroll = scrolls.getOrDefault(key(), 0);
        int cardsH = cards.isEmpty() ? -GAP : layoutCards(scroll);
        contentH = tiles.isEmpty() ? cardsH : cardsH + GAP + layoutTiles(scroll - cardsH - GAP);
        int max = Math.max(0, contentH - areaH);
        if (scroll > max) {
            scrolls.put(key(), max);
        }
        for (Card c : cards) {
            c.draw(g, hot);
        }
        for (Tile tile : tiles) {
            tile.draw(g, tile == hot);
        }
        g.disableScissor();
        if (max > 0) {
            int barH = Math.max(20, areaH * areaH / contentH);
            int barY = areaY + (areaH - barH) * Math.min(scroll, max) / max;
            Panels.rect(g, winX + winW - 6, barY, 3, barH, 1, th.slate);
        }
        // An open list of choices on top of the page.
        if (dropped != null && !dropped.shown) {
            dropped = null;
        }
        if (dropped != null) {
            g.nextStratum();
            dropped.drawList(g, mx, my);
        }
        // Help for what's under the mouse, after a moment, on top of everything.
        if (dropped == null && hot != null && hot.tip() != null
                && System.currentTimeMillis() - hoveredSince > 450) {
            g.nextStratum();
            tooltip(g, hot.tip(), (int) mx, (int) my);
        }
        if(feedback!=null && dropped==null && mx>=winX+SIDE_W && my>=winY+28 && my<winY+40 && mx<hudButton.ex) {
            g.nextStratum();tooltip(g,feedback,(int)mx,(int)my);
        }
        g.pose().popMatrix();
    }

    /** A card of help by the mouse, in the theme's colours, kept inside the screen. */
    private void tooltip(GuiGraphicsExtractor g, String tip, int mx, int my) {
        Theme.Colours th = t();
        List<FormattedCharSequence> lines = new ArrayList<>();
        for (String para : tip.split("\n\n")) {
            if (!lines.isEmpty()) {
                lines.add(FormattedCharSequence.EMPTY);
            }
            lines.addAll(font.split(Ui.text(para, small), 200));
        }
        int w = 0;
        for (FormattedCharSequence l : lines) {
            w = Math.max(w, font.width(l));
        }
        w += 16;
        int h = lines.size() * 10 + 12;
        int screenW = Math.round(width / k);
        int screenH = Math.round(height / k);
        int at = mx + 10 + w > screenW ? mx - 10 - w : mx + 10;
        int top = Math.clamp(my + 10, 2, Math.max(2, screenH - h - 2));
        Pixel.panel(g, at, top, w, h, cardEdge(th), th.bgPanel);
        int yy = top + 7;
        for (FormattedCharSequence l : lines) {
            g.text(font, l, at + 8, yy, th.text, false);
            yy += 10;
        }
    }

    /** How far the page still has to slide in from the right, in units. */
    private int slide() {
        double t = Math.min(1, (System.currentTimeMillis() - pageAt) / 160.0);
        double u = 1 - t;
        return (int) Math.round(16 * u * u * u);
    }

    /** Eases out past 1 and back, for the opening bounce. */
    private static double backOut(double t) {
        double c = 1.6;
        double u = t - 1;
        return 1 + (c + 1) * u * u * u + c * u * u;
    }

    /**
     * Who's playing, at the bottom of the sidebar: their face, their name, and which map this
     * is and what .A* is doing, by a sparkle that lights up while it's going.
     */
    private void userCard(GuiGraphicsExtractor g, Theme.Colours th) {
        int x = winX + 8;
        int w = SIDE_W - 16;
        int y = winY + winH - 46;
        Pixel.panel(g, x, y, w, 38, th.panelBorder, th.bg);
        int face = 22;
        int fx = x + 8;
        int fy = y + 8;
        Pixel.panel(g, fx - 2, fy - 2, face + 4, face + 4, th.slate, th.bg);
        if (minecraft.player != null) {
            net.minecraft.client.gui.components.PlayerFaceExtractor.extractRenderState(g,
                    minecraft.player.getSkin(), fx, fy, face);
        }
        String name = minecraft.player == null ? minecraft.getUser().getName()
                : minecraft.player.getName().getString();
        int textX = fx + face + 7;
        int room = x + w - 6 - textX;
        text(g, fit(name, strong, room), strong, textX, y + 9, th.text);
        Places.Place place = Places.current();
        boolean going = state() != Navigator.State.DONE;
        String line = going ? stateWord() : place == null ? "Unknown map" : place.name;
        text(g, fit(line, small, room - 8), small, textX + 8, y + 23, going ? th.highlight
                : th.textDim);
        Pixel.sparkle(g, textX + 2, y + 26, 3, going ? th.accentBright : th.accent,
                th.highlight, th.bg);
    }

    private String stateWord() {
        return switch (state()) {
            case DONE -> "Idle";
            case PLANNING -> "Planning a route";
            case DRIVING -> "Going";
            case PAUSED -> "Paused";
        };
    }

    /** Places the cards one under another, down the middle; returns the height used. */
    private int layoutCards(int scroll) {
        int y = 0;
        for (Card c : cards) {
            y += c.layout(columnX() + slide(), areaY + y - scroll, columnW()) + GAP;
        }
        return y - GAP;
    }

    private int layoutTiles(int scroll) {
        int columns = Math.clamp((columnW() + GAP) / (160 + GAP), 2, 4);
        int w = (columnW() - GAP * (columns - 1)) / columns;
        int h = 64;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).at(columnX() + slide() + (i % columns) * (w + GAP),
                    areaY + (i / columns) * (h + GAP) - scroll, w, h);
        }
        int rows = (tiles.size() + columns - 1) / columns;
        return rows * (h + GAP) - GAP;
    }

    /** Window text: in capitals, letters spaced out like a pixel-art game's ({@link PixelText}). */
    private void text(GuiGraphicsExtractor g, String s, FontDescription f, int at, int top,
            int colour) {
        PixelText.draw(g, font, s, f, at, top, colour);
    }

    /**
     * A small pixel arrowhead centred on {@code (cx, cy)}, pointing {@code '^'} up,
     * {@code 'v'} down or {@code '>'} right.
     */
    private static void arrow(GuiGraphicsExtractor g, int cx, int cy, char dir, int colour) {
        for (int i = 0; i < 3; i++) {
            int len = 2 * i + 1;
            switch (dir) {
                case '^' -> Panels.rect(g, cx - i, cy - 1 + i, len, 1, 0, colour);
                case 'v' -> Panels.rect(g, cx - i, cy + 1 - i, len, 1, 0, colour);
                default -> Panels.rect(g, cx + 1 - i, cy - i, 1, len, 0, colour);
            }
        }
    }

    private int width(String s, FontDescription f) {
        return PixelText.width(font, s, f);
    }

    /** As much of {@code s} as fits in {@code w}, with "..." if cut. */
    private String fit(String s, FontDescription f, int w) {
        if (width(s, f) <= w) {
            return s;
        }
        int end = s.length();
        while (end > 0 && width(s.substring(0, end) + "...", f) > w) {
            end--;
        }
        return s.substring(0, end).stripTrailing() + "...";
    }

    /**
     * {@code s} in lines, the first at most {@code first} wide and the rest {@code rest}, at
     * most {@code most} lines (the last cut with "..." if there's more).
     */
    private List<String> wrap(String s, FontDescription f, int first, int rest, int most) {
        List<String> lines = new ArrayList<>();
        String left = s.strip();
        while (!left.isEmpty()) {
            int w = lines.isEmpty() ? first : rest;
            if (lines.size() == most - 1 || width(left, f) <= w) {
                lines.add(fit(left, f, w));
                break;
            }
            int cut = left.length();
            while (cut > 0 && (width(left.substring(0, cut), f) > w
                    || (cut < left.length() && left.charAt(cut) != ' '))) {
                cut--;
            }
            if (cut == 0) {
                // No space to break at in what fits: if nothing fits on a short first line,
                // start on the next one; else break mid-word.
                if (lines.isEmpty() && w < rest) {
                    lines.add("");
                    continue;
                }
                cut = Math.max(1, fit(left, f, w).length() - 3);
            }
            lines.add(left.substring(0, cut).stripTrailing());
            left = left.substring(cut).strip();
        }
        return lines;
    }

    private static String capital(String s) {
        return s.isEmpty() ? s : s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    /** "drop_per_block" as "Drop per block". */
    private static String label(String name) {
        return capital(name.replace('_', ' '));
    }

    private static String when(Tuning.Effect effect) {
        return switch (effect) {
            case ROUTES, NEXT_ROUTE -> "Used from the next route.";
            case HOPS -> "Used from the next route with teleports.";
            case NOW -> "Used at once.";
        };
    }

    // ---- Cards and rows ----------------------------------------------------------------------

    /** The cards folded shut, by page and title: kept while the game runs. */
    private static final java.util.Set<String> FOLDED = new java.util.HashSet<>();

    /**
     * A card of rows: a title, what it's for, and its settings. A click on its title folds it
     * shut or opens it; a card with a switch shows its settings only while it's on.
     */
    private final class Card {
        final String title;
        final String about;
        final List<Row> rows = new ArrayList<>();
        /** A small button by the title. */
        El header;
        /** Its on/off switch, by the title. */
        Check master;
        /** The title line, which folds it. */
        final Head head = new Head();
        /** Shows the logo big under its title. */
        boolean preview;
        int artY;
        int artS;
        int cx;
        int cy;
        int cw;
        int ch;
        int headH;
        List<String> aboutLines = List.of();

        Card(String title, String about) {
            this.title = title;
            this.about = about;
        }

        String foldKey() {
            return page + "/" + title;
        }

        /** Whether its rows show: not folded, and on if it has a switch. */
        boolean open() {
            return title == null || !FOLDED.contains(foldKey())
                    && (master == null || master.get.getAsBoolean());
        }

        Row row(String label, Supplier<String> desc, El... controls) {
            Row r = new Row(label, desc, List.of(controls));
            rows.add(r);
            return r;
        }

        void callout(Supplier<Integer> kind, Supplier<String> text) {
            Row r = new Row(null, null, List.of());
            r.callout = kind;
            r.calloutText = text;
            rows.add(r);
        }

        /** Gives it an on/off switch by its title. */
        void master(BooleanSupplier get, Consumer<Boolean> set, String tip) {
            master = new Check(get, on -> {
                set.accept(on);
                changedSettings = true;
            }).toggle();
            master.tip(tip);
        }

        /** A "Reset" by the title, for when any of these settings has been changed. */
        void resetAll(List<Tuning.Option> options) {
            if (options.isEmpty()) {
                return;
            }
            header = new Button("Reset", () -> {
                for (Tuning.Option o : options) {
                    o.reset();
                    notes.remove(o);
                }
                changedSettings = true;
            }, Button.GHOST).when(() -> options.stream().anyMatch(Tuning.Option::changed))
                    .tip("Put this card's settings back as they started.");
        }

        /**
         * A line for a {@link Tuning} setting: its name, a slider and its value for a number,
         * a switch or a list for the rest. What it does is in its tooltip; the line under the
         * name only says when something's wrong with what was typed.
         */
        void option(Tuning.Option o) {
            El control = switch (o) {
                case Tuning.Num n -> numberField(n);
                case Tuning.Flag f -> new Check(f::get, on -> apply(o, on.toString())).toggle();
                case Tuning.Choice c when c.choices.size() <= 3 -> new Segmented<>(c.choices,
                        c::get, AstarScreen::choiceName, v -> apply(o, v));
                case Tuning.Choice c -> new Mode<>(c.choices, c::get, AstarScreen::choiceName,
                        v -> apply(o, v));
            };
            Row r = row(optionName(o), () -> notes.get(o), control);
            r.option = o;
            r.changed = o::changed;
            r.tip = o.help + "\n\n" + capital(o.range()) + ", default " + o.defaultText() + ". "
                    + when(o.effect) + (o instanceof Tuning.Flag ? ""
                    : " Right click to put it back.");
            control.tip(r.tip);
            if (o instanceof Tuning.Num n) {
                r.slider = new Slider(n, o);
                r.slider.tip(r.tip);
            }
        }

        /** One line for two numbers that are the ends of a range, with a two-knob slider. */
        void range(String name, Tuning.Num lo, Tuning.Num hi) {
            Row r = row(name, () -> notes.containsKey(lo) ? notes.get(lo) : notes.get(hi),
                    numberField(lo), numberField(hi));
            r.option = lo;
            r.changed = () -> lo.changed() || hi.changed();
            r.tip = lo.help + "\n\n" + hi.help + "\n\nRight click to put it back.";
            r.controls.forEach(e -> e.tip(r.tip));
            r.slider = new RangeSlider(lo, hi);
            r.slider.tip(r.tip);
        }

        /** Places everything at this spot; returns the card's height. */
        int layout(int at, int top, int w) {
            cx = at;
            cy = top;
            cw = w;
            int yy = top + 10;
            boolean open = open();
            if (title != null) {
                yy += 14;
                aboutLines = about == null ? List.of() : wrap(about, small, w - 20, w - 20, 1);
                yy += 11 * aboutLines.size();
                headH = yy - top;
                int right = at + w - 10;
                if (master != null) {
                    master.at(right - master.prefW(), top + 6, master.prefW(), 18);
                    right -= master.prefW() + 6;
                }
                if (header != null) {
                    header.at(right - header.prefW(), top + 7, header.prefW(), 16);
                }
                head.at(at, top, w, Math.max(24, headH));
                yy += open && !rows.isEmpty() ? 10 : 2;
            }
            if (!open || rows.isEmpty() && !preview) {
                hideRows();
                ch = yy - top + 4;
                return ch;
            }
            if (preview) {
                // About 96 units, at a whole number of screen pixels per pixel of the art.
                int m = Math.max(1, Math.round(96f * px / 64));
                artS = Math.min(w - 24, 64 * m / px);
                artY = yy + 2;
                yy += artS + 10;
            }
            for (Row r : rows) {
                yy += r.layout(at + 10, yy, w - 20);
            }
            ch = yy - top + 4;
            return ch;
        }

        void draw(GuiGraphicsExtractor g, El hot) {
            if (cy > areaY + areaH || cy + ch < areaY - 4) {
                hideAll();
                return;
            }
            Theme.Colours th = t();
            boolean open = open();
            boolean off = master != null && !master.get.getAsBoolean();
            Pixel.panel(g, cx, cy, cw, ch, cardEdge(th), th.bgPanel);
            if (title != null) {
                head.shown = true;
                boolean lit = hot == head;
                int titleRoom = (header != null && header.enabled() ? header.ex
                        : master != null ? master.ex : cx + cw - 10) - cx - 30;
                // A fold arrow, then the title: dim while its switch is off.
                arrow(g, cx + 13, cy + 14, FOLDED.contains(foldKey()) ? '>' : 'v',
                        lit ? th.text : th.slate);
                text(g, fit(title, strong, titleRoom), strong, cx + 21, cy + 11,
                        off ? th.textDim : lit ? th.highlight : th.text);
                for (int i = 0; i < aboutLines.size(); i++) {
                    text(g, aboutLines.get(i), small, cx + 10, cy + 24 + 11 * i, th.textDim);
                }
                if (open && !rows.isEmpty()) {
                    int line = cy + headH + 4;
                    Panels.rect(g, cx + 10, line, cw - 20, 1, 0, th.panelBorder);
                }
                if (header != null) {
                    header.shown = header.enabled() && open;
                    if (header.shown) {
                        header.draw(g, header == hot);
                    }
                }
                if (master != null) {
                    master.shown = true;
                    master.draw(g, master == hot);
                }
            }
            if (!open) {
                return;
            }
            if (preview) {
                int ax = cx + (cw - artS) / 2;
                Pixel.panel(g, ax - 3, artY - 3, artS + 6, artS + 6, th.panelBorder, th.bg);
                LoadingArt art = LoadingArt.logo();
                if (art != null) {
                    art.draw(g, ax, artY, artS, artS, 1);
                }
            }
            for (Row r : rows) {
                r.draw(g, hot);
            }
        }

        private void hideAll() {
            head.shown = false;
            if (header != null) {
                header.shown = false;
            }
            if (master != null) {
                master.shown = false;
            }
            hideRows();
        }

        private void hideRows() {
            for (Row r : rows) {
                r.hint.shown = false;
                r.controls.forEach(e -> e.shown = false);
                if (r.slider != null) {
                    r.slider.shown = false;
                }
                if (r.reset != null) {
                    r.reset.shown = false;
                }
            }
        }

        /** The card's title line: a click folds the card or opens it. */
        private final class Head extends El {
            @Override
            void draw(GuiGraphicsExtractor g, boolean hot) {}

            @Override
            String tip() {
                return null;
            }

            @Override
            void click(double mx, double my, int button) {
                if (!FOLDED.remove(foldKey())) {
                    FOLDED.add(foldKey());
                }
            }
        }
    }

    /** A box for a number setting, following it while not typed in, its unit after it. */
    private Field numberField(Tuning.Num n) {
        Field f = new Field(n.text(), 12, "", s -> apply(n, s),
                c -> Character.isDigit(c) || c == '.' || c == '-');
        f.live = n::text;
        f.unit = unit(n);
        f.reset = () -> {
            n.reset();
            notes.remove(n);
            changedSettings = true;
        };
        return f.wide(64);
    }

    /** A number's unit, short, after its value. */
    private static String unit(Tuning.Num n) {
        return switch (n.name) {
            case "goal_reach", "look_ahead", "strafe_look", "ridge_look", "view_height",
                    "lead_ahead", "etherwarp_min", "it_min" -> "blocks";
            case "pitch_min", "pitch_max", "strafe_lead", "hold", "hold_strafing", "drift",
                    "drift_pitch", "sweep_over", "flick_over" -> "\u00b0";
            case "pitch_speed", "turn_speed" -> "\u00b0/s";
            case "air_turn_accel", "turn_accel" -> "\u00b0/s\u00b2";
            case "aim_time", "sweep_time", "sweep_time_per_doubling" -> "s";
            case "whole_map_size" -> "M";
            default -> "";
        };
    }

    /** What a setting is called in the window, where its name in the file reads badly. */
    private static String optionName(Tuning.Option o) {
        return switch (o.name) {
            case "drop_per_block" -> "Drop, per block";
            case "etherwarp_cost" -> "Etherwarp cost";
            case "etherwarp_min" -> "Shortest etherwarp";
            case "it_cost" -> "Transmission cost";
            case "it_min" -> "Shortest transmission";
            case "aim_variation" -> "Aim time varies";
            case "goal_reach" -> "Goal reach";
            case "look_ahead_per_speed" -> "Look ahead, per speed";
            case "strafe_look_per_speed" -> "Strafe look, per speed";
            case "ridge_look_per_speed" -> "Ridge look, per speed";
            case "air_turn_accel" -> "Speed-up in the air";
            case "turn_accel" -> "Turn speed-up";
            case "sweep_time_per_doubling" -> "Sweep time, per doubling";
            case "overshoot_out" -> "Overshoot, out";
            case "overshoot_in" -> "Overshoot, in";
            case "hand" -> "Mouse hand";
            case "strafe" -> "Strafe round corners";
            case "save_chunks" -> "Save chunks you see";
            case "auto_detect" -> "Tell maps apart";
            case "match" -> "Likeness needed";
            case "new_after" -> "New map after";
            case "bundled_mines" -> "Bundled Mines map";
            case "whole_map" -> "Plan over the whole map";
            case "whole_map_size" -> "Biggest whole map";
            case "keep_files" -> "Keep map files";
            default -> label(o.name);
        };
    }

    /** A choice's value as shown: "easeInOutSine" as "Sine, in and out", "right" as "Right". */
    static String choiceName(String v) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("ease(InOut|In|Out)(\\w+)").matcher(v);
        if (m.matches()) {
            String way = switch (m.group(1)) {
                case "InOut" -> "in and out";
                case "In" -> "in";
                default -> "out";
            };
            return m.group(2) + ", " + way;
        }
        return capital(v.replaceAll("([a-z])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT));
    }

    /** A card's edge: a step lighter than the boxes inside it. */
    private static int cardEdge(Theme.Colours th) {
        return Theme.mix(th.panelBorder, th.slate, 0.35) | 0xFF000000;
    }

    /** Callout kinds: their colour. */
    private static final class Callout {
        static final int INFO = 0xFF5B9BE6;
        static final int OK = 0xFF4FC07A;
        static final int WARN = 0xFFE0A040;
        static final int ERROR = 0xFFE05A5A;
    }

    /** One line of a card: a name and a line under it on the left, controls on the right. */
    private final class Row {
        final String label;
        final Supplier<String> desc;
        final List<El> controls;
        Tuning.Option option;
        /** Whether its setting isn't at its default. */
        BooleanSupplier changed = () -> false;
        El slider;
        Button reset;
        String tip;
        /** Controls across the whole line, evenly, instead of on the right. */
        boolean spread;
        Supplier<Integer> callout;
        Supplier<String> calloutText;
        int rx;
        int ry;
        int rw;
        int rh;
        /** How wide its name may be. */
        int labelW;
        /** Its name, for its tooltip. */
        final Hint hint = new Hint();
        List<String> descLines = List.of();
        boolean stacked;

        Row(String label, Supplier<String> desc, List<El> controls) {
            this.label = label;
            this.desc = desc;
            this.controls = controls;
        }

        int layout(int at, int top, int w) {
            rx = at;
            ry = top;
            rw = w;
            stacked=false;
            if (callout != null) {
                String s = calloutText.get();
                rh = s == null || s.isBlank() ? 0 : 14+10*wrap(s,small,w-30,w-30,5).size();
                return rh;
            }
            int controlsWidth=controls.stream().mapToInt(El::prefW).sum()+6*Math.max(0,controls.size()-1);
            if(reset!=null)controlsWidth+=reset.prefW()+6;
            if(label!=null && !controls.isEmpty() && controlsWidth+width(label,body)+12>w) {
                stacked=true;
                labelW=w;
                hint.at(at,top,w,18);
                hint.tip(tip);
                String description=desc==null?null:desc.get();
                descLines=description==null?List.of():wrap(description,small,w,w,4);
                int yy=top+18+descLines.size()*11+5,xx=at;
                List<El> items=new ArrayList<>(controls);
                if(reset!=null)items.add(reset);
                for(El e:items) {
                    int ew=Math.min(w,e.prefW());
                    if(xx>at && xx+ew>at+w){xx=at;yy+=24;}
                    e.at(xx,yy,ew,18);xx+=ew+6;
                }
                rh=yy+24-top;
                if(slider!=null){slider.at(at,top+rh,w,10);rh+=14;}
                return rh;
            }
            int cyy = top + (ROW_H - 18) / 2;
            if (spread) {
                int each = (w - 6 * (controls.size() - 1)) / controls.size();
                for (int i = 0; i < controls.size(); i++) {
                    controls.get(i).at(at + i * (each + 6), cyy, each, 18);
                }
                rh = 26;
                return rh;
            }
            // Controls all 18 high on one right edge; a lone list as wide as the others (a
            // number's box stays narrow, leaving its line room in the wider capitals).
            int right = at + w;
            for (int i = controls.size() - 1; i >= 0; i--) {
                El e = controls.get(i);
                int ew = e.prefW();
                if (controls.size() == 1 && e instanceof Mode) {
                    ew = Math.max(ew, CONTROL_W);
                }
                e.at(right - ew, cyy, ew, 18);
                right -= ew + 6;
            }
            if (reset != null) {
                int ew = reset.prefW();
                reset.at(right - ew, cyy, ew, 18);
            }
            // A number's slider on the same line, between its name and its value.
            labelW = controlsLeft() - at - 8;
            if (slider != null) {
                labelW = Math.min(w * 2 / 5, 150);
                int sx = at + labelW;
                slider.at(sx, cyy + 4, Math.max(20, controlsLeft() - 10 - sx), 10);
            }
            // The line under the name, if it has one: as much as fits beside the controls,
            // the rest wrapped across the row below them.
            descLines = List.of();
            String d = desc == null ? null : desc.get();
            if (label != null && d != null && !d.isEmpty()) {
                descLines = wrap(d, small, labelW, w, 2);
            }
            rh = descLines.isEmpty() ? ROW_H : ROW_H + 4 + (descLines.size() - 1) * 11;
            hint.at(at, top, Math.max(0, labelW), rh);
            hint.tip(tip);
            return rh;
        }

        int controlsLeft() {
            int left = rx + rw;
            for (El e : controls) {
                left = Math.min(left, e.ex);
            }
            if (reset != null && reset.shown) {
                left = Math.min(left, reset.ex);
            }
            return left;
        }

        void draw(GuiGraphicsExtractor g, El hot) {
            if (callout != null) {
                String s = calloutText.get();
                if (s != null && !s.isBlank()) {
                    drawCallout(g, rx, ry + 3, rw, rh-6, callout.get(), s);
                }
                return;
            }
            boolean in = ry + rh > areaY && ry < areaY + areaH;
            hint.shown = in && tip != null;
            for (El e : controls) {
                e.shown = in;
            }
            if (slider != null) {
                slider.shown = in;
            }
            if (reset != null) {
                reset.shown = in && changed.getAsBoolean();
            }
            if (!in) {
                return;
            }
            if (label != null) {
                int ly = descLines.isEmpty() ? ry + (ROW_H - 7) / 2 : ry + 5;
                text(g, fit(label, body, labelW), body, rx, ly,
                        hint == hovered && tip != null ? t().highlight : t().text);
                if (changed.getAsBoolean()) {
                    // Changed from how it started: a dot by its name.
                    Panels.rect(g, rx - 6, ly + 2, 3, 3, 1, t().accentBright);
                }
                boolean note = option != null && (notes.containsKey(option)
                        || slider instanceof RangeSlider rs && notes.containsKey(rs.hi));
                for (int i = 0; i < descLines.size(); i++) {
                    text(g, descLines.get(i), small, rx, ry + 17 + 11 * i,
                            note ? Callout.WARN : t().textDim);
                }
            }
            for (El e : controls) {
                e.draw(g, e == hot);
            }
            if (slider != null) {
                slider.draw(g, slider == hot);
            }
            if (reset != null && reset.shown) {
                reset.draw(g, reset == hot);
            }
        }
    }

    /** A row's height without a line under its name. */
    private static final int ROW_H = 26;

    /** A spot that only shows a tooltip, such as a setting's name. */
    private final class Hint extends El {
        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {}
    }

    private void drawCallout(GuiGraphicsExtractor g, int at, int top, int w, int h, int colour,
            String s) {
        Theme.Colours th = t();
        // Information in slate like the rest; only success, warnings and errors keep a colour.
        boolean info = colour == Callout.INFO;
        int ink = info ? th.slateLight : colour;
        Pixel.panel(g, at, top, w, h, info ? th.panelBorder : colour, th.bg);
        Pixel.panel(g, at + 7, top + 5, 10, 10, ink, th.bg);
        if (colour == Callout.OK) {
            // A tick, in pixels.
            int[][] tick = {{0, 2}, {1, 3}, {2, 4}, {3, 3}, {4, 2}, {5, 1}};
            for (int[] p : tick) {
                Panels.rect(g, at + 9 + p[0], top + 8 + p[1], 1, 1, 0, ink);
            }
        } else {
            String mark = colour == Callout.INFO ? "i" : "!";
            int mw = width(mark, small);
            text(g, mark, small, at + 12 - mw / 2, top + 6, ink);
        }
        var lines=wrap(s,small,w-30,w-30,5);
        for(int i=0;i<lines.size();i++)text(g,lines.get(i),small,at+23,top+6+10*i,th.text);
    }

    // ---- Controls ----------------------------------------------------------------------------

    /** A control: where it is, in units, and how it's drawn and used. */
    private abstract class El {
        int ex;
        int ey;
        int ew;
        int eh;
        boolean shown = true;
        String tip;
        BooleanSupplier when = () -> true;

        El at(int at, int top, int w, int h) {
            ex = at;
            ey = top;
            ew = w;
            eh = h;
            return this;
        }

        boolean in(double mx, double my) {
            return mx >= ex && my >= ey && mx < ex + ew && my < ey + eh;
        }

        boolean enabled() {
            return when.getAsBoolean();
        }

        El tip(String tip) {
            this.tip = tip;
            return this;
        }

        String tip() {
            return tip;
        }

        int prefW() {
            return ew;
        }

        abstract void draw(GuiGraphicsExtractor g, boolean hot);

        void click(double mx, double my, int button) {}

        void drag(double mx) {}
    }

    private final class Button extends El {
        static final int PLAIN = 0;
        static final int ACCENT = 1;
        /** Text only, for small things like reset. */
        static final int GHOST = 2;

        final String label;
        Runnable action;
        final int style;
        /** Until when a second click does it, for buttons that ask first. */
        long armedUntil;

        Button(String label, Runnable action, int style) {
            this.label = label;
            this.action = action;
            this.style = style;
        }

        Button when(BooleanSupplier when) {
            this.when = when;
            return this;
        }

        @Override
        Button tip(String tip) {
            super.tip(tip);
            return this;
        }

        @Override
        int prefW() {
            return width(label, body) + (style == GHOST ? 10 : 24);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = enabled();
            hot &= on;
            int colour = !on ? th.slate : th.text;
            boolean down = on && pressed == this;
            if (style == GHOST) {
                colour = hot ? th.text : th.textDim;
            } else {
                // A chunky stone button, in one of its five states.
                Pixel.State state = !on ? Pixel.State.DISABLED : down ? Pixel.State.PRESSED
                        : hot ? Pixel.State.HOVER : style == ACCENT ? Pixel.State.ACTIVE
                        : Pixel.State.NORMAL;
                Pixel.button(g, ex, ey, ew, eh, state, th);
            }
            String shown = label;
            if (armedUntil > System.currentTimeMillis()) {
                shown = "Sure?";
                Pixel.outline(g, ex, ey, ew, eh, Callout.ERROR);
            }
            int tw = width(shown, body);
            text(g, shown, body, ex + (ew - tw) / 2, ey + (eh - 7) / 2 + (down ? 1 : 0),
                    colour);
        }

        @Override
        void click(double mx, double my, int button) {
            action.run();
        }
    }

    /** An on/off box, like a ticked square. */
    private final class Check extends El {
        final BooleanSupplier get;
        final Consumer<Boolean> set;

        /** Drawn as a sliding switch instead of a box. */
        boolean toggle;

        Check(BooleanSupplier get, Consumer<Boolean> set) {
            this.get = get;
            this.set = set;
            ew = 12;
        }

        Check toggle() {
            toggle = true;
            ew = 30;
            return this;
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = get.getAsBoolean();
            if (toggle) {
                Pixel.toggle(g, ex, ey + (eh - 16) / 2, ew, 16, on, hot, th);
                return;
            }
            int top = ey + (eh - 12) / 2;
            // A route node: a slate square when off; on, an accent one round a highlight centre.
            int edge = on ? th.accent : hot ? th.slateLight : th.slate;
            Pixel.panel(g, ex, top, ew, 12, edge, th.bg);
            if (on) {
                Panels.rect(g, ex + 4, top + 4, ew - 8, 4, 0, th.highlight);
            }
        }

        @Override
        void click(double mx, double my, int button) {
            set.accept(!get.getAsBoolean());
        }
    }

    /** A few choices side by side, the picked one lit: one click, nothing hidden. */
    private final class Segmented<T> extends El {
        final List<T> values;
        final Supplier<T> get;
        final Function<T, String> label;
        final Consumer<T> set;
        /** What each choice does, for its tooltip. */
        Function<T, String> about = v -> null;

        Segmented(List<T> values, Supplier<T> get, Function<T, String> label, Consumer<T> set) {
            this.values = values;
            this.get = get;
            this.label = label;
            this.set = set;
        }

        int segW(T v) {
            return width(label.apply(v), body) + 18;
        }

        @Override
        int prefW() {
            int w = 2;
            for (T v : values) {
                w += segW(v);
            }
            return Math.max(w, SEG_W);
        }

        /** Where each choice starts, spread over its width. */
        int segX(int i) {
            int total = 0;
            for (T v : values) {
                total += segW(v);
            }
            int extra = ew - 2 - total;
            int x = ex + 1;
            for (int j = 0; j < i; j++) {
                x += segW(values.get(j)) + extra / values.size();
            }
            return x;
        }

        int under(double mx) {
            for (int i = values.size() - 1; i >= 0; i--) {
                if (mx >= segX(i)) {
                    return i;
                }
            }
            return 0;
        }

        @Override
        String tip() {
            String a = about.apply(values.get(under(mouseUX)));
            return a != null ? label.apply(values.get(under(mouseUX))) + ": " + a : tip;
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            Pixel.panel(g, ex, ey, ew, eh, th.slate, th.bgPanel);
            T now = get.get();
            int over = hot ? under(mouseUX) : -1;
            for (int i = 0; i < values.size(); i++) {
                T v = values.get(i);
                int x = segX(i);
                int w = (i == values.size() - 1 ? ex + ew - 1 : segX(i + 1)) - x;
                boolean picked = v.equals(now);
                if (picked) {
                    Pixel.panel(g, x, ey + 1, w, eh - 2, th.accent, th.accentDeep);
                    Panels.rect(g, x + 1, ey + 2, w - 2, 1, 0, th.accent);
                } else if (i == over) {
                    Panels.rect(g, x + 1, ey + 2, w - 2, eh - 4, 0, th.terrainLight);
                }
                if (i > 0 && !picked && !values.get(i - 1).equals(now)) {
                    Panels.rect(g, x, ey + 4, 1, eh - 8, 0, th.panelBorder);
                }
                String s = label.apply(v);
                text(g, s, body, x + (w - width(s, body)) / 2, ey + (eh - 7) / 2,
                        picked ? th.highlight : i == over ? th.text : th.textDim);
            }
        }

        @Override
        void click(double mx, double my, int button) {
            set.accept(values.get(under(mx)));
        }
    }

    /** Colour squares in a line; a click picks one. */
    private final class Swatches extends El {
        static final int S = 14;
        static final int GAP = 4;
        final List<OverlayColour> values;
        final Supplier<OverlayColour> get;
        final Consumer<OverlayColour> set;

        Swatches(List<OverlayColour> values, Supplier<OverlayColour> get,
                Consumer<OverlayColour> set) {
            this.values = values;
            this.get = get;
            this.set = set;
        }

        @Override
        int prefW() {
            return values.size() * (S + GAP) - GAP + 4;
        }

        int under(double mx) {
            int i = (int) ((mx - ex - 2) / (S + GAP));
            return Math.max(0, Math.min(values.size() - 1, i));
        }

        @Override
        String tip() {
            return capital(values.get(under(mouseUX)).id());
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            OverlayColour now = get.get();
            int over = hot ? under(mouseUX) : -1;
            int top = ey + (eh - S) / 2;
            for (int i = 0; i < values.size(); i++) {
                OverlayColour c = values.get(i);
                int x = ex + 2 + i * (S + GAP);
                if (c == now) {
                    Pixel.panel(g, x - 2, top - 2, S + 4, S + 4, th.highlight, th.bg);
                } else if (i == over) {
                    Pixel.panel(g, x - 2, top - 2, S + 4, S + 4, th.slateLight, th.bg);
                }
                Pixel.panel(g, x, top, S, S, th.bg, 0xFF000000 | c.lineNow());
            }
        }

        @Override
        void click(double mx, double my, int button) {
            set.accept(values.get(under(mx)));
        }
    }

    /** A box showing one of a few values; a click steps to the next, a right click back. */
    private final class Mode<T> extends El {
        final List<T> values;
        final Supplier<T> get;
        final Function<T, String> label;
        final Consumer<T> set;
        /** A dot of each value's colour, for colours. */
        Function<T, Integer> swatch;

        Mode(List<T> values, Supplier<T> get, Function<T, String> label, Consumer<T> set) {
            this.values = values;
            this.get = get;
            this.label = label;
            this.set = set;
        }

        @Override
        int prefW() {
            int most = 0;
            for (T v : values) {
                most = Math.max(most, width(label.apply(v), body));
            }
            return Math.max(44, most + 26 + (swatch != null ? 12 : 0));
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean open = dropped == this;
            Pixel.button(g, ex, ey, ew, eh, open ? Pixel.State.ACTIVE
                    : hot ? Pixel.State.HOVER : Pixel.State.NORMAL, th);
            T v = get.get();
            int at = ex + 7;
            if (swatch != null) {
                Pixel.panel(g, at, ey + 5, 8, 8, th.bg, swatch.apply(v));
                at += 12;
            }
            text(g, label.apply(v), body, at, ey + 6, th.text);
            Pixel.chevron(g, ex + ew - 11, ey + eh / 2 - 1, open ? th.highlight : th.slateLight);
        }

        /** A left click opens its list (or closes it); a right click steps back one. */
        @Override
        void click(double mx, double my, int button) {
            // 26.x numbers the buttons as SDL does: left is 1, right is 3.
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                int i = values.indexOf(get.get());
                set.accept(values.get((i + values.size() - 1) % values.size()));
                return;
            }
            dropped = dropped == this ? null : this;
        }

        private static final int ITEM_H = 13;

        int listH() {
            return values.size() * ITEM_H + 4;
        }

        /** Below the box, or above it if it would run out of the window. */
        int listTop() {
            return ey + eh + listH() + 1 > winY + winH ? ey - listH() - 1 : ey + eh + 1;
        }

        /** Which value's line is at this spot in the open list, or -1. */
        int itemAt(double mx, double my) {
            int top = listTop() + 2;
            if (mx < ex || mx >= ex + ew || my < top || my >= top + values.size() * ITEM_H) {
                return -1;
            }
            return (int) ((my - top) / ITEM_H);
        }

        void pick(int i) {
            set.accept(values.get(i));
        }

        /** The open list, over everything else. */
        void drawList(GuiGraphicsExtractor g, double mx, double my) {
            Theme.Colours th = t();
            int top = listTop();
            Pixel.panel(g, ex, top, ew, listH(), th.accent, th.bgPanel);
            int under = itemAt(mx, my);
            T now = get.get();
            for (int i = 0; i < values.size(); i++) {
                T v = values.get(i);
                int yy = top + 2 + i * ITEM_H;
                if (i == under) {
                    Panels.rect(g, ex + 2, yy, ew - 4, ITEM_H, 0, th.terrainLight);
                }
                boolean picked = v.equals(now);
                int at = ex + 7;
                if (picked) {
                    Panels.rect(g, ex + 3, yy + 5, 2, 3, 0, th.accent);
                }
                if (swatch != null) {
                    Pixel.panel(g, at, yy + 2, 8, 8, th.bg, swatch.apply(v));
                    at += 12;
                }
                text(g, fit(label.apply(v), body, ew - (at - ex) - 4), body, at, yy + 3,
                        picked ? th.highlight : i == under ? th.text : th.textDim);
            }
        }
    }

    /** A box to type in. */
    private class Field extends El {
        String text;
        final int max;
        final String hint;
        final Consumer<String> changed;
        final java.util.function.Predicate<Character> allowed;
        /** What to show while not being typed in, if it follows something else. */
        Supplier<String> live;
        boolean secret;
        Field masked(){secret=true;return this;}
        /** A unit, dim after the value. */
        String unit = "";
        /** What a right click does: puts a setting back. */
        Runnable reset;

        Field(String text, int max, String hint, Consumer<String> changed,
                java.util.function.Predicate<Character> allowed) {
            this.text = text;
            this.max = max;
            this.hint = hint;
            this.changed = changed;
            this.allowed = allowed;
        }

        Field wide(int w) {
            ew = w;
            return this;
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = focused == this;
            if (!on && live != null) {
                text = live.get();
            }
            // Sunk into the card: darker than it, the edge lighting up when typed in.
            Pixel.panel(g, ex, ey, ew, eh, on ? th.accent : hot ? th.slateLight
                    : th.panelBorder, th.bg);
            int room = ew - 16 - (unit.isEmpty() || on ? 0 : width(unit, small) + 3);
            if (text.isEmpty() && !on) {
                text(g, fit(hint, body, room), body, ex + 8, ey + 6, t().slate);
                return;
            }
            // Preserve both private-key masking and the upstream leading-value display.
            String shown = secret?"*".repeat(text.length()):text;
            while (width(shown, body) > room && !shown.isEmpty()) {
                shown = on ? shown.substring(1) : shown.substring(0, shown.length() - 1);
            }
            text(g, shown, body, ex + 8, ey + 6, t().text);
            if (!unit.isEmpty() && !on) {
                text(g, unit, small, ex + ew - 6 - width(unit, small), ey + 7, th.textDim);
            }
            if (on && System.currentTimeMillis() / 500 % 2 == 0) {
                int cx = ex + 9 + width(shown, body);
                Panels.rect(g, cx, ey + 5, 1, 8, 0, th.accentBright);
            }
        }

        @Override
        void click(double mx, double my, int button) {
            if (reset != null && button == InputConstants.MOUSE_BUTTON_RIGHT) {
                focus(null);
                reset.run();
                return;
            }
            focus(this);
        }

        void type(char c) {
            if (text.length() < max && allowed.test(c)) {
                text += c;
                changed.accept(text);
            }
        }

        void backspace(boolean word) {
            if (text.isEmpty()) {
                return;
            }
            text = word ? text.replaceAll("\\S*\\s*$", "") : text.substring(0, text.length() - 1);
            changed.accept(text);
        }
    }

    /** A number's slider, across its line: dragged, it sets the number. */
    private final class Slider extends El {
        final Tuning.Num n;
        final Tuning.Option o;

        Slider(Tuning.Num n, Tuning.Option o) {
            this.n = n;
            this.o = o;
        }

        /**
         * The slider's top end: about three times the default rather than the most the file
         * takes, so the usual values use the slider's length; further if the value is past it.
         * Anything past that can still be typed.
         */
        private double top() {
            double soft = n.def > 0 ? Math.max(3 * n.def, n.def + 1) : n.max;
            double hi = Math.min(n.max, soft);
            double v = n.get();
            return v > hi ? Math.min(n.max, v * 1.25) : hi;
        }

        /** Wide ranges go by ratio, so the small end isn't squeezed. */
        private boolean log() {
            return n.min > 0 && top() / n.min >= 100;
        }

        private double toT(double v) {
            double hi = top();
            v = Math.clamp(v, n.min, hi);
            return log() ? Math.log(v / n.min) / Math.log(hi / n.min)
                    : (v - n.min) / (hi - n.min);
        }

        private double fromT(double t) {
            double hi = top();
            return log() ? n.min * Math.pow(hi / n.min, t) : n.min + t * (hi - n.min);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            int track = ey + eh / 2 - 1;
            int kx = ex + (int) Math.round(toT(n.get()) * ew);
            int dx = ex + (int) Math.round(toT(n.def) * ew);
            // A route: a start node, a solid accent line up to the knob, and a dotted line on
            // from there.
            Panels.rect(g, ex + 6, track, Math.max(0, kx - ex - 6), 2, 0, th.accent);
            for (int x = kx + 7; x + 1 <= ex + ew; x += 3) {
                Panels.rect(g, x, track + 1, 1, 1, 0, th.slate);
            }
            Pixel.panel(g, ex, track - 2, 6, 6, th.accent, th.bg);
            Panels.rect(g, ex + 2, track, 2, 2, 0, th.highlight);
            // Where the default is.
            Panels.rect(g, dx, track - 2, 1, 6, 0, th.slateLight);
            // The knob, a square node on the route: slate round an accent centre, lit while
            // held or under the mouse.
            boolean on = pressed == this;
            int r = 5;
            Pixel.panel(g, kx - r, track + 1 - r, 2 * r, 2 * r, on ? th.highlight
                    : hot ? th.slateLight : th.slate, th.bg);
            Panels.rect(g, kx - 2, track - 1, 4, 4, 0, on ? th.highlight : th.accent);
        }

        /** The top end while dragging, so the slider doesn't stretch under the mouse. */
        private double held = Double.NaN;

        @Override
        void click(double mx, double my, int button) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                o.reset();
                notes.remove(o);
                changedSettings = true;
                pressed = null;
                return;
            }
            held = top();
            drag(mx);
        }

        @Override
        void drag(double mx) {
            if (pressed != this) {
                return;
            }
            double hi = Double.isNaN(held) ? top() : held;
            double t = Math.clamp((mx - ex) / ew, 0, 1);
            double v = log() ? n.min * Math.pow(hi / n.min, t) : n.min + t * (hi - n.min);
            apply(o, round(v, hi - n.min));
        }

        /** About three figures, as typed. */
        private static String round(double v, double span) {
            double step = Math.pow(10, Math.floor(Math.log10(Math.max(Math.abs(v), span / 50)))
                    - 2);
            BigDecimal b = BigDecimal.valueOf(Math.round(v / step) * step)
                    .round(new java.math.MathContext(4));
            return b.stripTrailingZeros().toPlainString();
        }
    }

    /** Two numbers that are a range's ends, as two knobs on one line that can't cross. */
    private final class RangeSlider extends El {
        final Tuning.Num lo;
        final Tuning.Num hi;
        /** The knob held: the high one if true. */
        boolean high;

        RangeSlider(Tuning.Num lo, Tuning.Num hi) {
            this.lo = lo;
            this.hi = hi;
        }

        private double toT(double v) {
            return (Math.clamp(v, lo.min, lo.max) - lo.min) / (lo.max - lo.min);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            int track = ey + eh / 2 - 1;
            int a = ex + (int) Math.round(toT(lo.get()) * ew);
            int b = ex + (int) Math.round(toT(hi.get()) * ew);
            // Dotted outside the range, a solid accent line between its ends.
            for (int x = ex; x + 1 <= ex + ew; x += 3) {
                if (x < a - 5 || x > b + 5) {
                    Panels.rect(g, x, track + 1, 1, 1, 0, th.slate);
                }
            }
            Panels.rect(g, a, track, Math.max(0, b - a), 2, 0, th.accent);
            Panels.rect(g, ex + (int) Math.round(toT(lo.def) * ew), track - 2, 1, 6, 0,
                    th.slateLight);
            Panels.rect(g, ex + (int) Math.round(toT(hi.def) * ew), track - 2, 1, 6, 0,
                    th.slateLight);
            boolean held = pressed == this;
            knob(g, a, track, held && !high, hot, th);
            knob(g, b, track, held && high, hot, th);
        }

        private void knob(GuiGraphicsExtractor g, int kx, int track, boolean held, boolean hot,
                Theme.Colours th) {
            int r = 5;
            Pixel.panel(g, kx - r, track + 1 - r, 2 * r, 2 * r, held ? th.highlight
                    : hot ? th.slateLight : th.slate, th.bg);
            Panels.rect(g, kx - 2, track - 1, 4, 4, 0, held ? th.highlight : th.accent);
        }

        @Override
        void click(double mx, double my, int button) {
            if (button == InputConstants.MOUSE_BUTTON_RIGHT) {
                lo.reset();
                hi.reset();
                notes.remove(lo);
                notes.remove(hi);
                changedSettings = true;
                pressed = null;
                return;
            }
            // The nearer knob; on top of each other, the one the mouse is past.
            double a = ex + toT(lo.get()) * ew;
            double b = ex + toT(hi.get()) * ew;
            high = Math.abs(mx - b) < Math.abs(mx - a) || (a == b && mx > b);
            drag(mx);
        }

        @Override
        void drag(double mx) {
            if (pressed != this) {
                return;
            }
            double v = lo.min + Math.clamp((mx - ex) / ew, 0, 1) * (lo.max - lo.min);
            String text = Slider.round(v, lo.max - lo.min);
            if (high) {
                apply(hi, Double.parseDouble(text) < lo.get() ? lo.text() : text);
            } else {
                apply(lo, Double.parseDouble(text) > hi.get() ? hi.text() : text);
            }
        }
    }

    /**
     * The search box in the top bar, its own page: clicking it or typing in it opens
     * the Search page, which lists the settings that match.
     */
    private final class SearchBox extends Field {
        SearchBox(String text) {
            super(text, 32, "Search", typed -> {
                if (!page.equals(SEARCH)) {
                    page = SEARCH;
                    problem = null;
                }
                buildPage();
            }, c -> true);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = focused == this || page.equals(SEARCH);
            // Sunk into the top bar: darker than it, no icon.
            Pixel.panel(g, ex, ey, ew, eh, on ? th.accent : hot ? th.slateLight
                    : th.panelBorder, th.bg);
            int room = ew - 18;
            int ty = ey + (eh - 7) / 2;
            if (text.isEmpty() && focused != this) {
                text(g, "Search", body, ex + 7, ty, on ? th.highlight
                        : hot ? th.text : th.textDim);
                return;
            }
            String shown = secret?"*".repeat(text.length()):text;
            while (width(shown, body) > room && !shown.isEmpty()) {
                shown = shown.substring(1);
            }
            text(g, shown, body, ex + 7, ty, t().text);
            if (focused == this && System.currentTimeMillis() / 500 % 2 == 0) {
                Panels.rect(g, ex + 8 + width(shown, body), ty - 1, 1, 9, 0, th.accentBright);
            }
        }

        @Override
        void click(double mx, double my, int button) {
            super.click(mx, my, button);
            if (!page.equals(SEARCH)) {
                page = SEARCH;
                problem = null;
                buildPage();
            }
        }
    }

    /** Each page's icon in the sidebar, 9 by 9: '#' lit, '+' dim, '.' clear. */
    private static final Map<String, String[]> ICONS = Map.of(
            ROUTE, new String[] {
                    "......##.", "......###", "......#..", "....+.#..", ".........",
                    "..+......", ".........", "##.......", "##......."},
            PATHFINDER, new String[] {
                    "....#....", "....#....", ".#..#..#.", "..#.#.#..", "...###...",
                    "..#.#.#..", ".#..#..#.", "....#....", "....#...."},
            MOVEMENT, new String[] {
                    ".........", ".#...+...", "..#...+..", "...#...+.", "....#...+",
                    "...#...+.", "..#...+..", ".#...+...", "........."},
            MAPS, new String[] {
                    "#########", "#+++#...#", "#+++#...#", "#+++#...#", "#########",
                    "#...#+++#", "#...#+++#", "#...#+++#", "#########"},
            WIDGETS, new String[] {
                    "####.####", "#++#.#..#", "####.#..#", ".....#..#", "####.####",
                    "#..#.....", "#..#.####", "#..#.#++#", "####.####"},
            THEMES, new String[] {
                    "....#....", "...###...", "...###...", "..##+##..", ".##+++##.",
                    ".#+++++#.", ".#+++++#.", "..#+++#..", "...###..."},
            MACROS, new String[] {
                    ".........", ".##......", ".#+##....", ".#+++##..", ".#+++++#.",
                    ".#+++##..", ".#+##....", ".##......", "........."});

    private static void icon(GuiGraphicsExtractor g, String page, int x, int y, int lit,
            int dim) {
        String[] rows = ICONS.get(page);
        if (rows == null) {
            return;
        }
        for (int r = 0; r < rows.length; r++) {
            for (int c = 0; c < rows[r].length(); c++) {
                char ch = rows[r].charAt(c);
                if (ch != '.') {
                    Panels.rect(g, x + c, y + r, 1, 1, 0, ch == '#' ? lit : dim);
                }
            }
        }
    }

    /** A page in the sidebar: its icon and name. */
    private final class SideItem extends El {
        final String name;
        final String label;

        SideItem(String name, String label, int at, int top, int w, int h) {
            this.name = name;
            this.label = label;
            at(at, top, w, h);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean open = name.equals(page);
            if (open) {
                // The page that's open: an accent edge round a darker box, a bar at its left.
                Pixel.panel(g, ex, ey, ew, eh, th.accent, th.bg);
                Panels.rect(g, ex + 1, ey + 3, 2, eh - 6, 0, th.highlight);
            } else if (hot) {
                Pixel.panel(g, ex, ey, ew, eh, th.panelBorder, th.bgPanel);
            }
            int colour = open ? th.highlight : hot ? th.text : th.textDim;
            int cy = ey + eh / 2;
            icon(g, name, ex + 8, cy - 5, open ? th.accentBright : hot ? th.slateLight
                    : th.slate, open ? th.accentDeep : th.panelBorder);
            text(g, label, body, ex + 23, cy - 3, colour);
        }

        @Override
        void click(double mx, double my, int button) {
            open(name);
        }
    }

    /** The window key's setting: click, then press the new key (Esc keeps the old one). */
    private final class KeyBind extends El {

        private String key() {
            return listening == this ? "Press a key"
                    : client.windowKey().getTranslatedKeyMessage().getString();
        }

        @Override
        int prefW() {
            return Math.max(CONTROL_W, width(key(), body) + 20);
        }

        @Override
        String tip() {
            return "The key that opens this window. Click, then press the key you want; Esc"
                    + " keeps the one it has. Also in Controls.";
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = listening == this;
            Pixel.button(g, ex, ey, ew, eh, on ? Pixel.State.ACTIVE
                    : hot ? Pixel.State.HOVER : Pixel.State.NORMAL, th);
            String s = key();
            int tw = width(s, body);
            boolean blink = on && System.currentTimeMillis() / 400 % 2 == 0;
            text(g, s, body, ex + (ew - tw) / 2, ey + (eh - 7) / 2,
                    on ? (blink ? th.highlight : th.textDim) : th.text);
        }

        @Override
        void click(double mx, double my, int button) {
            listening = listening == this ? null : this;
        }
    }

    /** The key setting waiting for a key, if any. */
    private KeyBind listening;

    /** A colour theme to pick, with its colours along the bottom. */
    private final class Tile extends El {
        final Theme theme;

        Tile(Theme theme) {
            this.theme = theme;
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            boolean active = theme == Theme.current();
            // Its colours now (Rainbow's go round).
            Theme.Colours c = theme.colours();
            int fill = c.bgPanel;
            // Each tile in its own theme's tokens; only the one in use gets an accent border.
            int edge = active ? c.accent : hot ? c.slateLight : c.panelBorder;
            Pixel.panel(g, ex, ey, ew, eh, edge, fill);
            if (active) {
                Panels.rect(g, ex + 1, ey + 1, ew - 2, 1, 0, c.highlight);
            }
            text(g, theme.label, title, ex + 12, ey + 11, c.text);
            text(g, active ? "Active" : "Inactive", small, ex + 12, ey + 27,
                    active ? c.highlight : c.textDim);
            // Its colours as a little route: the start box, the route's dots, a node, and the
            // lead box in the route colour.
            int ry = ey + eh - 13;
            for (int x = ex + 14; x < ex + 100; x += 4) {
                Panels.rect(g, x, ry, 2, 2, 0, c.slateLight);
            }
            Pixel.panel(g, ex + 10, ry - 4, 10, 10, c.accent, fill);
            Panels.rect(g, ex + 14, ry, 2, 2, 0, c.highlight);
            Panels.rect(g, ex + 52, ry - 2, 6, 6, 0, c.slate);
            Pixel.panel(g, ex + 92, ry - 4, 10, 10, theme.route.leadNow(), fill);
        }

        @Override
        String tip() {
            return "The screen in " + theme.label.toLowerCase(Locale.ROOT)
                    + ", and the route drawn in " + theme.route.id() + ".";
        }

        @Override
        void click(double mx, double my, int button) {
            Theme.use(theme);
            overlay.colour(theme.route);
        }
    }
}
