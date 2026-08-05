package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Puts the render pair's two terrain entities into a loaded save and keeps exactly one of each
 * there. The pair only paints because hyperspace carries an entity per variant - the schematic one
 * the map draws normally, and the starscape one it draws over the starfield - and terrain persists,
 * so every load has to add what is missing without stacking a second copy on what is already there.
 *
 * <p>It also owns the class-name lineage the save holds. Both concerns are the same one seen from
 * two sides: a terrain entity in a save names its plugin class and resolves its spec from a type id,
 * so the ids this installs under and the class names XStream can still read are what decide whether
 * an existing save comes back with a working overlay.
 *
 * <p>Kept out of the mod's entry point because none of it is wiring. The starscape trick, the sweep
 * that retires a renamed id, and the alias bridges are knowledge about this package's own classes,
 * and the entry point's job is to call two methods on the right load.
 */
public final class MapLayerTerrainInstaller {

    private static final Logger LOG = Global.getLogger(MapLayerTerrainInstaller.class);

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

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the map layers on
    // the sector map, through renderOnMap. The id is serialised into saves, so an existing save
    // holds an entity under whatever id it was written with; removeStaleMapLayerTerrain is what
    // retires one left behind by a former id, and this constant is the only id installed.
    // Renaming it again is survivable only because that sweep runs on every load.
    static final String SECTOR_MAP_LAYER_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the same layers over
    // the Starscape starfield - the mode the map widget suppresses the type above in. Its entity
    // resolves spec and plugin from this id at construction and then reports the engine's whitelisted
    // map type in the getter's place, so unlike the schematic variant the id cannot be read back off
    // a loaded entity. The sweep below therefore cannot recognise one left under a former id; a later
    // rename needs a bridge on the entity itself instead.
    static final String SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE =
        "kmu_sector_map_layer_starscape_terrain";

    // The variant the map draws outside starscape. Its entity reports the id it was installed under,
    // so one read back off a save under any other id was written by a former version of the mod and
    // is stale.
    private static final MapLayerTerrainVariant SCHEMATIC_TERRAIN = new MapLayerTerrainVariant(
        SectorMapLayerTerrainPlugin.class, SECTOR_MAP_LAYER_TERRAIN_TYPE::equals);

    // The variant the map draws over the starfield. Whatever its entity reports counts as current,
    // because it answers with the engine's whitelisted map type rather than with the id it was
    // installed under - so nothing it reports can mark it stale, and its plugin class alone is what
    // marks it as ours. Package-private alongside the type ids for the reason they are: the presence
    // guard is exercised directly, and has to be handed the wiring the install path uses rather than
    // a restatement of it.
    static final MapLayerTerrainVariant STARSCAPE_TERRAIN = new MapLayerTerrainVariant(
        SectorMapLayerStarscapeTerrainPlugin.class, reportedType -> true);

    private MapLayerTerrainInstaller() {
    }

