package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.VanillaMapTooltip;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.MapLayerTerrainInstaller;
import kmu.maplayers.base.sidebar.runtime.SidebarHosts;
import kmu.maplayers.base.sidebar.runtime.SidebarInput;
import kmu.maplayers.base.sidebar.runtime.SidebarRenderer;
import kmu.maplayers.base.tooltip.HoverTooltipDetailModeInput;
import kmu.maplayers.base.tooltip.HoverTooltipDetailModeState;
import kmu.maplayers.base.tooltip.MapLayerCellTooltip;
import kmu.maplayers.politicalmap.base.PoliticalMapSaveMigrations;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapStalenessSource;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonisationListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.maplayers.politicalmap.base.render.PoliticalMapLayerRenderer;
import kmu.settings.KmuLunaSettings;
import kmu.starsector.nexerelin.NexerelinInvasionListenerInstaller;
import kmu.ui.context.StarsectorMarketUiContextTracker;

import org.apache.log4j.Logger;

import java.util.function.Supplier;

/**
 * KMU's entry point: the class the game constructs from the {@code modPlugin} field in
 * {@code mod_info.json}, once per launch, and calls again on every save load. What it does is
 * wiring - each step hands one collaborator the per-launch or per-save registration it cannot
 * perform for itself.
 *
 * <p>The name breaks the naming rules the rest of the mod follows, and does so deliberately.
 * {@code <PREFIX>_ModPlugin} is the Starsector ecosystem's idiom for this one class, so a log line
 * or a stack trace naming it says which mod it came from without the reader knowing KMU's package
 * layout - and every enabled mod contributes one of these to the same running game, where a bare
 * {@code ModPlugin} would say nothing. No other class in KMU is named this way, and none should be.
 */
public class KMU_ModPlugin extends BaseModPlugin {

    private static final Logger LOG = Global.getLogger(KMU_ModPlugin.class);

    @Override
    public void onApplicationLoad() {
        // App-scoped, once per launch: register KMU's LunaLib settings bindings before any save
        // loads. LunaLib is a hard dependency, so it has already loaded by the time this runs.
        runGuardedStep("Failed to install KMU LunaLib settings bindings",
            KmuLunaSettings::installBindings);

        // Wire the concrete map layers into the framework registry once per launch, before any
        // sector map can open. The registry stays agnostic to which views exist; this is the
        // one place they are named.
        runGuardedStep("Failed to register KMU map layers", MapLayers::registerAll);
    }

    // The alias lineage belongs to the terrain plugin whose renames created it, so the list and the
    // ordering rule live beside that class rather than here. super runs first so this only adds to
    // what the base plugin configures. XStream is fully qualified for the reason the installer
    // fully qualifies it: com.thoughtworks belongs to no import group the checkstyle order
    // recognises.
    @Override
    public void configureXStream(com.thoughtworks.xstream.XStream x) {
        super.configureXStream(x);
        MapLayerTerrainInstaller.registerSaveAliases(x);
    }

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        runGuardedStep("Failed to install KMU market UI context tracker",
            () -> installMarketUiContextTracker(Global.getSector()));

        // Self-heal pre-rename saves before the terrain reads them. One seam owns the full set of
        // political-map heals, so this call site never has to track which migrations exist.
        runGuardedStep("Failed to migrate KMU political map state",
            PoliticalMapSaveMigrations::healLoadedSave);

        runGuardedStep("Failed to install KMU sector map layer terrain",
            () -> MapLayerTerrainInstaller.installSchematicTerrain(Global.getSector()));

        // The second terrain of the render pair, guarded separately so a failure to build the
        // starscape entity - the variant reaching concrete core classes - cannot take the schematic
        // one down with it and leave the map painting nothing in either mode.
        runGuardedStep("Failed to install KMU sector map layer starscape terrain",
            () -> MapLayerTerrainInstaller.installStarscapeTerrain(Global.getSector()));

        runGuardedStep("Failed to install KMU political map discovery listener",
            () -> installPoliticalMapDiscoveryListener(Global.getSector()));

        runGuardedStep("Failed to install KMU political map sidebar",
            () -> installPoliticalMapSidebar(Global.getSector()));

