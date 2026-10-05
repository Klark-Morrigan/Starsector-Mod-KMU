package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.mods.nexerelin.NexerelinPresence;
import kmlib.starsector.compatibility.ModIntegration;

import kmu.KmuWiringSteps;
import kmu.util.KmuCompatibilityConsumers;
import kmu.util.KmuStringKeys;

/**
 * Registers KMU's relay for Nexerelin's colony transfers only when Nexerelin is present. The relay
 * implements a Nexerelin interface, so an install without Nexerelin must never load it: the sole
 * reference to that coupled type lives in the nested {@link Installer} holder, which the
 * classloader does not resolve until the mod-enabled gate has passed. That keeps such an install
 * from seeking {@code exerelin.utilities.InvasionListener} and failing with a missing-class error -
 * the same isolation KMLib's Abyssal Fracture matcher uses for Random Assortment of Things. An
 * optional mod read through settings rather than through its own types needs none of this: there
 * is no class to defer, only a mod-enabled gate in front of the read.
 *
 * <p>Nexerelin is the only source of colony ownership transfers, which vanilla fires no listener
 * for. One relay per sector carries them to whatever listens for them, so what listens is
 * registered by its owner and names no Nexerelin type.
 */
public final class NexerelinInvasionListenerInstaller {

    // Which of KMU's features a failed relay costs, as the half of a latch key the mod ID does not
    // cover.
    private static final String COLONY_TRANSFERS_FEATURE_KEY = "nexerelin-colony-transfers";

    private NexerelinInvasionListenerInstaller() {
    }

    /**
     * Adds the transfer relay when Nexerelin is enabled, and does nothing otherwise; a null sector
     * is ignored.
     *
     * @param sector the sector whose listener manager receives the relay
     */
    public static void installIfPresent(SectorAPI sector) {
        // Short-circuit before touching Installer so an install without Nexerelin never loads the
        // class that names its InvasionListener. The gate is the library's rather than a mod-manager
        // hop of this class's own: the ID belongs to the mod, and an install being asked about
        // before the game has stood its settings up answers rather than throwing.
        if (sector == null || !NexerelinPresence.isModEnabled()) {
            return;
        }
        // Guarded here rather than by the caller, as every installer guards its own. The relay
        // implements a Nexerelin interface, so a release that moves or renames it fails this step
        // with a link error. Caught, that costs the relay alone - the political map's own poll still
        // repaints a conquest, seconds later - rather than the whole load.
        KmuWiringSteps.runGuardedStep(
            () -> Installer.install(sector),
            "Failed to install the KMU relay for Nexerelin's colony transfers",
            NexerelinInvasionListenerInstaller::describeColonyTransfersIntegration);
    }

    /**
     * Clears the transfer relay when Nexerelin is enabled, and does nothing otherwise; a null
     * sector is ignored.
     *
     * @param sector the sector whose listener manager is cleared
     */
    public static void uninstallIfPresent(SectorAPI sector) {
        // Gated exactly as the install is, and for the same reason: an install without Nexerelin
        // must not load the class that names its InvasionListener, not even to remove it.
        if (sector == null || !NexerelinPresence.isModEnabled()) {
            return;
        }
        // Guarded like the install: naming the relay's class to remove it links the same Nexerelin
        // interface, so a release that broke the install breaks this too. Under the same description,
        // the second failure joins the first report rather than raising another.
        KmuWiringSteps.runGuardedStep(
            () -> Installer.uninstall(sector),
            "Failed to remove the KMU relay for Nexerelin's colony transfers",
            NexerelinInvasionListenerInstaller::describeColonyTransfersIntegration);
    }

    /**
     * The relay as the compatibility channel states it: Nexerelin as the third party, and KMU as the
     * mod that loses something by it.
     *
     * <p>One description for the install and the removal, so a Nexerelin release that breaks both is
     * one report. Composed only once a step has failed, so the strings and the mod manager read
     * behind it stay off every load where the relay took.
     *
     * @return the integration a failed relay step is reported under
     */
    static ModIntegration describeColonyTransfersIntegration() {

        return new ModIntegration(
            NexerelinPresence.MOD_ID,
            NexerelinPresence.MOD_NAME,
            KmuCompatibilityConsumers.describeConsumer(
                COLONY_TRANSFERS_FEATURE_KEY,
                KmuStringKeys.COMPATIBILITY_LOST_NEXERELIN_COLONY_TRANSFERS,
                KmuStringKeys.COMPATIBILITY_UNAFFECTED_NEXERELIN_COLONY_TRANSFERS));
    }

    // Isolates the only reference to the Nexerelin-coupled relay. The classloader resolves this
    // holder on first call, which the gate in installIfPresent defers until Nexerelin is known to
    // be present, so InvasionListener is never sought otherwise.
    private static final class Installer {

        private static void install(SectorAPI sector) {

            var listenerManager = sector.getListenerManager();
            if (listenerManager == null) {
                return;
            }
            // Transient (true), not persisted (false): the relay is a KMU class implementing a
            // Nexerelin interface, so serialising it into the save would fail to load if Nexerelin
            // were later removed. It is re-added on each load instead, exactly when the gate in
            // installIfPresent still passes.
            //
            // Remove-then-add rather than a presence check, so a save that does carry a copy is
            // repaired by the load rather than left holding it beside the fresh one. A save can
            // only carry one if some build registered it persistently, which is exactly the
            // mistake the flag above exists to prevent - and the one shape that survives having
            // made it is this one.
            //
            // Built with the sector it is installed on: a transfer is relayed to that sector's own
            // listeners, and a relay reading the running game instead would tell whichever sector
            // the player has loaded about a conquest in another.
            listenerManager.removeListenerOfClass(NexerelinMarketTransferRelay.class);
            listenerManager.addListener(new NexerelinMarketTransferRelay(sector), true);
        }

        private static void uninstall(SectorAPI sector) {

            var listenerManager = sector.getListenerManager();
            if (listenerManager == null) {
                return;
            }
            listenerManager.removeListenerOfClass(NexerelinMarketTransferRelay.class);
        }
    }
}