    /**
     * Adds the terrain the sector map draws outside starscape, unless the save already carries it.
     */
    public static void installSchematicTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            SCHEMATIC_TERRAIN,
            // No params: the plugin is purely a map drawer and reads system positions itself, so it
            // needs nothing passed in. addTerrain builds the plain entity, which is all this variant
            // needs - the type it is registered under is the type it reports.
            hyperspace -> hyperspace.addTerrain(SECTOR_MAP_LAYER_TERRAIN_TYPE, null));
    }

    /**
     * Adds the terrain the sector map draws over the starfield, unless the save already carries it,
     * so one of the pair is always the one drawing.
     *
     * <p>The entity is built by hand because {@code addTerrain} always constructs a plain
     * {@code CampaignTerrain} and so could never produce the subclass whose reported type is what
     * gets this variant past the map widget's starscape filter; {@code addEntity} reaches the same
     * registration {@code addTerrain} would have.
     *
     * <p>A per-load sweep rather than a one-time seed, for a reason beyond the renames: this variant
     * is briefly taken out of hyperspace and put back on the next advance every time a map opens
     * onto the starfield, so a save written inside that one-advance window holds no starscape terrain
     * at all. This is what such a save comes back with, which is why the window needs no recovery
     * path of its own.
     */
    public static void installStarscapeTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            STARSCAPE_TERRAIN,
            hyperspace -> hyperspace.addEntity(
                new SectorMapLayerStarscapeTerrain(SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE)));
    }

    /**
     * The starscape terrain this save is carrying, or null while hyperspace holds none - which is
     * the ordinary state before {@link #installStarscapeTerrain} has run for the load, and during
     * the advance the reseat holds it out.
     *
     * <p>Published because the entity has to be reachable from outside this package without the
     * variant that identifies it being: what marks one as ours is the plugin class, and a caller
     * given that would be holding a second copy of a rule that changes whenever the pair does. Each
     * call resolves it afresh, so a caller holding the answer across a save load is holding a stale
     * entity by its own choice rather than by this handing one out.
     */
    public static CampaignTerrainAPI findStarscapeTerrain(SectorAPI sector) {
        if (sector == null) {
            return null;
        }
        var hyperspace = sector.getHyperspace();
        return hyperspace == null
            ? null
            : findMapLayerTerrain(hyperspace.getTerrainCopy(), STARSCAPE_TERRAIN);
    }

    /**
     * Maps every former terrain-plugin class name to the current class so a save written under any
     * of them still loads, then aliases the live class to itself last so XStream writes new and
     * re-saved games under the real class name - a re-saved game sheds the historical names rather
     * than carrying a dead class reference forever.
     *
     * <p>The starscape variant's entity and plugin class names are frozen into saves from their
     * first install too, but neither has a former name to bridge, so no lineage exists for them yet.
     * A rename of either needs the same treatment this gives the schematic plugin: the old name kept
     * as a read-only alias, and the live name aliased to itself last.
     *
     * <p>XStream is fully qualified because it belongs to no import group the checkstyle order
     * recognises, and this is the type's only use site.
     */
    public static void registerSaveAliases(com.thoughtworks.xstream.XStream x) {
        for (var formerClass : FORMER_TERRAIN_PLUGIN_CLASSES) {
            x.alias(formerClass, SectorMapLayerTerrainPlugin.class);
        }
        x.alias(SectorMapLayerTerrainPlugin.class.getName(), SectorMapLayerTerrainPlugin.class);
    }

    // The live terrain this location carries for the given variant, or null when it carries none.
    // Takes the terrain list rather than the location so the decision can be exercised on its own:
    // the starscape variant's install cannot be driven from a test at all, its add step constructing
    // an entity whose obfuscated supertype chain a verifying JVM refuses to load, which leaves this
    // the one decision on that path a test can reach.
    //
    // Answers with the entity rather than with a bare present/absent because the reseat needs the
    // entity itself to move, and a second by-plugin walk beside this one would be the same guard
    // written twice - of which one goes stale at the first change to how a variant is recognised.
    static CampaignTerrainAPI findMapLayerTerrain(
            List<CampaignTerrainAPI> locationTerrain,
            MapLayerTerrainVariant variant) {

        for (var terrain : locationTerrain) {
            if (variant.isCurrentTerrain(terrain)) {
                return terrain;
            }
        }
        return null;
    }

    // The install shape both variants share: retire anything of ours left under a type id this mod
    // no longer installs, then add one only if none is already present. The variants differ in how
    // they identify their own and in how the entity is built, so the first arrives as the variant
    // itself and the second as the add. Copying the block instead would leave two sweeps differing
    // only in a class literal, of which one goes stale at the first rename nobody remembers to apply
    // twice.
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
        if (findMapLayerTerrain(hyperspace.getTerrainCopy(), variant) != null) {
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

    // One of the two terrains the render pair installs, as the install has to tell them apart: the
    // plugin class that marks an entity as this variant's, and the test for whether a reported type
    // is what a live one of them carries. The two are never useful apart - every step of the install
    // needs both - so they are one value rather than a pair of parameters threaded through the
    // sweep, the presence check and the log line alike.
    record MapLayerTerrainVariant(
            Class<? extends CampaignTerrainPlugin> pluginClass,
            Predicate<String> isReportedTypeCurrent) {

        // Whether this terrain is a live one of this variant's - ours, and reporting what a live one
        // reports.
        private boolean isCurrentTerrain(CampaignTerrainAPI terrain) {
            return isOwnTerrain(terrain) && isReportedTypeCurrent.test(terrain.getType());
        }

        // Whether this terrain is one of ours left behind under a type id this mod no longer
        // installs. Only the schematic variant can ever answer true: the starscape one reports no id
        // of its own, so nothing it reports can date it.
        private boolean isStaleTerrain(CampaignTerrainAPI terrain) {
            return isOwnTerrain(terrain) && !isReportedTypeCurrent.test(terrain.getType());
        }

        // Whether this terrain is this variant's own, identified by its plugin rather than by its
        // type id: the id is the thing a rename changes, so matching on it would make the sweep blind
        // to exactly the entities it exists to find - and the starscape variant has no id to match on
        // at all. The class is compared exactly rather than with instanceof because the starscape
        // variant's plugin is a subclass of the schematic one, and each installs its own entity.
        private boolean isOwnTerrain(CampaignTerrainAPI terrain) {
            return terrain != null
                && terrain.getPlugin() != null
                && terrain.getPlugin().getClass() == pluginClass;
        }
    }
}
