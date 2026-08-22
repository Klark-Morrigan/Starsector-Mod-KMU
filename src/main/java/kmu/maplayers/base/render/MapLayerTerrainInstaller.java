package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignTerrainAPI;
import com.fs.starfarer.api.campaign.CampaignTerrainPlugin;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import org.apache.log4j.Logger;

import java.util.List;
import java.util.function.Consumer;

/**
 * Puts the render surfaces' terrain entities into a loaded save and keeps exactly one of each there.
 * The overlay only paints because hyperspace carries an entity per variant - the schematic one the
 * map draws normally, and the two it draws in Starscape mode, one either side of its nebula effects -
 * and terrain persists, so every load has to add what is missing without stacking a second copy on
 * what is already there.
 */
public final class MapLayerTerrainInstaller {

    private static final Logger LOG = Global.getLogger(MapLayerTerrainInstaller.class);

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the map layers on
    // the sector map, through renderOnMap. The id is serialised into saves, so it is frozen once
    // shipped for the reason the plugin class name is: an existing save holds an entity under
    // whatever id it was written with, and nothing bridges a renamed one back.
    static final String SECTOR_MAP_LAYER_TERRAIN_TYPE = "kmu_sector_map_layer_terrain";

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the same layers over
    // in Starscape mode - the mode the map widget suppresses the type above in. Its entity
    // resolves spec and plugin from this id at construction and then reports the engine's whitelisted
    // map type in the getter's place, so unlike the schematic variant the id cannot be read back off
    // a loaded entity at all.
    static final String SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE =
        "kmu_sector_map_layer_starscape_terrain";

    // Terrain type registered in data/campaign/terrain.json whose plugin paints the upper band over
    // the nebulae - the part of the overlay the map's own fog must not cover. Its entity is the
    // same class the row above installs, differing only in the id it resolves its plugin from.
    static final String SECTOR_MAP_LAYER_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE =
        "kmu_sector_map_layer_above_starscape_nebulae_terrain";

    // Which terrain in hyperspace belongs to which variant, and the whole of what tells the three
    // apart: the plugin class its entity carries. Not the type id, which two of them never report -
    // those entities answer with the engine's whitelisted map type instead, so nothing they say
    // about themselves distinguishes one from the other. Package-private alongside the type ids so
    // the presence guard names the same constants the install path does rather than a restatement
    // of them.
    private static final Class<? extends CampaignTerrainPlugin> SCHEMATIC_TERRAIN =
        SectorMapLayerTerrainPlugin.class;

    static final Class<? extends CampaignTerrainPlugin> STARSCAPE_TERRAIN =
        SectorMapLayerStarscapeTerrainPlugin.class;

    static final Class<? extends CampaignTerrainPlugin> ABOVE_STARSCAPE_NEBULAE_TERRAIN =
        SectorMapLayerAboveStarscapeNebulaeTerrainPlugin.class;

    private MapLayerTerrainInstaller() {
    }

