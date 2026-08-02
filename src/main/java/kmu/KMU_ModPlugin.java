package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.VanillaMapTooltip;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.SectorMapLayerTerrainPlugin;
import kmu.maplayers.base.sidebar.runtime.IntelSidebarHost;
import kmu.maplayers.base.sidebar.runtime.MapSidebarHost;
import kmu.maplayers.base.sidebar.runtime.SidebarInput;
import kmu.maplayers.base.sidebar.runtime.SidebarRenderer;
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

import java.util.List;

public class KMU_ModPlugin extends BaseModPlugin {

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

    // Every fully-qualified name the terrain plugin has been saved under, oldest first. XStream
    // stores the concrete class in the save, so each rename orphaned saves written under the prior
    // name (CannotResolveClassException on load) until an alias mapped it back. They stay as
    // read-only bridges for as long as saves predating each rename might still exist.
    private static final List<String> FORMER_TERRAIN_PLUGIN_CLASSES = List.of(

        // feature-022, before the map-layers carve.
        "kmu.politicalmap.render.PoliticalMapTerrainPlugin",

        // The same pre-carve package while the class still carried the Factions prefix. This is
        // the name the earliest saves actually hold, the class having been Factions-prefixed
        // before it was un-prefixed.
        "kmu.politicalmap.render.FactionsPoliticalMapTerrainPlugin",

        // After the map-layers carve, before the shared pipeline moved from the faction package
        // to base.render.
        "kmu.maplayers.politicalmap.factions.render.FactionsPoliticalMapTerrainPlugin",

        // After that move, while the render surface still sat inside the political map - before
        // the map-layer carve lifted it into the framework and dropped the feature from its name.
        "kmu.maplayers.politicalmap.base.render.PoliticalMapTerrainPlugin");

