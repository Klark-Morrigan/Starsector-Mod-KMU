package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.sidebar.runtime.MapLayerSidebar;
import kmu.maplayers.base.sidebar.runtime.MapLayerSidebarInput;
import kmu.maplayers.politicalmap.base.refresh.MovingSystems;
import kmu.maplayers.politicalmap.base.refresh.PoliticalMapSectorWatcher;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonizationListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapColonySizeListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDecivListener;
import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapDiscoveryListener;
import kmu.maplayers.politicalmap.base.render.PoliticalMapTerrainPlugin;
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

        // Wire the concrete map layers into the framework registry once per launch, before any
        // sector map can open. The registry stays agnostic to which views exist; this is the
        // one place they are named.
        try {
            MapLayers.registerAll();
        } catch (RuntimeException exception) {
            LOG.error("Failed to register KMU map layers", exception);
        }
    }

    // The terrain plugin's feature-022 fully-qualified name, before the map-layers carve.
    // XStream stores the concrete class in the save, so each rename orphaned saves written under
    // the prior name (CannotResolveClassException on load) until an alias mapped it back. Bridged
    // read-only below so a save still holding this name loads.
    private static final String LEGACY_TERRAIN_PLUGIN_CLASS =
            "kmu.politicalmap.render.PoliticalMapTerrainPlugin";

    // The terrain plugin's class name after the map-layers carve but before the shared pipeline
    // moved from the faction package to base.render - bridged read-only for the same reason as
    // the name above.
    private static final String FACTION_PACKAGE_TERRAIN_PLUGIN_CLASS =
            "kmu.maplayers.politicalmap.factions.render.FactionsPoliticalMapTerrainPlugin";

    // Maps every former terrain-plugin class name to the current class so a save written under
    // any of them still loads, then aliases the live class to itself last so XStream writes new
    // and re-saved games under the real class name - a re-saved game sheds the historical names
    // rather than carrying a dead class reference forever. The former-name aliases stay as
    // read-only bridges for as long as saves predating each rename might still exist. super runs
    // first so this only adds to what the base plugin configures. XStream is fully qualified here
    // because it belongs to no import group the checkstyle order recognises, and it is the type's
    // only use site.
    @Override
    public void configureXStream(com.thoughtworks.xstream.XStream x) {
        super.configureXStream(x);
        x.alias(LEGACY_TERRAIN_PLUGIN_CLASS, PoliticalMapTerrainPlugin.class);
        x.alias(FACTION_PACKAGE_TERRAIN_PLUGIN_CLASS, PoliticalMapTerrainPlugin.class);
        x.alias(PoliticalMapTerrainPlugin.class.getName(), PoliticalMapTerrainPlugin.class);
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
            installPoliticalMapSidebar(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map sidebar", exception);
        }

        try {
            installPoliticalMapSectorWatcher(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map sector watcher", exception);
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

    // Registers the per-frame watcher that refreshes the political map when a
    // change the engine fires no event for slips past the listeners - the set of
    // drawn systems shifting (a gate activating, a jump point established), a drawn
    // system changing hands (an AI colony founded in a system already on the map),
    // or a mobile system drifting to a new position. Transient: not saved, so it is
    // re-added fresh each load and never duplicates across reloads.
    static void installPoliticalMapSectorWatcher(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        // Clear observed positions from any earlier save this app session: the shared
        // tracker outlives a single save, so a system id reused across saves would
        // otherwise be compared against the previous save's last-seen position until
        // the first poll re-seeds it.
        MovingSystems.getInstance().reset();
        sector.addTransientScript(new PoliticalMapSectorWatcher());
    }

    // Registers the layer bar's two listeners: the UI-coords render listener that draws the
    // bar on the sector map, and the input listener that reads its clicks and hotkeys. Both
    // transient: the active-layer pick lives in sector memory and the render listener's cached
    // GL text must never enter a save, so both are re-added fresh each load. Remove-then-add
    // keeps the install idempotent and clears any persistent registration an older save
    // captured, so exactly one of each renders.
    static void installPoliticalMapSidebar(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(MapLayerSidebar.class);
        listenerManager.addListener(new MapLayerSidebar(), true);

        // The bar's paint and its input are two listeners: the render listener above draws it,
        // this input listener reads its clicks and hotkeys (a render pass gets no events to
        // consume). Same transient, remove-then-add contract, so exactly one of each renders.
        listenerManager.removeListenerOfClass(MapLayerSidebarInput.class);
        listenerManager.addListener(new MapLayerSidebarInput(), true);
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
