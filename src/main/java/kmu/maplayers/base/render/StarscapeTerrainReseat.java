package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;

import kmlib.starsector.ui.map.icons.MapIconReseater;
import kmlib.starsector.ui.map.presence.MapPresence;
import kmlib.starsector.ui.map.probes.MapIconLayeringProbe;

import kmu.maplayers.base.installation.InstalledMachinery;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.starsector.listeners.InstalledTransientScript;

import java.util.function.Supplier;

/**
 * One sector's lift of the above-nebulae terrain over the map's own nebula icons: the script that
 * performs it, and the slot holding it so it can be taken off again.
 *
 * <p>Moving an icon up the widget's draw order is KMLib's, and the script is told only which map
 * matters and which entity to move. What belongs here is the rest: that the entity is a terrain,
 * that the fog above it is what makes the move worth making, and that only the upper surface is
 * moved - the one beneath it is meant to be fogged, a fill still reading as owned through the nebula
 * sprite where a name stops being legible, so lifting both would undo the split the two entities
 * exist for.
 *
 * <p>Held per installation because it names one sector's script. A slot shared across sectors cannot
 * do this job: removing against a sector other than the one the held script was installed on takes
 * nothing off either sector, so the script the caller meant to stop goes on running while a second
 * sector's install quietly drops the first's reference.
 *
 * <p>Disposal releases nothing, and that is not an omission. What is held is a transient script,
 * which no save carries and which dies with the sector it was added to; a sector whose overlay is
 * switched off mid-session has its script taken off by instance first, while the installation is
 * still standing.
 */
final class StarscapeTerrainReseat implements InstalledMachinery {

    // The script this sector has running. A slot rather than a bare field so it comes off by
    // instance rather than by class: MapIconReseater is KMLib's and another mod may be running its
    // own over the same sector. The slot states why that matters; this only says which script is in
    // it.
    private final InstalledTransientScript<MapIconReseater> installedReseat =
        new InstalledTransientScript<>();

    // Reached through resolveReseatIn, so the only holders that exist are ones an installation
    // holds.
    private StarscapeTerrainReseat() {
    }

    @Override
    public void disposeMachinery() {
        // Nothing to hand back: a transient script enters no save and goes with the sector it was
        // added to, and a switched-off overlay has already removed it by instance.
    }

    /**
     * The reseat {@code installation}'s sector has, made on the first ask and released with the
     * installation holding it.
     *
     * @param installation the machinery installed on the sector the reseat runs over
     * @return that sector's reseat
     */
    static StarscapeTerrainReseat resolveReseatIn(MapLayerInstallation installation) {
        return installation.resolveMachinery(
            StarscapeTerrainReseat.class,
            StarscapeTerrainReseat::new);
    }

    /**
     * Builds this sector's reseat script and puts it on the sector, replacing whatever it held.
     *
     * <p>A fresh script per load, so the previous save's spent lift attempts cannot carry into this
     * one and stand the move down over a sector it never tried. Transient: pure runtime logic that
     * must not enter a save, so it is re-added fresh each load and never duplicates across reloads.
     *
     * @param sector the sector to put the script on and whose above-nebulae terrain it lifts; null
     *               installs nothing
     */
    void installReseatOn(SectorAPI sector) {

        // Named once and used twice: the placement read has to be asked about the same entity the
        // move is aimed at, and two copies of the lookup would be two chances for one of them to be
        // repointed at the other surface.
        //
        // Read afresh per call rather than resolved here, since a save load replaces the entity and
        // the script outlives no load anyway. The lookup closes over the sector this reseat is being
        // installed on rather than reading the running one: this holder belongs to one sector's
        // installation, so a running-sector read would have it lift another sector's terrain - and
        // leave the sector it was installed for fogged.
        Supplier<SectorEntityToken> findAboveNebulaeTerrain =
            () -> MapLayerTerrainInstaller.findAboveStarscapeNebulaeTerrain(sector);

        // The map read is the Starscape one and its sibling on MapPresence is the wrong one, which
        // is worth stating because nothing catches the swap: both compile, both are on the same
        // object, and the entity moved here is one of the halves that stand aside entirely while a
        // schematic map is up. It scopes the move rather than triggering it - moving on a schematic
        // map would shift an entity that is not drawing, for a look with nothing of ours under the
        // fog to rescue.
        //
        // Where the icon currently sits is KMLib's to read - it reads that itself rather than being
        // told, the widget's ordering being its own subject.
        installedReseat.installOn(
            sector,
            () -> new MapIconReseater(
                new MapPresence()::isStarscapeMapShowing,
                findAboveNebulaeTerrain,
                () -> MapIconLayeringProbe.readLayeringOf(findAboveNebulaeTerrain.get())));
    }

    /**
     * Takes this sector's reseat script off it, if one is held.
     *
     * @param sector the sector to remove from; null leaves the script held
     */
    void removeReseatFrom(SectorAPI sector) {
        installedReseat.removeFrom(sector);
    }
}
