package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.starsector.listeners.SectorListeners;

import static kmu.KmuWiringSteps.runGuardedStep;

/**
 * Standing up the record of where the player has seen each colony: bringing it into step with the
 * loaded save, and keeping it written as they travel.
 *
 * <p>Runs before any surface reads a colony set, since a revelation gate answers off this register
 * - a derelict or a concealed base is shown where somebody has seen it standing, and the register
 * is what somebody-has-seen-it means.
 *
 * <p>Final class with a private constructor: pure-function utility, no instance state.
 */
public final class ColonySightingInstaller {

    private ColonySightingInstaller() {
        // utility class, no instances.
    }

    /**
     * Reconciles the register with the loaded save and installs the recorder, each step behind its
     * own failure boundary.
     *
     * @param sector the loaded sector; null leaves the register untouched rather than throwing
     */
    public static void installAll(SectorAPI sector) {

        // Sightings of colonies the sector no longer holds are shed, and wherever the player is
        // sitting is recorded as seen. That second half is what keeps a save loaded inside a system
        // from having to be left and re-entered before its colonies are shown.
        runGuardedStep(
            () -> SectorColonySightings.reconcileWithLoadedSave(sector),
            "Failed to reconcile KMU colony sightings with the loaded save");

        runGuardedStep(
            () -> installColonySightingRecorder(sector),
            "Failed to install KMU colony sighting recorder");
    }

    // Registers the listener that records what the player sees as they travel, which is what the
    // map's revelation gates read to decide whether a derelict or a concealed base may be shown.
    //
    // Built fresh per load, because the recorder holds the sector it writes into: one kept from an
    // earlier load would go on writing to the sector it was built against while the loaded one
    // learned nothing. Registered transient, as everything here is, so it enters no save.
    static void installColonySightingRecorder(SectorAPI sector) {
        SectorListeners.installListener(
            sector,
            ColonySightingRecorder.class,
            () -> new ColonySightingRecorder(sector));
    }
}
