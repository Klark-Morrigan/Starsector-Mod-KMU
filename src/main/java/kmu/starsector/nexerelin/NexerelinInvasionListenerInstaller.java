package kmu.starsector.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.politicalmap.refresh.listeners.PoliticalMapMarketTransferListener;

/**
 * Registers KMU's political-map market-transfer listener only when Nexerelin is
 * present. The listener implements a Nexerelin interface, so a Nex-free install
 * must never load it: the sole reference to that Nex-coupled type lives in the
 * nested {@link Installer} holder, which the classloader does not resolve until
 * the mod-enabled gate has passed. That keeps an install without Nex from
 * seeking {@code exerelin.utilities.InvasionListener} and failing with a
 * missing-class error - the same isolation KMU uses for its optional Random
 * Assortment of Things integration.
 *
 * <p>Nexerelin is the only source of colony ownership transfers, which vanilla
 * fires no listener for, so this is the one place the political map learns that a
 * colony changed hands mid play.
 */
public final class NexerelinInvasionListenerInstaller {

    private static final String NEXERELIN_MOD_ID = "nexerelin";

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
        // loads the class that names the Nex InvasionListener.
        if (sector == null
                || !Global.getSettings().getModManager().isModEnabled(NEXERELIN_MOD_ID)) {
            return;
        }
        Installer.install(sector);
    }

    // Isolates the only reference to the Nex-coupled listener. The classloader
    // resolves this holder on first call, which the gate in installIfPresent
    // defers until Nex is known to be present, so InvasionListener is never
    // sought otherwise.
    private static final class Installer {

        private static void install(SectorAPI sector) {
            var listenerManager = sector.getListenerManager();
            if (listenerManager == null
                    || listenerManager.hasListenerOfClass(PoliticalMapMarketTransferListener.class)) {
                return;
            }
            // Registered transient (not persisted): the listener is a KMU class
            // implementing a Nex interface, so serialising it into the save would
            // fail to load if Nex were later removed. It is re-added on each load
            // instead, exactly when the gate above still passes.
            listenerManager.addListener(new PoliticalMapMarketTransferListener(), false);
        }
    }
}