        // Open every sidebar at the fold this save was left at. Per load rather than at construction:
        // the hosts are process-lifetime singletons built before any sector exists, so this is the only
        // point they can read the save - and it also stops the previous save's folds leaking into this
        // one. Each host reads its own key, so the screens' folds stay independent.
        runGuardedStep("Failed to restore KMU political map sidebar folds", () -> {
            for (var host : SidebarHosts.getRegisteredHosts()) {
                host.restoreFoldFromSave();
            }
        });

        // Same reason as the folds above, for the overlay's derived state: the layer renderer is
        // a process-lifetime singleton, so without this the sector just left keeps painting over
        // the one being loaded.
        runGuardedStep("Failed to discard KMU political map state from the previous save",
            PoliticalMapLayerRenderer.INSTANCE::discardStateFromPreviousSave);

        // Same reason again, for the hover box's detail mode: the holder is a process-lifetime
        // singleton, so a save left showing the expanded box would otherwise open the next one on
        // it. The mode is a live view preference and enters no save, so load is the only point it
        // can be dropped.
        runGuardedStep("Failed to discard KMU hover tooltip detail mode from the previous save",
            HoverTooltipDetailModeState.getInstance()::discardModeFromPreviousSave);

        runGuardedStep("Failed to install KMU map layer hover tooltip",
            () -> installMapLayerHoverTooltip(Global.getSector()));

        runGuardedStep("Failed to install KMU hover tooltip detail mode input",
            () -> installHoverTooltipDetailModeInput(Global.getSector()));

        runGuardedStep("Failed to install KMU political map sector watcher",
            () -> installMapLayerSectorWatcher(Global.getSector()));

        runGuardedStep("Failed to install KMU political map colony size listener",
            () -> installPoliticalMapColonySizeListener(Global.getSector()));

        runGuardedStep("Failed to install KMU political map deciv listener",
            () -> installPoliticalMapDecivListener(Global.getSector()));

        runGuardedStep("Failed to install KMU political map colonisation listener",
            () -> installPoliticalMapColonisationListener(Global.getSector()));

