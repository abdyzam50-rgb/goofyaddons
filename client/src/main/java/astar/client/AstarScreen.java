package astar.client;

import astar.client.draw.OverlayColour;
import astar.client.ui.LoadingArt;
import astar.client.ui.MazeBackground;
import astar.client.ui.Panels;
import astar.client.ui.Pixel;
import astar.client.ui.Theme;
import astar.client.ui.Ui;
import astar.pathing.Tuning;
import com.mojang.blaze3d.platform.InputConstants;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
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
 * The mod's window, opened with a key (G unless changed in Controls): a sidebar of pages (where
 * to go, how the route is drawn, a page per {@link Tuning} section, and colour themes), each a
 * set of cards of settings. Everything here can also be done with {@code .A*}.
 *
 * <p>Drawn entirely by the mod: rounded shapes from the {@code panel} shader ({@link Panels})
 * and pixel fonts for the text, at its own scale of whole screen pixels ({@link #px}), whatever the GUI
 * scale, so it stays sharp. Its controls are its own too ({@link El}), laid out every frame.
 */
final class AstarScreen extends Screen implements com.goofy.goofyaddons.keybinds.BindingCaptureScreen {

    // ---- Pages -----------------------------------------------------------------------------

    static final String DRAWING = "drawing";
    static final String THEMES = "themes";
    static final String MACROS = "macros";
    /** The {@link Tuning} section of the saved maps, shown with the maps themselves. */
    static final String CACHE = "cache";
    /** Settings found by what they're called or do, from the box at the top of the sidebar. */
    static final String SEARCH = "search";

    /** Kept while the game runs, so the window opens as it was left. */
    private static String lastPage = DRAWING;

    private final AstarClient client;
    private final RouteOverlay overlay;
    private String page;
    private final Map<String, Integer> scrolls = new LinkedHashMap<>();

    // ---- Size, in units of px screen pixels -------------------------------------------------

    private static final int SIDE_W = 142;
    private static final int HEAD_H = 50;
    private static final int PAD = 12;
    /** The width of a row's box or list, so they line up down a card. */
    private static final int CONTROL_W = 96;
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
    private long hoveredSince;

    private String problem;
    /** What the last change to a setting said, for its line. */
    private final Map<Tuning.Option, String> notes = new LinkedHashMap<>();
    private boolean changedSettings;

    AstarScreen(AstarClient client, RouteOverlay overlay, String page) {
        super(Component.literal("A* Client"));
        this.client = client;
        this.overlay = overlay;
        this.page = page == null ? lastPage : page;
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
        m.put(DRAWING, "Drawing");
        for (String s : Tuning.sections().keySet()) {
            if (!s.equals("overlay")) {
                m.put(s, s.equals(CACHE) ? "Caches" : capital(s));
            }
        }
        m.put(MACROS, "Macros");
        m.put(THEMES, "Themes");
        return m;
    }

    @Override
    protected void init() {
        var window = minecraft.getWindow();
        // Whole screen pixels per unit: 2 on a 1080p screen, 3 at 1440p, 4 at 4K.
        px = Math.clamp(window.getHeight() / 400, 1, 4);
        k = px / (float) window.getGuiScale();
        body = Ui.font("body", px);
        strong = Ui.font("strong", px);
        small = Ui.font("small", px);
        title = Ui.font("title", px);
        int screenW = Math.round(width / k);
        int screenH = Math.round(height / k);
        // The stone frame goes round the outside.
        winW = Math.min(620, screenW - 2 * Pixel.FRAME - 8);
        winH = Math.min(380, screenH - 2 * Pixel.FRAME - 8);
        winX = (screenW - winW) / 2;
        winY = (screenH - winH) / 2;
        areaX = winX + SIDE_W + PAD;
        areaY = winY + HEAD_H + 16;
        areaW = winW - SIDE_W - 2 * PAD;
        areaH = winH - HEAD_H - 20;

        fixed.clear();
        if (!page.equals(SEARCH) && !pages().containsKey(page)) {
            page = DRAWING;
        }
        String typed = search == null ? "" : search.text;
        search = new SearchBox(typed);
        search.at(winX + 10, winY + HEAD_H + 10, SIDE_W - 20, 22);
        fixed.add(search);
        side();
        buildPage();
    }

    /** Whether the pathfinder group is open: folded each time the window opens. */
    private boolean pathfinderOpen;

    /** The pages that fold into "Pathfinder settings". */
    private static boolean pathfinderPage(String name) {
        return !name.equals(MACROS) && !name.equals(THEMES);
    }

    /** Lays out the sidebar: the other pages, then the pathfinder group (folded or not). */
    private void side() {
        fixed.removeIf(e -> e instanceof SideItem || e instanceof SideGroup);
        List<El> items = new ArrayList<>();
        int sy = winY + HEAD_H + 40;
        for (var e : pages().entrySet()) {
            if (!pathfinderPage(e.getKey())) {
                items.add(new SideItem(e.getKey(), e.getValue(), winX + 10, sy, SIDE_W - 20,
                        22));
                sy += 26;
            }
        }
        sy += 6;
        items.add(new SideGroup(winX + 10, sy, SIDE_W - 20, 22));
        sy += 28;
        if (pathfinderOpen) {
            for (var e : pages().entrySet()) {
                if (pathfinderPage(e.getKey())) {
                    items.add(new SideItem(e.getKey(), e.getValue(), winX + 18, sy,
                            SIDE_W - 28, 20));
                    sy += 23;
                }
            }
        }
        fixed.addAll(0, items);
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
        page = name;
        lastPage = name;
        problem = null;
        search.text = "";
        focus(null);
        buildPage();
    }

    // ---- Building pages ------------------------------------------------------------------

    private void buildPage() {
        dropped = null;
        fixed.removeIf(e->e instanceof TradingTab);
        areaY=winY+HEAD_H+16;
        areaH=winH-HEAD_H-20;
        if(page.equals(MACROS))tradingTabs();
        cards.clear();
        tiles.clear();
        if (page.equals(SEARCH)) {
            buildSearch(search.text.strip().toLowerCase(Locale.ROOT));
        } else if (page.equals(DRAWING)) {
            buildDrawing();
        } else if (page.equals(MACROS)) {
            buildTrading();
        } else if (page.equals(THEMES)) {
            for (Theme theme : Theme.values()) {
                tiles.add(new Tile(theme));
            }
        } else {
            buildSection(page);
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
        int x=areaX, y=winY+HEAD_H+8;
        for(var section:TradingSection.values()) {
            int w=width(section.label,body)+16;
            if(x>areaX && x+w>areaX+areaW){x=areaX;y+=24;}
            fixed.add(new TradingTab(section).at(x,y,w,18));x+=w+4;
        }
        areaY=y+30;
        areaH=winY+winH-4-areaY;
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
        status.callout(()->manager.hasSafetyBlock()||com.goofy.goofyaddons.config.GoofyConfig.loadError()!=null?Callout.ERROR:Callout.INFO,
            ()->com.goofy.goofyaddons.config.GoofyConfig.loadError()!=null?com.goofy.goofyaddons.config.GoofyConfig.loadError():manager.status()+" · "+manager.activity());
        status.row("Current task",manager::taskItem);
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
        status.row("Mode",()->"Change trading mode while stopped.",new Mode<>(List.of(com.goofy.goofyaddons.features.TradingMode.values()),
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
        Card capital=card("Spending limits","These limits apply to every purchase. Changes require stopping the trader.");
        capital.row("Capital limit",()->"Maximum committed trading capital, in coins.",tradingNumber("Capital limit",()->draft.view().maxTradingCapital,(cfg,v)->cfg.maxTradingCapital=v));
        capital.row("Purse reserve",()->"Coins kept outside trading; zero uses all available funds.",tradingNumber("Purse reserve",()->draft.view().purseReserve,(cfg,v)->cfg.purseReserve=v));
        capital.row("Book slots",()->"Maximum active book routes.",tradingWhole("Book slots",()->draft.view().maxActiveBooks,1,10,(cfg,v)->cfg.maxActiveBooks=v));
        capital.row("Item slots",()->"Maximum active ordinary-item routes.",tradingWhole("Item slots",()->draft.view().general.maxActiveItems,1,10,(cfg,v)->cfg.general.maxActiveItems=v));
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
        Card crafts=card("Craft flips","Buy ingredients, prepare intermediates, craft and instant-sell on the Bazaar.");
        crafts.row("Craft flip mode",()->"Select Craft flips, then use the trading toggle for automatic Bazaar crafting.",
            new Button("Use craft mode",()->editTrading("Mode",cfg->cfg.tradingMode=com.goofy.goofyaddons.features.TradingMode.CRAFT),Button.PLAIN));
        crafts.row("Sale market",()->"Choose which market appears in the craft ranking.",new Mode<>(List.of("BOTH","BAZAAR","AH"),()->draft.view().craftFlips.venue,
            v->v,v->editTrading("Craft sale market",cfg->cfg.craftFlips.venue=v)));
        crafts.row("Batch capital",()->"Maximum coins per craft run; zero uses spendable funds.",tradingNumber("Craft capital",()->draft.view().craftFlips.maximumCapital,(cfg,v)->cfg.craftFlips.maximumCapital=v));
        crafts.row("Minimum net profit",()->"Require this profit after depth, taxes and price movement allowance before buying.",tradingNumber("Craft profit",()->draft.view().craftFlips.minimumProfit,(cfg,v)->cfg.craftFlips.minimumProfit=v));
        crafts.row("Maximum batches",()->"1–16; planning reduces the batch to fit money, depth, volume and inventory.",tradingWhole("Craft batches",()->draft.view().craftFlips.maxBatches,1,16,(cfg,v)->cfg.craftFlips.maxBatches=v));
        crafts.row("Inspect ranking",()->"Account requirements and route reasons appear in the local dashboard.",new Button("Best crafts",()->manager.production().showCraftPlans(),Button.PLAIN));
        crafts.row("Current plan",manager.production()::activity);
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

    private void buildDrawing() {
        Card route = card("Route in the world", Tuning.sections().get("overlay"));
        drawingRows(route, words -> true);
        for (Tuning.Option o : Tuning.all()) {
            if (o.section.equals("overlay")) {
                route.option(o);
            }
        }
        // The logo big, beside the settings: the search it plays is the route's look in small.
        Card preview = card("Preview", "The logo's search, in this theme.");
        preview.preview = true;
        preview.row("Live search", () -> "Play it again", new Button("Replay", () -> {
            LoadingArt logo = LoadingArt.logo();
            if (logo != null) {
                logo.restart();
                if (Theme.current() != Theme.RAINBOW) {
                    logo.finish();
                }
            }
        }, Button.ACCENT));
    }

    /** The Drawing page's own rows (not settings from the file) whose words pass {@code keep}. */
    private void drawingRows(Card route, java.util.function.Predicate<String> keep) {
        StringBuilder looks = new StringBuilder();
        for (RouteOverlay.Style s : RouteOverlay.Style.values()) {
            looks.append(' ').append(s.label).append(' ').append(s.id()).append(' ')
                    .append(s.about);
        }
        StringBuilder colours = new StringBuilder();
        for (OverlayColour c : OverlayColour.values()) {
            colours.append(' ').append(c.id());
        }
        if (keep.test("shown show hide draw route")) {
            route.row("Shown", () -> "Draw the route while going (.A* show)",
                new Check(overlay::shown, overlay::shown));
        }
        if (keep.test("look style render route" + looks)) {
            route.row("Look", () -> overlay.style().about,
                    new Mode<>(List.of(RouteOverlay.Style.values()), overlay::style,
                            s -> s.label, overlay::style));
        }
        if (keep.test("colour color route theme" + colours)) {
            Mode<OverlayColour> colour = new Mode<>(List.of(OverlayColour.values()),
                    overlay::colour, c -> capital(c.id()), overlay::colour);
            colour.swatch = OverlayColour::lineNow;
            route.row("Colour", () -> overlay.colour() == Theme.current().route ? "Matches the theme"
                    : "The theme's is " + capital(Theme.current().route.id()), colour);
        }
    }

    private void buildSection(String section) {
        if (section.equals(CACHE)) {
            buildMaps();
        }
        List<Tuning.Option> options = new ArrayList<>();
        for (Tuning.Option o : Tuning.all()) {
            if (o.section.equals(section)) {
                options.add(o);
            }
        }
        // In cards of a few, so two columns fill evenly.
        int per = Math.max(4, (options.size() + 1) / 2);
        if (!twoColumns()) {
            per = options.size();
        }
        for (int i = 0; i < options.size(); i += per) {
            Card c = i == 0 ? card(capital(section), Tuning.sections().get(section))
                    : card(null, null);
            for (Tuning.Option o : options.subList(i, Math.min(options.size(), i + per))) {
                c.option(o);
            }
            if (i == 0) {
                c.header = new Button("Reset all", () -> {
                    for (Tuning.Option o : options) {
                        o.reset();
                    }
                    notes.clear();
                    changedSettings = true;
                }, Button.GHOST).when(() -> options.stream().anyMatch(Tuning.Option::changed));
            }
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
            c.callout(() -> Callout.INFO, () -> "Type in the box at the top left.");
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
        Card drawing = card("Drawing", null);
        drawingRows(drawing, hay -> matches.test("drawing " + hay));
        Map<String, Card> bySection = new LinkedHashMap<>();
        bySection.put("overlay", drawing);
        for (Tuning.Option o : Tuning.all()) {
            String page = o.section.equals("overlay") ? DRAWING : o.section;
            String hay = pages().get(page) + " " + o.section + " " + o.name.replace('_', ' ')
                    + " " + o.help + " " + Tuning.sections().get(o.section);
            if (matches.test(hay)) {
                bySection.computeIfAbsent(o.section, sec -> card(pages().get(sec), null))
                        .option(o);
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

    private boolean twoColumns() {
        return !page.equals(MACROS) && !page.equals(SEARCH) && areaW >= 420;
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
            if (c.header != null) {
                list.add(c.header);
            }
            for (Row r : c.rows) {
                list.addAll(r.controls);
                if (r.slider != null) {
                    list.add(r.slider);
                }
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
        double mx = ux(mouseX);
        double my = uy(mouseY);
        El hot = dropped != null && (dropped.itemAt(mx, my) >= 0 || dropped.in(mx, my)) ? dropped
                : at(mx, my);
        if (hot != hovered) {
            hovered = hot;
            hoveredSince = System.currentTimeMillis();
        }
        Theme.Colours th = t();
        g.pose().pushMatrix();
        g.pose().scale(k, k);

        // The window in its stone frame, and its sidebar.
        Pixel.bricks(g, winX, winY, winW, winH, th);
        Panels.rect(g, winX, winY, winW, winH, 0, th.bg);
        // The whole window is a piece of the loading screen's map: a search solving one random
        // maze after another, with the sidebar and the cards laid over it.
        g.enableScissor(winX + 1, winY + 1, winX + winW - 1, winY + winH - 1);
        MazeBackground.get().draw(g, winX + 1, winY + 1, winW - 2, winH - 2, 0.55f);
        g.disableScissor();
        Panels.rect(g, winX, winY, SIDE_W, winH, 0, th.bgPanel);
        // A double line between the sidebar and the page, and one under the page's title.
        Panels.rect(g, winX + SIDE_W, winY, 1, winH, 0, th.panelBorder);
        Panels.rect(g, winX + SIDE_W + 1, winY, 1, winH, 0, th.bg);
        Panels.rect(g, winX + SIDE_W + PAD, winY + HEAD_H, winW - SIDE_W - 2 * PAD, 1, 0,
                th.panelBorder);
        // The logo in its dark box, then "A* Client" and the version beside it.
        int lx = winX + 8;
        int ly = winY + (HEAD_H - LOGO) / 2;
        Pixel.panel(g, lx - 1, ly - 1, LOGO + 2, LOGO + 2, th.panelBorder, th.bg);
        logo(g, lx, ly);
        int tx = lx + LOGO + 8;
        text(g, "A* Client", strong, tx, winY + HEAD_H / 2 - 9, th.text);
        text(g, "v" + VERSION, small, tx, winY + HEAD_H / 2 + 3, th.textDim);
        // Where this is, at the bottom of the sidebar, by a sparkle.
        int fy = winY + winH - 44;
        Pixel.panel(g, winX + 8, fy, SIDE_W - 16, 36, th.panelBorder, th.bg);
        Places.Place place = Places.current();
        boolean going = state() != Navigator.State.DONE;
        Pixel.sparkle(g, winX + 22, fy + 18, 5, going ? th.accentBright : th.accent,
                th.highlight, th.bg);
        text(g, fit(place == null ? "Unknown map" : place.name, strong, SIDE_W - 52),
                strong, winX + 36, fy + 8, t().text);
        text(g, fit(stateWord(), small, SIDE_W - 52), small, winX + 36, fy + 21, t().textDim);
        // The page's title.
        String heading = page.equals(SEARCH) ? "Search" : pages().get(page);
        text(g, heading, title, winX + SIDE_W + PAD + 4, winY + (HEAD_H - 12) / 2 + 1, t().text);
        String feedback=page.equals(MACROS)?tradingFeedback():null;
        if(feedback!=null)text(g,fit(feedback,small,areaW-8),small,areaX+4,winY+HEAD_H-11,Callout.WARN);

        for (El e : fixed) {
            e.draw(g, e == hot);
        }

        // The page, scrolled, inside its area.
        g.enableScissor(areaX - 4, areaY - 4, areaX + areaW + 4, areaY + areaH);
        int scroll = scrolls.getOrDefault(key(), 0);
        contentH = tiles.isEmpty() ? layoutCards(scroll) : layoutTiles(scroll);
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
        if(feedback!=null && dropped==null && mx>=areaX && mx<areaX+areaW && my>=winY+HEAD_H-13 && my<winY+HEAD_H) {
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

    private String stateWord() {
        return switch (state()) {
            case DONE -> "Idle";
            case PLANNING -> "Planning a route";
            case DRIVING -> "Going";
            case PAUSED -> "Paused";
        };
    }

    /** Places the cards in one or two columns, each in the shorter; returns the height used. */
    private int layoutCards(int scroll) {
        int columns = twoColumns() ? 2 : 1;
        int colW = (areaW - GAP * (columns - 1)) / columns;
        int[] bottom = new int[columns];
        for (Card c : cards) {
            int col = 0;
            for (int i = 1; i < columns; i++) {
                if (bottom[i] < bottom[col]) {
                    col = i;
                }
            }
            int h = c.layout(areaX + col * (colW + GAP), areaY + bottom[col] - scroll, colW);
            bottom[col] += h + GAP;
        }
        int most = 0;
        for (int b : bottom) {
            most = Math.max(most, b - GAP);
        }
        return most;
    }

    private int layoutTiles(int scroll) {
        int columns = areaW >= 420 ? 3 : 2;
        int w = (areaW - GAP * (columns - 1)) / columns;
        int h = 70;
        for (int i = 0; i < tiles.size(); i++) {
            tiles.get(i).at(areaX + (i % columns) * (w + GAP),
                    areaY + (i / columns) * (h + GAP) - scroll, w, h);
        }
        int rows = (tiles.size() + columns - 1) / columns;
        return rows * (h + GAP) - GAP;
    }

    /** Units between letters, on top of the font's own gap: the pixel-art look's tracking. */
    private static final int TRACK = 1;
    /** Each letter's width in each font, measured once. */
    private final Map<FontDescription, Map<Integer, Integer>> advances = new HashMap<>();

    /**
     * Headings use tracked capitals; body text and typed values retain their case.
     */
    private void text(GuiGraphicsExtractor g, String s, FontDescription f, int at, int top,
            int colour) {
        String up = headingFont(f)?s.toUpperCase(Locale.ROOT):s;
        int x = at;
        for (int i = 0; i < up.length(); ) {
            int cp = up.codePointAt(i);
            if (cp != ' ') {
                g.text(font, Ui.text(Character.toString(cp), f), x, top, colour, false);
            }
            x += advance(cp, f) + (headingFont(f)?TRACK:0);
            i += Character.charCount(cp);
        }
    }

    private boolean headingFont(FontDescription f) { return f.equals(strong)||f.equals(title); }
    private int advance(int cp, FontDescription f) {
        return advances.computeIfAbsent(f, k -> new HashMap<>()).computeIfAbsent(cp,
                c -> font.width(Ui.text(Character.toString(c), f)));
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
        String up = headingFont(f)?s.toUpperCase(Locale.ROOT):s;
        int w = 0;
        int n = 0;
        for (int i = 0; i < up.length(); ) {
            int cp = up.codePointAt(i);
            w += advance(cp, f);
            n++;
            i += Character.charCount(cp);
        }
        return w + Math.max(0, n - 1) * (headingFont(f)?TRACK:0);
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

    /** The first sentence of a setting's help, for under its name. */
    private static String firstSentence(String help) {
        int end = help.indexOf(". ");
        return end < 0 ? help.replaceAll("\\.$", "") : help.substring(0, end);
    }

    private static String when(Tuning.Effect effect) {
        return switch (effect) {
            case ROUTES, NEXT_ROUTE -> "Used from the next route.";
            case HOPS -> "Used from the next route with teleports.";
            case NOW -> "Used at once.";
        };
    }

    // ---- Cards and rows ----------------------------------------------------------------------

    /** A card of rows: a title, what it's for, and its settings. */
    private final class Card {
        final String title;
        final String about;
        final List<Row> rows = new ArrayList<>();
        /** A small button by the title. */
        El header;
        /** Shows the logo big under its title. */
        boolean preview;
        int artY;
        int artS;
        int cx;
        int cy;
        int cw;
        int ch;
        List<String> aboutLines = List.of();

        Card(String title, String about) {
            this.title = title;
            this.about = about;
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

        /** A line for a {@link Tuning} setting: its control, a slider for a number, a reset. */
        void option(Tuning.Option o) {
            El control = switch (o) {
                case Tuning.Num n -> {
                    Field f = new Field(n.text(), 12, "", s -> apply(o, s),
                            c -> Character.isDigit(c) || c == '.' || c == '-');
                    f.live = n::text;
                    yield f.wide(72);
                }
                case Tuning.Flag f -> new Check(f::get, on -> apply(o, on.toString())).toggle();
                case Tuning.Choice c -> new Mode<>(c.choices, c::get, v -> v, v -> apply(o, v));
            };
            Row r = row(label(o.name), () -> notes.getOrDefault(o, firstSentence(o.help)),
                    control);
            r.option = o;
            r.tip = o.help + "\n\n" + capital(o.range()) + ", default " + o.defaultText() + ". "
                    + when(o.effect);
            control.tip(r.tip);
            if (o instanceof Tuning.Num n) {
                r.slider = new Slider(n, o);
            }
            r.reset = new Button("Reset", () -> {
                o.reset();
                notes.remove(o);
                changedSettings = true;
            }, Button.GHOST).tip("Back to " + o.defaultText() + ".");
        }

        /** Places everything at this spot; returns the card's height. */
        int layout(int at, int top, int w) {
            cx = at;
            cy = top;
            cw = w;
            int yy = top + 10;
            if (title != null) {
                yy += 14;
                aboutLines = about == null ? List.of() : wrap(about, small, w - 20, w - 20, 2);
                yy += 11 * aboutLines.size();
                yy += 10;
                if (header != null) {
                    header.at(at + w - 10 - header.prefW(), top + 8, header.prefW(), 16);
                }
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
            Pixel.panel(g, cx, cy, cw, ch, cardEdge(th), th.bgPanel);
            if (title != null) {
                text(g, fit(title, strong, cw - (header==null?20:header.prefW()+30)), strong, cx + 10, cy + 11, t().text);
                for (int i = 0; i < aboutLines.size(); i++) {
                    text(g, aboutLines.get(i), small, cx + 10, cy + 24 + 11 * i, t().textDim);
                }
                int line = cy + 10 + 14 + 11 * aboutLines.size() + 4;
                Panels.rect(g, cx + 10, line, cw - 20, 1, 0, th.panelBorder);
                if (header != null) {
                    header.shown = header.enabled();
                    if (header.shown) {
                        header.draw(g, header == hot);
                    }
                }
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
            if (header != null) {
                header.shown = false;
            }
            for (Row r : rows) {
                r.controls.forEach(e -> e.shown = false);
                if (r.slider != null) {
                    r.slider.shown = false;
                }
                if (r.reset != null) {
                    r.reset.shown = false;
                }
            }
        }
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
        Slider slider;
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
            int cyy = top + (30 - 18) / 2;
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
            // The line under the name: as much as fits beside the controls, the rest wrapped
            // across the row below them.
            descLines = List.of();
            String d = desc == null ? null : desc.get();
            if (label != null && d != null && !d.isEmpty()) {
                descLines = wrap(d, small, controlsLeft() - at - 10, w, 3);
            }
            int extra = Math.max(0, descLines.size() - 1) * 11;
            rh = 30 + extra + (slider != null ? 14 : 0);
            if (slider != null) {
                slider.at(at, top + 32 + extra, w, 10);
            }
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
            for (El e : controls) {
                e.shown = in;
            }
            if (slider != null) {
                slider.shown = in;
            }
            if (reset != null) {
                reset.shown = in && option.changed();
            }
            if (!in) {
                return;
            }
            if (label != null) {
                int room = stacked?rw:controlsLeft() - rx - 8;
                boolean changed = option != null && option.changed();
                text(g, fit(label, body, room), body, rx, ry + 6, t().text);
                if (changed) {
                    Panels.rect(g, rx - 6, ry + 9, 3, 3, 1, t().slateLight);
                }
                boolean note = option != null && notes.containsKey(option);
                for (int i = 0; i < descLines.size(); i++) {
                    text(g, descLines.get(i), small, rx, ry + 18 + 11 * i,
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
            ew = 24;
            return this;
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = get.getAsBoolean();
            int top = ey + (eh - 12) / 2;
            if (toggle) {
                Pixel.toggle(g, ex, top, ew, 12, on, hot, th);
                return;
            }
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

    /** A dropdown of named choices; right click steps back. */
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
            int room = ew - 16;
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
            if (on && System.currentTimeMillis() / 500 % 2 == 0) {
                int cx = ex + 9 + width(shown, body);
                Panels.rect(g, cx, ey + 5, 1, 8, 0, th.accentBright);
            }
        }

        @Override
        void click(double mx, double my, int button) {
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

        /** Wide ranges go by ratio, so the small end isn't squeezed. */
        private boolean log() {
            return n.min > 0 && n.max / n.min >= 100;
        }

        private double toT(double v) {
            v = Math.clamp(v, n.min, n.max);
            return log() ? Math.log(v / n.min) / Math.log(n.max / n.min)
                    : (v - n.min) / (n.max - n.min);
        }

        private double fromT(double t) {
            return log() ? n.min * Math.pow(n.max / n.min, t) : n.min + t * (n.max - n.min);
        }

        @Override
        String tip() {
            return null;
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

        @Override
        void click(double mx, double my, int button) {
            drag(mx);
        }

        @Override
        void drag(double mx) {
            double v = fromT(Math.clamp((mx - ex) / ew, 0, 1));
            apply(o, round(v, n.max - n.min));
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

    /**
     * The search box at the top of the sidebar, its own page: clicking it or typing in it opens
     * the Search page, which lists the settings that match.
     */
    private final class SearchBox extends Field {
        SearchBox(String text) {
            super(text, 32, "Search", typed -> {
                if (!page.equals(SEARCH)) {
                    page = SEARCH;
                    lastPage = SEARCH;
                    problem = null;
                }
                buildPage();
            }, c -> true);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            boolean on = focused == this || page.equals(SEARCH);
            // Sunk into the sidebar, like the mockup's: darker than it, no icon.
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
                lastPage = SEARCH;
                problem = null;
                buildPage();
            }
        }
    }

    /** A page in the sidebar. */
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
                // The page that's open: an accent edge round a darker box.
                Pixel.panel(g, ex, ey, ew, eh, th.accent, th.bg);
            } else if (hot) {
                Pixel.panel(g, ex, ey, ew, eh, th.panelBorder, th.bgPanel);
            }
            int colour = open ? th.highlight : hot ? th.text : th.textDim;
            // A route node: a hollow accent square round a highlight centre for the open page,
            // a small slate one for the rest.
            int cy = ey + eh / 2;
            if (open) {
                Pixel.panel(g, ex + 7, cy - 4, 8, 8, th.accent, th.bg);
                Panels.rect(g, ex + 10, cy - 1, 2, 2, 0, th.highlight);
            } else {
                Panels.rect(g, ex + 9, cy - 2, 3, 3, 0, hot ? th.slateLight : th.slate);
            }
            text(g, label, body, ex + 21, cy - 3, colour);
        }

        @Override
        void click(double mx, double my, int button) {
            open(name);
        }
    }

    /** "Pathfinder settings": folds the pathfinder's pages away and back. */
    private final class SideGroup extends El {

        SideGroup(int at, int top, int w, int h) {
            at(at, top, w, h);
        }

        @Override
        void draw(GuiGraphicsExtractor g, boolean hot) {
            Theme.Colours th = t();
            // Folded with one of its pages open, it shows that the page is in here.
            boolean inside = !pathfinderOpen && pathfinderPage(page) && !page.equals(SEARCH);
            if (inside) {
                Pixel.panel(g, ex, ey, ew, eh, th.accent, th.bg);
            }
            // A section's header: quieter than the pages under it, as in a pixel game's menu.
            int colour = inside ? th.highlight : hot ? th.text : th.textDim;
            text(g, fit("Pathfinder", body, ew - 22), body, ex + 6, ey + eh / 2 - 3, colour);
            arrow(g, ex + ew - 11, ey + eh / 2, pathfinderOpen ? 'v' : '>',
                    hot ? th.text : th.slate);
        }

        @Override
        void click(double mx, double my, int button) {
            pathfinderOpen = !pathfinderOpen;
            side();
        }
    }

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
