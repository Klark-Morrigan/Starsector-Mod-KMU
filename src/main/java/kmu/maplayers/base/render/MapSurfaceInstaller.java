package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.listeners.SectorListeners;

import kmu.maplayers.base.machinery.SectorMapMachineryIndex;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the surfaces a map layer paints on, and the frame boundary they paint within.
 *
 * <p>Three render entities, the script that keeps the upper one above the map's nebula icons, and
 * the claim that keeps a frame's preparation to one. Grouped because they are one subject - what
 * exists for a layer to draw into - and because their order among themselves matters: an entity has
 * to be installed before the script that moves it, and the claim has to be registered before
 * anything asks it for a frame.
 *
 * <p>The parked-minimap suppressor is not among them, for all that it is a script over the same
 * map. Nothing about it is a surface - it neither draws nor is drawn into - and it answers a
 * compatibility mode aimed at another mod's widget rather than anything this one paints, so it
 * stands or falls on that switch instead: {@link ParkedMinimapInstaller} owns it.
 *
 * <p>Each is guarded on its own, so a surface that fails to stand up costs its own look and leaves
 * the rest painting.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MapSurfaceInstaller {

    private MapSurfaceInstaller() {
        // utility class, no instances.
    }

    /**
     * Installs every render surface and its per-frame machinery, each step behind its own failure
     * boundary.
     *
     * @param sector the loaded sector; null leaves the surfaces uninstalled rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        runGuardedStep(
            () -> MapLayerTerrainInstaller.installSchematicTerrain(sector),
            "Failed to install KMU sector map layer terrain");

        // The second render surface, guarded separately so a failure to build the Starscape entity -
        // the variant reaching concrete core classes - cannot take the schematic one down with it and
        // leave the map painting nothing in either mode.
        runGuardedStep(
            () -> MapLayerTerrainInstaller.installStarscapeTerrain(sector),
            "Failed to install KMU sector map layer Starscape terrain");

        // The third surface, which paints over the map's own nebula icons. Guarded apart from the
        // one above for the same reason it is guarded apart from the schematic one, and because
        // losing it costs only the band that clears the fog rather than the whole Starscape overlay.
        runGuardedStep(
            () -> MapLayerTerrainInstaller.installAboveStarscapeNebulaeTerrain(sector),
            "Failed to install KMU sector map layer Starscape above-nebulae terrain");

        runGuardedStep(
            () -> installStarscapeTerrainReseater(sector),
            "Failed to install KMU sector map layer Starscape terrain reseater");

        // Before the surfaces that ask it, and before the hover box installed after this one: the
        // claim is what keeps a frame's preparation to one, and until it is registered the surfaces
        // fall back to preparing per pass, so a late registration costs duplicated work rather than
        // a wrong picture.
        runGuardedStep(
            () -> installMapFramePreparationClaim(sector),
            "Failed to install KMU map layer frame preparation claim");
    }

    /**
     * Removes every surface this installs, for a player switching the map layers off.
     *
     * <p>All of it, and not only the terrain: the toggle takes effect where the player made it, so
     * the reseat and the claim have to stop within the session that registered them. Across a load
     * they would have gone by themselves, being transient - but a player who switched the overlay
     * off and went on playing would still have the reseat running and the frame boundary claimed.
     *
     * <p>The reseat is taken out by instance rather than by class, since another mod may have its
     * own of that KMLib class in the same sector and this must not reach it. The claim and the
     * terrain are this mod's own, so a class is enough for them.
     *
     * @param sector the loaded sector; null leaves the save untouched rather than throwing
     */
    public static void uninstallAll(SectorAPI sector) {

        runGuardedStep(
            () -> MapLayerTerrainInstaller.removeMapLayerTerrain(sector),
            "Failed to remove KMU sector map layer terrain");

        runGuardedStep(
            () -> removeStarscapeTerrainReseater(sector),
            "Failed to remove KMU sector map layer Starscape terrain reseater");

        runGuardedStep(
            () -> SectorListeners.removeListener(sector, MapFramePreparationClaim.class),
            "Failed to remove KMU map layer frame preparation claim");
    }

    // Takes off the lift this sector had running, reached through that sector's own machinery so
    // the slot asked is the one the script went into. What the lift is and why it is worth making is
    // StarscapeTerrainReseat's; this only says when it stops.
    static void removeStarscapeTerrainReseater(SectorAPI sector) {
        StarscapeTerrainReseat
            .resolveReseatIn(SectorMapMachineryIndex.resolveMachineryFor(sector))
            .removeReseatFrom(sector);
    }

    // Puts the per-frame lift of the upper Starscape terrain onto this sector, through that sector's
    // own machinery for the reason the removal above goes through it.
    static void installStarscapeTerrainReseater(SectorAPI sector) {
        StarscapeTerrainReseat
            .resolveReseatIn(SectorMapMachineryIndex.resolveMachineryFor(sector))
            .installReseatOn(sector);
    }

    // Registers the render listener the map surfaces read their frame boundary from. Transient,
    // remove-then-add, for the dispatcher's reasons: it holds live view state and none of it belongs
    // in a save.
    //
    // The machinery's own claim rather than a fresh one, because that is the claim the surfaces
    // over this sector ask: a listener built beside it would open frames on a claim nothing consults,
    // and every surface would go on preparing per pass. It arrives unarmed without being cleared
    // here, machinery being made fresh when the layers are installed - so a claim left mid-frame
    // by the session before went with the machinery that held it, and a load that fails to
    // register anything falls back to preparing per pass rather than to a frozen overlay.
    static void installMapFramePreparationClaim(SectorAPI sector) {

        var machinery = SectorMapMachineryIndex.resolveMachineryFor(sector);

        SectorListeners.installListener(
            sector,
            MapFramePreparationClaim.class,
            () -> MapFramePreparationClaim.resolveClaimIn(machinery));
    }
}