        // Nex-gated: no-op unless Nexerelin is enabled, so a Nex-free install
        // never loads the market-transfer listener's Nex-coupled class.
        runGuardedStep("Failed to install KMU political map market-transfer listener",
            () -> NexerelinInvasionListenerInstaller.installIfPresent(Global.getSector()));
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        installListenerOnce(
            sector, StarsectorMarketUiContextTracker.class, StarsectorMarketUiContextTracker::new);
    }

    // Registers the listener that refreshes the political map when the player
    // discovers a map-relevant entity (a market, a jump point, or a gate), so
    // the overlay updates live rather than only on reload.
    static void installPoliticalMapDiscoveryListener(SectorAPI sector) {
        installListenerOnce(
            sector, PoliticalMapDiscoveryListener.class, PoliticalMapDiscoveryListener::new);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies resizes, so a size change that flips the dominant
    // faction repaints live rather than only on reload.
    static void installPoliticalMapColonySizeListener(SectorAPI sector) {
        installListenerOnce(
            sector, PoliticalMapColonySizeListener.class, PoliticalMapColonySizeListener::new);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies decivilises, so a dying colony sheds its faction
    // colour and repaints neutral live rather than only on reload.
    static void installPoliticalMapDecivListener(SectorAPI sector) {
        installListenerOnce(
            sector, PoliticalMapDecivListener.class, PoliticalMapDecivListener::new);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when the player founds or abandons a colony in it, so planting or dropping
    // a colony repaints its system live rather than only on reload.
    static void installPoliticalMapColonisationListener(SectorAPI sector) {
        installListenerOnce(
            sector, PoliticalMapColonisationListener.class, PoliticalMapColonisationListener::new);
    }

    // Registers the per-frame watcher that refreshes the political map when a
    // change the engine fires no event for slips past the listeners - the set of
    // drawn systems shifting (a gate activating, a jump point established), a drawn
    // system changing hands (an AI colony founded in a system already on the map),
    // or a mobile system drifting to a new position. The watcher owns only the
    // cadence, so which of those count as a change is handed in as the political
    // map's own staleness source. Transient: not saved, so it is re-added fresh
    // each load and never duplicates across reloads.
    static void installMapLayerSectorWatcher(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        // Clear observed positions from any earlier save this app session: the shared
        // tracker outlives a single save, so a system id reused across saves would
        // otherwise be compared against the previous save's last-seen position until
        // the first poll re-seeds it.
        MovingSystems.getInstance().reset();

        // A fresh source per load, so the baselines it diffs against start empty rather
        // than carrying the previous save's last read into this one.
        sector.addTransientScript(
            new MapLayerSectorWatcher(new PoliticalMapStalenessSource()));
    }

    // Registers the sidebar's render and input listeners for every screen it draws on, walking the one
    // roster the rest of the mod asks its host-blind questions of. One SidebarRenderer and one
    // SidebarInput per host - a render listener that draws the panel and an input listener that reads
    // its clicks, notch, and hotkeys (a render pass gets no events to consume). All transient: each
    // host's active-layer pick lives in sector memory and the render listeners' cached GL text must
    // never enter a save, so all are re-added fresh each load. Remove-then-add per class clears any
    // persistent registration an older save captured and re-adds every host, so exactly one of each
    // renders per screen.
    static void installPoliticalMapSidebar(SectorAPI sector) {

        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        // Both classes are cleared before either is re-added, so a host is never left with one half of
        // its pair registered while the loop is partway through the roster.
        listenerManager.removeListenerOfClass(SidebarRenderer.class);
        listenerManager.removeListenerOfClass(SidebarInput.class);

        for (var host : SidebarHosts.getRegisteredHosts()) {
            listenerManager.addListener(new SidebarRenderer(host), true);
            listenerManager.addListener(new SidebarInput(host), true);
        }
    }

    // Registers the hover-tooltip dispatcher - the render listener that draws whichever tooltip the
    // active map layer injects for the star system under the cursor. Transient, remove-then-add: it
    // draws only, holds no save-relevant state, and its cached GL text must never enter a save, so it
    // is re-added fresh each load and never duplicates. Mirrors the sidebar's contract so exactly one
    // renders.
    static void installMapLayerHoverTooltip(SectorAPI sector) {
        // Both live reads are supplied rather than built by the dispatcher, so a test can stand
        // stand-ins in their place and pin the step-aside and the on-screen gate. The map read is
        // host-blind: the box draws wherever the layer paints, which is the sector map and the intel
        // screen's map visor alike, and look-blind: the layers paint through a terrain pair, so the
        // Starscape filter changes what is under the box rather than whether there is a box.
        installTransientListener(
            sector,
            MapLayerCellTooltip.class,
            () -> new MapLayerCellTooltip(
                new VanillaMapTooltip(),
                buildMapPresenceRead()));
    }

    // Registers the input listener that reads the hover box's detail-mode toggle key. A separate
    // registration from the dispatcher above rather than a second listener added beside it: the two
    // are different listener kinds claiming different halves of the same feature - a render pass gets
    // no events to consume and an input pass gets no GL context - so a failure to install either must
    // cost only its own half. Transient, remove-then-add, for the dispatcher's reasons: it holds no
    // save-relevant state, and a registration an older save carried would flip the mode twice per
    // press.
    static void installHoverTooltipDetailModeInput(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(listenerClass);
        listenerManager.addListener(buildListener.get(), true);
    }

    // Adds one listener to the sector unless a listener of that class is already registered, which a
    // reloaded save is what makes possible: these are persistent registrations, so a save carries
    // them back and adding a second would run every one of that listener's reactions twice.
    // Transient is false by that same design - the listeners are meant to survive into the save; the
    // ones that must not are added elsewhere, remove-then-add, for exactly the opposite reason.
    private static void installListenerOnce(
            SectorAPI sector, Class<?> listenerClass, Supplier<?> buildListener) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null || listenerManager.hasListenerOfClass(listenerClass)) {
            return;
        }

        // Built only once the registration is going ahead, so a reloaded save does not construct a
        // listener it is about to discard.
        listenerManager.addListener(buildListener.get(), true);
    }

    // Runs one wiring step behind its own failure boundary, so a collaborator that throws costs its
    // own registration and nothing else: the load carries on and every later step still runs. That
    // isolation is the whole reason the steps are listed rather than called in sequence - a mod
    // whose first failing install aborted the rest would come back with a half-wired sector and no
    // indication of which piece went missing.
    private static void runGuardedStep(String failureMessage, Runnable wiringStep) {
        try {
            wiringStep.run();
        } catch (RuntimeException exception) {
            LOG.error(failureMessage, exception);
        }
    }
}