    /**
     * Adds the terrain the sector map draws outside Starscape, unless the save already carries it.
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
     * Adds the terrain the sector map draws in Starscape mode, unless the save already carries it,
     * so a map is never left with neither surface drawing.
     *
     * <p>The entity is built by hand because {@code addTerrain} always constructs a plain
     * {@code CampaignTerrain} and so could never produce the subclass whose reported type is what
     * gets this variant past the map widget's Starscape filter; {@code addEntity} reaches the same
     * registration {@code addTerrain} would have.
     *
     * <p>It stays a per-load sweep even though this variant is never moved, because a renamed row id
     * would otherwise strand the entity an existing save holds - and because the sweep is what the
     * shared helper does for every variant regardless.
     */
    public static void installStarscapeTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            STARSCAPE_TERRAIN,
            hyperspace -> hyperspace.addEntity(
                new SectorMapLayerStarscapeTerrain(SECTOR_MAP_LAYER_STARSCAPE_TERRAIN_TYPE)));
    }

    /**
     * Adds the terrain the sector map draws over its own nebula icons, unless the save already
     * carries it. Missing it costs the upper band rather than the whole overlay: the surface below
     * paints its own band either way, and what would have ridden above the nebulae simply stops
     * appearing.
     *
     * <p>The entity is the same class the surface below installs, differing only in the type id it
     * resolves its spec and plugin from, since reporting the whitelisted map type is the whole of
     * what that class does.
     *
     * <p>A per-load sweep rather than a one-time seed, for a reason beyond the renames: this variant
     * is briefly taken out of hyperspace and put back on the next advance every time a map opens onto
     * Starscape, which is how its icon is lifted past the nebulae. A save written inside that
     * one-advance window holds no upper-band terrain at all, and this is what such a save comes back
     * with - which is why the window needs no recovery path of its own.
     */
    public static void installAboveStarscapeNebulaeTerrain(SectorAPI sector) {
        installMapLayerTerrain(
            sector,
            ABOVE_STARSCAPE_NEBULAE_TERRAIN,
            hyperspace -> hyperspace.addEntity(new SectorMapLayerStarscapeTerrain(
                SECTOR_MAP_LAYER_ABOVE_STARSCAPE_NEBULAE_TERRAIN_TYPE)));
    }

    /**
     * Removes every render surface this mod installs, so a save carries none of them.
     *
     * <p>What switching the map layers off has to do, and the only part of the wiring that does:
     * every listener and script the surfaces install is transient and simply never registered
     * again, but terrain is an entity and entities persist, so one left behind would sit in
     * hyperspace for as long as the save exists.
     *
     * <p>Identified by plugin class, as everything here is - a third party's terrain is never
     * touched.
     *
     * @param sector the sector to clear; null is a no-op
     */
    public static void removeMapLayerTerrain(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        var hyperspace = sector.getHyperspace();

        if (hyperspace == null) {
            return;
        }
        // getTerrainCopy hands back a copy, so removing while walking it is safe.
        for (var terrain : hyperspace.getTerrainCopy()) {

            if (isMapLayerTerrain(terrain)) {
                hyperspace.removeEntity(terrain);
                LOG.debug("Removed map layer terrain " + terrain.getPlugin().getClass().getSimpleName());
            }
        }
    }

    /**
     * The above-nebulae terrain this save is carrying, or null while hyperspace holds none - which is
     * the ordinary state before {@link #installAboveStarscapeNebulaeTerrain} has run for the load, and
     * during the advance the reseat holds it out.
     *
     * <p>This is the one variant that is ever moved, so it is the one variant with a published read.
     * Moving either of the others would lift geometry the nebulae are meant to fog, or move an
     * entity the engine is not drawing at all.
     *
     * <p>Published because the entity has to be reachable from outside this package without the
     * variant that identifies it being: what marks one as ours is the plugin class, and a caller
     * given that would be holding a second copy of a rule that changes whenever the surfaces do. Each
     * call resolves it afresh, so a caller holding the answer across a save load is holding a stale
     * entity by its own choice rather than by this handing one out.
     */
    public static CampaignTerrainAPI findAboveStarscapeNebulaeTerrain(SectorAPI sector) {
        if (sector == null) {
            return null;
        }
        var hyperspace = sector.getHyperspace();
        return hyperspace == null
            ? null
            : findMapLayerTerrain(hyperspace.getTerrainCopy(), ABOVE_STARSCAPE_NEBULAE_TERRAIN);
    }

    // The terrain this location carries for the given variant, or null when it carries none.
    // Takes the terrain list rather than the location so the recognition rule stands on its own,
    // apart from the walk that fetches a location's terrain and apart from the entity construction
    // an install carries out around it.
    //
    // Answers with the entity rather than with a bare present/absent because the reseat needs the
    // entity itself to move, and a second by-plugin walk beside this one would be the same guard
    // written twice - of which one goes stale at the first change to how a variant is recognised.
    //
    // The plugin class is compared exactly rather than with instanceof because the three plugins
    // form a subclass chain - each Starscape variant extends the one before it - and each installs
    // its own entity, so an instanceof match would let one variant answer for another's.
    static CampaignTerrainAPI findMapLayerTerrain(
            List<CampaignTerrainAPI> locationTerrain,
            Class<? extends CampaignTerrainPlugin> pluginClass) {

        for (var terrain : locationTerrain) {

            if (isTerrainOfPlugin(terrain, pluginClass)) {
                return terrain;
            }
        }
        return null;
    }

    // Whether this terrain is one of the three the render surfaces install, whichever it is - what
    // a wholesale removal asks, where every read above asks after one variant.
    private static boolean isMapLayerTerrain(CampaignTerrainAPI terrain) {

        return isTerrainOfPlugin(terrain, SCHEMATIC_TERRAIN)
            || isTerrainOfPlugin(terrain, STARSCAPE_TERRAIN)
            || isTerrainOfPlugin(terrain, ABOVE_STARSCAPE_NEBULAE_TERRAIN);
    }

    private static boolean isTerrainOfPlugin(
            CampaignTerrainAPI terrain,
            Class<? extends CampaignTerrainPlugin> pluginClass) {

        return terrain != null
            && terrain.getPlugin() != null
            && terrain.getPlugin().getClass() == pluginClass;
    }

    // The install shape all three variants share: add one only if none is already present. They
    // differ in the plugin class that identifies their own and in how the entity is built, so the
    // first arrives as that class and the second as the add. Copying the block instead would leave
    // three presence checks differing only in a class literal.
    private static void installMapLayerTerrain(
            SectorAPI sector,
            Class<? extends CampaignTerrainPlugin> pluginClass,
            Consumer<LocationAPI> addTerrainToLocation) {

        if (sector == null) {
            return;
        }

        var hyperspace = sector.getHyperspace();
        if (hyperspace == null) {
            return;
        }

        // One terrain instance per save: a reloaded save already carries it
        // (terrain persists), so skip if a copy is present to avoid stacking.
        if (findMapLayerTerrain(hyperspace.getTerrainCopy(), pluginClass) != null) {
            return;
        }

        addTerrainToLocation.accept(hyperspace);

        // One-shot install diagnostic, naming the variant so the surfaces are told apart in a log.
        // DEBUG so it stays silent at the WARN default; set KMU log verbosity to DEBUG in LunaLib
        // to see it.
        LOG.debug("Map layer terrain installed for " + pluginClass.getSimpleName()
            + "; star systems=" + sector.getStarSystems().size());
    }
}
