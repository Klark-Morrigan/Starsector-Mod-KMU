package kmu;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.presence.SchematicMapPresence;
import kmlib.starsector.ui.map.probes.VanillaMapTooltip;

import kmu.maplayers.MapLayers;
import kmu.maplayers.base.refresh.MapLayerSectorWatcher;
import kmu.maplayers.base.refresh.MovingSystems;
import kmu.maplayers.base.render.SectorMapLayerStarscapeTerrain;
import kmu.maplayers.base.render.SectorMapLayerStarscapeTerrainPlugin;
import kmu.maplayers.base.render.SectorMapLayerTerrainPlugin;
import kmu.maplayers.base.sidebar.runtime.SidebarHosts;
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
import java.util.function.Consumer;
import java.util.function.Predicate;

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

    // The starscape variant's entity and plugin class names are frozen into saves from their first
    // install too, but neither has a former name to bridge, so no lineage exists for them yet. A
    // rename of either needs the same treatment this list gives the base plugin: the old name kept
    // as a read-only alias, and the live name aliased to itself last.
    //
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

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the same layers over
    // the Starscape starfield - the mode the map widget suppresses the type above in. Its entity
    // resolves spec and plugin from this id at construction and then reports the engine's whitelisted
    // map type in the getter's place, so unlike the base variant the id cannot be read back off a
    // loaded entity. The sweep below therefore cannot recognise one left under a former id; a later
    // rename needs a bridge on the entity itself instead.
    static final String SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE =
        "kmu_sector_map_layer_starscape_terrain";

    // The base variant. Its entity reports the id it was installed under, so one read back off a save
    // under any other id was written by a former version of the mod and is stale.
    private static final MapLayerTerrainVariant BASE_TERRAIN = new MapLayerTerrainVariant(
        SectorMapLayerTerrainPlugin.class, SECTOR_MAP_LAYER_TERRAIN_TYPE::equals);

    // The starscape variant. Whatever its entity reports counts as current, because it answers with
    // the engine's whitelisted map type rather than with the id it was installed under - so nothing
    // it reports can mark it stale, and its plugin class alone is what marks it as ours.
    // Package-private alongside the type ids for the reason they are: the presence guard is exercised
    // directly, and has to be handed the wiring the install path uses rather than a restatement of it.
    static final MapLayerTerrainVariant STARSCAPE_TERRAIN = new MapLayerTerrainVariant(
        SectorMapLayerStarscapeTerrainPlugin.class, reportedType -> true);

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
            // The second terrain of the render pair, installed in its own try so a failure to build
            // the starscape entity - the variant reaching concrete core classes - cannot take the
            // base one down with it and leave the map painting nothing in either mode.
            installSectorMapLayerStarscapeTerrain(Global.getSector());
        } catch (RuntimeException exception) {
            LOG.error("Failed to install KMU sector map layer starscape terrain", exception);
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
            // Open every sidebar at the fold this save was left at. Per load rather than at construction:
            // the hosts are process-lifetime singletons built before any sector exists, so this is the only
            // point they can read the save - and it also stops the previous save's folds leaking into this
            // one. Each host reads its own key, so the screens' folds stay independent.
            for (var host : SidebarHosts.getRegisteredHosts()) {
                host.restoreFoldFromSave();
            }
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
        if (sector == null) {
            return;
        }

        var listenerManager = sector.getListenerManager();
        if (listenerManager == null) {
            return;
        }

        listenerManager.removeListenerOfClass(MapLayerCellTooltip.class);
        // Both live reads are supplied rather than built by the dispatcher, so a test can stand
        // stand-ins in their place and pin the step-aside and the on-screen gate. The map read is
        // host-blind: the box draws wherever the layer paints, which is the sector map and the intel
        // screen's map visor alike.
        listenerManager.addListener(
            new MapLayerCellTooltip(
                new VanillaMapTooltip(),
                new SchematicMapPresence()::isSchematicMapShowing),
            true);
    }

    static void installSectorMapLayerTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            BASE_TERRAIN,
            // No params: the plugin is purely a map drawer and reads system positions itself, so it
            // needs nothing passed in. addTerrain builds the plain entity, which is all this variant
            // needs - the type it is registered under is the type it reports.
            hyperspace -> hyperspace.addTerrain(SECTOR_MAP_LAYER_TERRAIN_TYPE, null));
    }

    // Installs the starscape variant beside the base one, so one of the pair is always the one
    // drawing. The entity is built by hand because addTerrain always constructs a plain
    // CampaignTerrain and so could never produce the subclass whose reported type is what gets this
    // variant past the map widget's starscape filter; addEntity reaches the same registration
    // addTerrain would have.
    static void installSectorMapLayerStarscapeTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            STARSCAPE_TERRAIN,
            hyperspace -> hyperspace.addEntity(
                new SectorMapLayerStarscapeTerrain(SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE)));
    }

    // Whether this location already carries the given variant's terrain. Takes the terrain list
    // rather than the location so the decision can be exercised on its own: the starscape variant's
    // install cannot be driven from a test at all, its add step constructing an entity whose
    // obfuscated supertype chain a verifying JVM refuses to load, which leaves this the one decision
    // on that path a test can reach.
    static boolean hasMapLayerTerrain(
            List<CampaignTerrainAPI> locationTerrain,
            MapLayerTerrainVariant variant) {

        for (var terrain : locationTerrain) {
            if (variant.isCurrentTerrain(terrain)) {
                return true;
            }
        }
        return false;
    }

    // The install shape both variants of the render pair share: retire anything of ours left under a
    // type id this mod no longer installs, then add one only if none is already present. The variants
    // differ in how they identify their own and in how the entity is built, so the first arrives as
    // the variant itself and the second as the add. Copying the block instead would leave two sweeps
    // differing only in a class literal, of which one goes stale at the first rename nobody
    // remembers to apply twice.
    private static void installMapLayerTerrain(
            SectorAPI sector,
            MapLayerTerrainVariant variant,
            Consumer<LocationAPI> addTerrainToLocation) {

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
        removeStaleMapLayerTerrain(hyperspace, variant);

        // One terrain instance per save: a reloaded save already carries it
        // (terrain persists), so skip if a copy is present to avoid stacking.
        if (hasMapLayerTerrain(hyperspace.getTerrainCopy(), variant)) {
            return;
        }

        addTerrainToLocation.accept(hyperspace);

        // One-shot install diagnostic, naming the variant so the pair is distinguishable in a log.
        // DEBUG so it stays silent at the WARN default; set KMU log verbosity to DEBUG in LunaLib
        // to see it.
        LOG.debug("Map layer terrain installed for " + variant.pluginClass().getSimpleName()
            + "; star systems=" + sector.getStarSystems().size());
    }

    // Retires map-layer terrain carrying a type id this mod no longer installs. The id is
    // serialised into the save, so a save written before a rename still holds an entity under the
    // old one, whose spec no longer resolves; leaving it would also slip past the presence check
    // and stack a second overlay on top of it, painting every fill at doubled alpha.
    private static void removeStaleMapLayerTerrain(
            LocationAPI hyperspace,
            MapLayerTerrainVariant variant) {

        // getTerrainCopy hands back a copy, so removing while walking it is safe.
        for (var terrain : hyperspace.getTerrainCopy()) {
            if (variant.isStaleTerrain(terrain)) {
                hyperspace.removeEntity(terrain);
                LOG.debug("Retired map layer terrain under former type id " + terrain.getType());
            }
        }
    }

    // One of the two terrains the sector map's render pair installs, as the install has to tell them
    // apart: the plugin class that marks an entity as this variant's, and the test for whether a
    // reported type is what a live one of them carries. The two are never useful apart - every step
    // of the install needs both - so they are one value rather than a pair of parameters threaded
    // through the sweep, the presence check and the log line alike.
    record MapLayerTerrainVariant(
        Class<? extends CampaignTerrainPlugin> pluginClass,
        Predicate<String> isReportedTypeCurrent) {

        // Whether this terrain is a live one of this variant's - ours, and reporting what a live one
        // reports.
        private boolean isCurrentTerrain(CampaignTerrainAPI terrain) {
            return isOwnTerrain(terrain) && isReportedTypeCurrent.test(terrain.getType());
        }

        // Whether this terrain is one of ours left behind under a type id this mod no longer
        // installs. Only the base variant can ever answer true: the starscape one reports no id of
        // its own, so nothing it reports can date it.
        private boolean isStaleTerrain(CampaignTerrainAPI terrain) {
            return isOwnTerrain(terrain) && !isReportedTypeCurrent.test(terrain.getType());
        }

        // Whether this terrain is this variant's own, identified by its plugin rather than by its
        // type id: the id is the thing a rename changes, so matching on it would make the sweep blind
        // to exactly the entities it exists to find - and the starscape variant has no id to match on
        // at all. The class is compared exactly rather than with instanceof because the starscape
        // variant's plugin is a subclass of the base one, and each installs its own entity.
        private boolean isOwnTerrain(CampaignTerrainAPI terrain) {
            return terrain != null
                && terrain.getPlugin() != null
                && terrain.getPlugin().getClass() == pluginClass;
        }
    }
}
