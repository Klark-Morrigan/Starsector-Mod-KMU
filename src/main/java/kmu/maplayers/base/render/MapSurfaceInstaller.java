package kmu.maplayers.base.render;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmlib.starsector.ui.map.icons.MapIconReseater;
import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.MapIconLayeringProbe;
import kmlib.starsector.ui.screen.VanillaScreen;
import kmlib.starsector.ui.suppression.OffScreenWidgetSuppressor;

import kmu.starsector.listeners.SectorListeners;

import java.util.function.Supplier;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the surfaces a map layer paints on, and the frame boundary they paint within.
 *
 * <p>Three render entities, the script that keeps the upper one above the map's nebula icons, the
 * claim that keeps a frame's preparation to one, and the suppressor that stops a parked minimap
 * driving a second pass over the whole sector. Grouped because they are one subject - what exists
 * for a layer to draw into - and because their order among themselves matters: an entity has to be
 * installed before the script that moves it, and the claim has to be registered before anything
 * asks it for a frame.
 *
 * <p>Each is guarded on its own, so a surface that fails to stand up costs its own look and leaves
 * the rest painting.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class MapSurfaceInstaller {

    // The two library scripts this installs, held so they can be taken out again by instance. Both
    // are KMLib classes another mod may also be running in the same sector, so a removal by
    // class would reach further than this mod's own wiring. Per process rather than per sector: a
    // reference to a script from a sector since left is inert, since removing it from another
    // sector does nothing, and the next install overwrites it.
    private static MapIconReseater installedReseater;
    private static OffScreenWidgetSuppressor installedMinimapSuppressor;

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

        runGuardedStep(
            () -> installParkedMinimapSuppressor(sector),
            "Failed to install KMU parked minimap suppressor");
    }

    /**
     * Removes every surface this installs, for a player switching the map layers off.
     *
     * <p>All of it, and not only the terrain: the toggle takes effect where the player made it, so
     * the scripts and the claim have to stop within the session that registered them. Across a load
     * they would have gone by themselves, being transient - but a player who switched the overlay
     * off and went on playing would still have the reseat running and the frame boundary claimed.
     *
     * <p>The two library scripts are taken out by instance rather than by class, since a sibling KM
     * mod may have its own of the same class in the same sector and this must not reach it. The
     * watcher, the listeners and the terrain are all this mod's own, so a class is enough for them.
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

        runGuardedStep(
            () -> removeParkedMinimapSuppressor(sector),
            "Failed to remove KMU parked minimap suppressor");
    }

    // Stops the reseat, by the instance this installed rather than by class: MapIconReseater is
    // KMLib's and a sibling KM mod may be running its own over the same sector, which removing by
    // class would take with it. Cleared after, so a later removal cannot reach a script belonging
    // to a sector this one has since left.
    static void removeStarscapeTerrainReseater(SectorAPI sector) {

        if (sector == null || installedReseater == null) {
            return;
        }
        sector.removeTransientScript(installedReseater);
        installedReseater = null;
    }

    // Stops the suppressor, by instance and for the reason the reseat is - and with one of its own:
    // the script hands the widget it silenced its opacity back as it goes, which a removal by class
    // over somebody else's suppressor would do to a widget this mod never touched.
    static void removeParkedMinimapSuppressor(SectorAPI sector) {

        if (sector == null || installedMinimapSuppressor == null) {
            return;
        }
        sector.removeTransientScript(installedMinimapSuppressor);
        installedMinimapSuppressor = null;
    }

    // Registers the per-frame script that lifts the upper Starscape terrain over the map's nebula
    // icons whenever the widget has seeded it underneath them. Moving an icon to the end of the
    // widget's draw order is KMLib's, and it is told only which map matters and which entity to move;
    // that the entity is a terrain, and that the fog above it is what makes the move worth making,
    // are KMU's side of it. Where the icon currently sits is KMLib's too - it reads that itself
    // rather than being told, the widget's ordering being its own subject.
    //
    // Only the above-nebulae entity is moved. The surface beneath it is meant to be fogged - a fill
    // still reads as owned through the nebula sprite, where a name stops being legible - so lifting
    // both would undo the split the two entities exist for and leave the overlay laid flat beneath
    // the fog again.
    //
    // The map read is the Starscape one and its sibling on MapPresence is the wrong one, which is
    // worth stating because nothing catches the swap: both compile, both are on the same object,
    // and the entity moved here is one of the halves that stand aside entirely while a schematic map
    // is up. It scopes the move rather than triggering it - moving on a schematic map would shift an
    // entity that is not drawing, for a look with nothing of ours under the fog to rescue.
    //
    // Both ports are read afresh per call rather than resolved here, since a save load replaces the
    // entity and the script outlives no load anyway. Transient: pure runtime logic that must not
    // enter a save, so it is re-added fresh each load and never duplicates across reloads.
    static void installStarscapeTerrainReseater(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        // Named once and used twice: the placement read has to be asked about the same entity the
        // move is aimed at, and two copies of the lookup would be two chances for one of them to be
        // repointed at the other surface.
        Supplier<SectorEntityToken> findAboveNebulaeTerrain =
            () -> MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(Global.getSector());

        // A fresh script per load, so the previous save's spent lift attempts cannot carry into this
        // one and stand the move down over a sector it never tried.
        installedReseater = new MapIconReseater(
            new MapPresence()::isStarscapeMapShowing,
            findAboveNebulaeTerrain,
            () -> MapIconLayeringProbe.readLayeringOf(findAboveNebulaeTerrain.get()));

        sector.addTransientScript(installedReseater);
    }

    // Registers the render listener the map surfaces read their frame boundary from, and clears what
    // the previous session left on it first. Transient, remove-then-add, for the dispatcher's
    // reasons: it holds live view state and none of it belongs in a save.
    //
    // The clear comes first and is not conditional on the registration going ahead, because the two
    // failures it covers are the ones where no registration happens at all: a sector without a
    // listener manager, and a guarded step that throws. Either would otherwise leave the claim armed
    // by a session whose boundary pass is gone, which denies every preparation and freezes the
    // overlay - where an unarmed claim merely prepares once per pass.
    static void installMapFramePreparationClaim(SectorAPI sector) {

        MapFramePreparationClaim.getInstance().discardFrameTrackingFromPreviousSave();

        // The shared instance rather than a fresh one: the surfaces that ask reach it through the
        // singleton, so a listener built beside it would be a second claim nothing consults.
        SectorListeners.installTransientListener(
            sector,
            MapFramePreparationClaim.class,
            MapFramePreparationClaim::getInstance);
    }

    // Registers the per-frame script that stops a docked minimap rendering while its owner has it
    // parked off screen. A minimap nobody can see still renders a whole sector map and drives every
    // terrain pass in the sector, so the frame a player opened a vanilla map on carries a second
    // transform - and which of the two a cursor read resolves through is the engine's child order
    // rather than a contract.
    //
    // The split is the same one the compatibility mode is built on. What parked means and how a
    // widget is switched off are stated over any widget at all and are KMLib's; whether writing into
    // somebody else's panel is wanted is a per-mod question, and the mode is where the player answers
    // it. The screen the widget is compared against is a live read for the same reason the box is: a
    // window resized mid-session moves both.
    //
    // Transient: pure runtime logic that must not enter a save, so it is re-added fresh each load.
    // A fresh permission per load with it, so the widget walk behind it starts on this save's tree
    // rather than holding the previous one's.
    static void installParkedMinimapSuppressor(SectorAPI sector) {

        if (sector == null) {
            return;
        }
        var minimapSuppression = RandomAssortmentOfThingsMinimapSuppression.createForLiveScreen();

        installedMinimapSuppressor = new OffScreenWidgetSuppressor(
            minimapSuppression::resolveSuppressibleMinimap,
            VanillaScreen::resolveScreenBox);

        sector.addTransientScript(installedMinimapSuppressor);
    }
}