    // Maps every former terrain-plugin class name to the current class so a save written under
    // any of them still loads, then aliases the live class to itself last so XStream writes new
    // and re-saved games under the real class name - a re-saved game sheds the historical names
    // rather than carrying a dead class reference forever. super runs first so this only adds to
    // what the base plugin configures. XStream is fully qualified here because it belongs to no
    // import group the checkstyle order recognises, and it is the type's only use site.
    @Override
    public void configureXStream(com.thoughtworks.xstream.XStream x) {
        super.configureXStream(x);
        for (var formerClass : FORMER_TERRAIN_PLUGIN_CLASSES) {
            x.alias(formerClass, SectorMapLayerTerrainPlugin.class);
        }
        x.alias(SectorMapLayerTerrainPlugin.class.getName(), SectorMapLayerTerrainPlugin.class);
    }

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the map layers on
    // the sector map, through renderOnMap. The id is serialised into saves, so an existing save
    // holds an entity under whatever id it was written with; removeStaleSectorMapLayerTerrain is
    // what retires one left behind by a former id, and this constant is the only id installed.
    // Renaming it again is survivable only because that sweep runs on every load.
    static final String SECTOR_MAP_LAYER_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    @Override
    public void onGameLoad(boolean newGame) {
        super.onGameLoad(newGame);

        try {
            installMarketUiContextTracker(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU market UI context tracker", exception);
        }

        try {
            // Self-heal pre-rename saves before the terrain reads them. One seam owns the full set of
            // political-map heals, so this call site never has to track which migrations exist.
            PoliticalMapSaveMigrations.healLoadedSave();
        } catch (RuntimeException exception) {
            LOG.error("Failed to migrate KMU political map state", exception);
        }

        try {
            installSectorMapLayerTerrain(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU sector map layer terrain", exception);
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
            // Open both sidebars at the folds this save was left at. Per load rather than at construction:
            // the hosts are process-lifetime singletons built before any sector exists, so this is the only
            // point they can read the save - and it also stops the previous save's folds leaking into this
            // one. Each host reads its own key, so the two screens' folds stay independent.
            MapSidebarHost.INSTANCE.restoreFoldFromSave();
            IntelSidebarHost.INSTANCE.restoreFoldFromSave();
        } catch (RuntimeException exception) {
            LOG.error("Failed to restore KMU political map sidebar folds", exception);
        }

        try {
            // Same reason as the folds above, for the overlay's derived state: the layer renderer is
            // a process-lifetime singleton, so without this the sector just left keeps painting over
            // the one being loaded.
            PoliticalMapLayerRenderer.INSTANCE.discardStateFromPreviousSave();
        } catch (RuntimeException exception) {
            LOG.error("Failed to discard KMU political map state from the previous save", exception);
        }

        try {
            installMapLayerHoverTooltip(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU map layer hover tooltip", exception);
        }

        try {
            installMapLayerSectorWatcher(Global.getSector());
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
            installPoliticalMapColonisationListener(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU political map colonisation listener", exception);
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
    // colour and repaints neutral live rather than only on reload. Idempotent: a
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
    static void installPoliticalMapColonisationListener(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null
                || listenerManager.hasListenerOfClass(PoliticalMapColonisationListener.class)) {
            return;
        }

        listenerManager.addListener(new PoliticalMapColonisationListener(), true);
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

    // Registers the sidebar's render and input listeners for both screens it draws on: the sector
    // map (MapSidebarHost) and the intel screen (IntelSidebarHost). One SidebarRenderer and one
    // SidebarInput per host - a render listener that draws the panel and an input listener that reads
    // its clicks, notch, and hotkeys (a render pass gets no events to consume). All transient: each
    // host's active-layer pick lives in sector memory and the render listeners' cached GL text must
    // never enter a save, so all are re-added fresh each load. Remove-then-add per class clears any
    // persistent registration an older save captured and re-adds both hosts, so exactly one of each
    // renders per screen.
    static void installPoliticalMapSidebar(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(SidebarRenderer.class);
        listenerManager.addListener(new SidebarRenderer(MapSidebarHost.INSTANCE), true);
        listenerManager.addListener(new SidebarRenderer(IntelSidebarHost.INSTANCE), true);

        listenerManager.removeListenerOfClass(SidebarInput.class);
        listenerManager.addListener(new SidebarInput(MapSidebarHost.INSTANCE), true);
        listenerManager.addListener(new SidebarInput(IntelSidebarHost.INSTANCE), true);
    }

    // Registers the hover-tooltip dispatcher - the render listener that draws whichever tooltip the
    // active map layer injects for the star system under the cursor. Transient, remove-then-add: it
    // draws only, holds no save-relevant state, and its cached GL text must never enter a save, so it
    // is re-added fresh each load and never duplicates. Mirrors the sidebar's contract so exactly one
    // renders.
    static void installMapLayerHoverTooltip(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(MapLayerCellTooltip.class);
        // The vanilla-tooltip probe is supplied rather than built by the dispatcher, so a test can
        // stand a stub in its place and pin the step-aside.
        listenerManager.addListener(new MapLayerCellTooltip(new VanillaMapTooltip()), true);
    }

    static void installSectorMapLayerTerrain(SectorAPI sector) {
        if (sector == null) {
            return;
        }

        var hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            return;
        }

        // Retire anything left under a former type id before counting what is present, so a save
        // written before a rename ends up with one live entity rather than the stale one plus a
        // freshly added replacement.
        removeStaleSectorMapLayerTerrain(hyperspace);

        // One terrain instance per save: a reloaded save already carries it
        // (terrain persists), so skip if a copy is present to avoid stacking.
        if (hasSectorMapLayerTerrain(hyperspace)) {
            return;
        }

        // No params: the plugin is purely a map drawer and reads system
        // positions itself, so it needs nothing passed in.
        hyperspace.addTerrain(SECTOR_MAP_LAYER_TERRAIN_TYPE, null);

        // One-shot install diagnostic. DEBUG so it stays silent at the WARN
        // default; set KMU log verbosity to DEBUG in LunaLib to see it.
        LOG.debug("Sector map layer terrain installed; star systems="
            + sector.getStarSystems().size());
    }

    // Retires map-layer terrain carrying a type id this mod no longer installs. The id is
    // serialised into the save, so a save written before a rename still holds an entity under the
    // old one, whose spec no longer resolves; leaving it would also slip past the presence check
    // and stack a second overlay on top of it, painting every fill at doubled alpha.
    private static void removeStaleSectorMapLayerTerrain(LocationAPI hyperspace) {
        // getTerrainCopy hands back a copy, so removing while walking it is safe.
        for (var terrain : hyperspace.getTerrainCopy()) {
            if (isSectorMapLayerTerrain(terrain)
                    && !SECTOR_MAP_LAYER_TERRAIN_TYPE.equals(terrain.getType())) {
                hyperspace.removeEntity(terrain);
                LOG.debug("Retired sector map layer terrain under former type id "
                    + terrain.getType());
            }
        }
    }

    private static boolean hasSectorMapLayerTerrain(LocationAPI hyperspace) {
        for (var terrain : hyperspace.getTerrainCopy()) {
            if (isSectorMapLayerTerrain(terrain)
                    && SECTOR_MAP_LAYER_TERRAIN_TYPE.equals(terrain.getType())) {
                return true;
            }
        }
        return false;
    }

    // Whether this terrain is the map layers' own, identified by its plugin rather than by its
    // type id: the id is the thing a rename changes, so matching on it would make the sweep blind
    // to exactly the entities it exists to find. The class is compared exactly rather than with
    // instanceof because the starscape half's plugin is a subclass of this one, and each half owns
    // and installs its own entity.
    private static boolean isSectorMapLayerTerrain(CampaignTerrainAPI terrain) {
        return terrain != null
            && terrain.getPlugin() != null
            && terrain.getPlugin().getClass() == SectorMapLayerTerrainPlugin.class;
    }
}
