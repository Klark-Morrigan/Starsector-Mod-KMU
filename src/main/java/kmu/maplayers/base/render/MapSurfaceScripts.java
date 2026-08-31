package kmu.maplayers.base.render;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.map.icons.MapIconReseater;

import kmu.maplayers.base.installation.InstalledMachinery;
import kmu.maplayers.base.installation.MapLayerInstallation;
import kmu.starsector.listeners.InstalledTransientScript;

import java.util.function.Supplier;

/**
 * The per-frame scripts one sector's render surfaces have running, held so each can be taken off
 * again by instance.
 *
 * <p>One reseat today: the script that lifts the above-nebulae terrain over the map's own nebula
 * icons. It is held per installation rather than in a static because it names one sector's script -
 * a removal aimed at the sector it was installed on would otherwise reach whatever the last install
 * anywhere happened to leave in the slot, and a second sector's install would drop the first's
 * script without taking it off the sector still running it.
 *
 * <p>Disposal releases nothing, and that is not an omission. What is held is a transient script,
 * which no save carries and which dies with the sector it was added to; a sector whose overlay is
 * switched off mid-session has its script taken off by name first, through the uninstall, while the
 * installation is still standing.
 */
final class MapSurfaceScripts implements InstalledMachinery {

    // The reseat this sector has running, held so it can be taken off by instance rather than by
    // class: MapIconReseater is KMLib's and another mod may be running its own over the same sector.
    // The slot states why that matters; this only says which script is in it.
    private final InstalledTransientScript<MapIconReseater> installedReseater =
        new InstalledTransientScript<>();

    // Reached through resolveScriptsIn, so the only holders that exist are ones an installation
    // holds.
    private MapSurfaceScripts() {
    }

    @Override
    public void disposeMachinery() {
        // Nothing to hand back: a transient script enters no save and goes with the sector it was
        // added to, and a switched-off overlay has already removed it by instance.
    }

    /**
     * The scripts {@code installation}'s surfaces have running, made on the first ask and released
     * with the installation holding them.
     *
     * @param installation the machinery installed on the sector the scripts run over
     * @return that sector's script holder
     */
    static MapSurfaceScripts resolveScriptsIn(MapLayerInstallation installation) {
        return installation.resolveMachinery(MapSurfaceScripts.class, MapSurfaceScripts::new);
    }

    /**
     * Builds a reseat, adds it to {@code sector} as a transient script, and holds it for removal.
     *
     * @param sector       the sector to add to; null installs nothing
     * @param buildReseater makes the script, called only where there is a sector to install it on
     */
    void installReseaterOn(SectorAPI sector, Supplier<MapIconReseater> buildReseater) {
        installedReseater.installOn(sector, buildReseater);
    }

    /**
     * Takes the held reseat off {@code sector}, if this holder has one.
     *
     * @param sector the sector to remove from; null leaves the script held
     */
    void removeReseaterFrom(SectorAPI sector) {
        installedReseater.removeFrom(sector);
    }
}
