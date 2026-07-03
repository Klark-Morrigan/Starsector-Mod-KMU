package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.politicalmap.refresh.PoliticalMapAccessWatcher;
import kmu.politicalmap.refresh.listeners.PoliticalMapColonizationListener;
import kmu.politicalmap.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.politicalmap.refresh.listeners.PoliticalMapDecivListener;
import kmu.politicalmap.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.settings.KmuLunaSettings;
import kmu.starsector.nexerelin.NexerelinInvasionListenerInstaller;
import kmu.ui.context.StarsectorMarketUiContextTracker;

import org.apache.log4j.Logger;

public class KMU_ModPlugin extends BaseModPlugin {
    public static final String MOD_ID = "kmu";
    public static final String MOD_NAME = "Klark Morrigan's Utilities";

    private static final Logger LOG = Global.getLogger(KMU_ModPlugin.class);

    @Override
    public void onApplicationLoad() {
        // App-scoped, once per launch: register KMU's LunaLib settings
        // bindings before any save loads. LunaLib is a hard dependency, so it
        // has already loaded by the time this runs.
        try {
            KmuLunaSettings.installBindings();
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU LunaLib settings bindings", exception);
        }
    }

    // Terrain type registered in data/campaign/terrain.json that hosts the
    // political map overlay's renderOnMapAbove draw. Serialised into saves;
    // do not rename.
    static final String POLITICAL_MAP_TERRAIN_TYPE = "kmu_political_terrain";

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        try {
            installMarketUiContextTracker(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU market UI context tracker", exception);
        }

        try {
            installPoliticalMapTerrain(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map terrain", exception);
        }

        try {
            installPoliticalMapDiscoveryListener(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map discovery listener", exception);
        }

        try {
            installPoliticalMapAccessWatcher(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map access watcher", exception);
        }

        try {
            installPoliticalMapColonySizeListener(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map colony size listener", exception);
        }

        try {
            installPoliticalMapDecivListener(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map deciv listener", exception);
        }

        try {
            installPoliticalMapColonizationListener(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map colonization listener", exception);
        }

        try {
            // Nex-gated: no-op unless Nexerelin is enabled, so a Nex-free install
            // never loads the market-transfer listener's Nex-coupled class.
            NexerelinInvasionListenerInstaller.installIfPresent(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map market-transfer listener", exception);
        }
    }

    static void installMarketUiContextTracker(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null || listenerManager.hasListenerOfClass(StarsectorMarketUiContextTracker.class)) {
            return;
        }

        listenerManager.addListener(new StarsectorMarketUiContextTracker(), true);
    }

    // Registers the listener that refreshes the political map when the player
    // discovers a map-relevant entity (a market, a jump point, or a gate), so
    // the overlay updates live rather than only on reload. Idempotent: a
    // reloaded save already carries it.
    static void installPoliticalMapDiscoveryListener(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null
                || listenerManager.hasListenerOfClass(PoliticalMapDiscoveryListener.class)) {
            return;
        }

        listenerManager.addListener(new PoliticalMapDiscoveryListener(), true);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies resizes, so a size change that flips the dominant
    // faction repaints live rather than only on reload. Idempotent: a reloaded
    // save already carries it.
    static void installPoliticalMapColonySizeListener(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null
                || listenerManager.hasListenerOfClass(PoliticalMapColonySizeListener.class)) {
            return;
        }

        listenerManager.addListener(new PoliticalMapColonySizeListener(), true);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when one of its colonies decivilises, so a dying colony sheds its faction
    // color and repaints neutral live rather than only on reload. Idempotent: a
    // reloaded save already carries it.
    static void installPoliticalMapDecivListener(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null
                || listenerManager.hasListenerOfClass(PoliticalMapDecivListener.class)) {
            return;
        }

        listenerManager.addListener(new PoliticalMapDecivListener(), true);
    }

    // Registers the listener that marks a system's political-map ownership stale
    // when the player founds or abandons a colony in it, so planting or dropping
    // a colony repaints its system live rather than only on reload. Idempotent: a
    // reloaded save already carries it.
    static void installPoliticalMapColonizationListener(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null
                || listenerManager.hasListenerOfClass(PoliticalMapColonizationListener.class)) {
            return;
        }

        listenerManager.addListener(new PoliticalMapColonizationListener(), true);
    }

    // Registers the per-frame watcher that refreshes the political map when the
    // set of accessible systems changes (a gate activating, a jump point
    // established) - the engine has no event for those. Transient: not saved, so
    // it is re-added fresh each load and never duplicates across reloads.
    static void installPoliticalMapAccessWatcher(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        sector.addTransientScript(new PoliticalMapAccessWatcher());
    }

    static void installPoliticalMapTerrain(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            return;
        }

        // One terrain instance per save: a reloaded save already carries it
        // (terrain persists), so skip if a copy is present to avoid stacking.
        for (var terrain : hyperspace.getTerrainCopy()) {
            if (POLITICAL_MAP_TERRAIN_TYPE.equals(terrain.getType())) {
                return;
            }
        }

        // No params: the plugin is purely a map drawer and reads system
        // positions itself, so it needs nothing passed in.
        hyperspace.addTerrain(POLITICAL_MAP_TERRAIN_TYPE, null);

        // One-shot install diagnostic. DEBUG so it stays silent at the WARN
        // default; set KMU log verbosity to DEBUG in LunaLib to see it.
        LOG.debug("Political map terrain installed; star systems="
                + sector.getStarSystems().size());
    }
}
