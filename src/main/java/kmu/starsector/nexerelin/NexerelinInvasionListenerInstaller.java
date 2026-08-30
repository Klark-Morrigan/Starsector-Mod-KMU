package kmu.starsector.nexerelin;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.mods.nexerelin.NexerelinPresence;

import kmu.maplayers.politicalmap.base.refresh.listeners.PoliticalMapMarketTransferListener;

/**
 * Registers KMU's political-map market-transfer listener only when Nexerelin is
 * present. The listener implements a Nexerelin interface, so a Nex-free install
 * must never load it: the sole reference to that Nex-coupled type lives in the
 * nested {@link Installer} holder, which the classloader does not resolve until
 * the mod-enabled gate has passed. That keeps an install without Nex from
 * seeking {@code exerelin.utilities.InvasionListener} and failing with a
 * missing-class error - the same isolation KMLib's Abyssal Fracture matcher uses
 * for Random Assortment of Things. An optional mod read through settings rather
 * than through its own types needs none of this: there is no class to defer, only
 * a mod-enabled gate in front of the read.
 *
 * <p>Nexerelin is the only source of colony ownership transfers, which vanilla
 * fires no listener for, so this is the one place the political map learns that a
 * colony changed hands mid play.
 */
public final class NexerelinInvasionListenerInstaller {

    private NexerelinInvasionListenerInstaller() {
    }

    /**
     * Adds the market-transfer listener when Nexerelin is enabled, and does
     * nothing otherwise; a null sector is ignored.
     *
     * @param sector the sector whose listener manager receives the listener
     */
    public static void installIfPresent(SectorAPI sector) {
        // Short-circuit before touching Installer so a Nex-free install never
        // loads the class that names the Nex InvasionListener. The gate is the
        // library's rather than a mod-manager hop of this class's own: the id
        // belongs to the mod, and an install being asked about before the game
        // has stood its settings up answers rather than throwing.
        if (sector == null || !NexerelinPresence.isModEnabled()) {
            return;
        }
        Installer.install(sector);
    }

    /**
     * Clears the market-transfer listener when Nexerelin is enabled, and does
     * nothing otherwise; a null sector is ignored.
     *
     * @param sector the sector whose listener manager is cleared
     */
    public static void uninstallIfPresent(SectorAPI sector) {
        // Gated exactly as the install is, and for the same reason: a Nex-free install must not
        // load the class that names the Nex InvasionListener, not even to remove it.
        if (sector == null || !NexerelinPresence.isModEnabled()) {
            return;
        }
        Installer.uninstall(sector);
    }

    // Isolates the only reference to the Nex-coupled listener. The classloader
    // resolves this holder on first call, which the gate in installIfPresent
    // defers until Nex is known to be present, so InvasionListener is never
    // sought otherwise.
    private static final class Installer {

        private static void install(SectorAPI sector) {

            var listenerManager = sector.getListenerManager();
            if (listenerManager == null) {
                return;
            }
            // Transient (true), not persisted (false): the listener is a KMU class
            // implementing a Nex interface, so serialising it into the save would fail to
            // load if Nex were later removed. It is re-added on each load instead, exactly
            // when the gate in installIfPresent still passes.
            //
            // Remove-then-add rather than a presence check, so a save that does carry a copy
            // is repaired by the load rather than left holding it beside the fresh one. A save
            // can only carry one if some build registered it persistently, which is exactly the
            // mistake the flag above exists to prevent - and the one shape that survives having
            // made it is this one.
            //
            // Built with the sector it is installed on, as its vanilla-driven siblings are: a
            // transfer marks that sector's own refresh board, and one reading the running game
            // instead would mark whichever sector the player has loaded for a conquest in another.
            listenerManager.removeListenerOfClass(PoliticalMapMarketTransferListener.class);
            listenerManager.addListener(new PoliticalMapMarketTransferListener(sector), true);
        }

        private static void uninstall(SectorAPI sector) {

            var listenerManager = sector.getListenerManager();
            if (listenerManager == null) {
                return;
            }
            listenerManager.removeListenerOfClass(PoliticalMapMarketTransferListener.class);
        }
    }
}
