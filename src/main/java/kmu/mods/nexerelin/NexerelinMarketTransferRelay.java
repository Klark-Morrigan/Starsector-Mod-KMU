package kmu.mods.nexerelin;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;

import kmu.starsector.listeners.MarketTransferListener;

import org.apache.log4j.Logger;

import java.util.List;

import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;

/**
 * Passes Nexerelin's colony transfers on to every {@link MarketTransferListener} registered on the
 * sector.
 *
 * <p>Nexerelin is the only source of such transfers, and it reports them through its own
 * {@link InvasionListener}. One relay speaks that interface so nothing else has to: each listener
 * is written against KMU's own contract and registered by whoever owns it, and this asks the
 * sector's listener manager for them at the moment a transfer lands, so a listener added or removed
 * mid-campaign is reached or dropped with no word to this class.
 *
 * <p>Loaded only past a mod-enabled gate - {@link NexerelinInvasionListenerInstaller} defers every
 * reference to it - so an install without Nexerelin never resolves {@link InvasionListener}.
 *
 * <p>Holds the sector it was installed on, so a conquest is relayed to that sector's listeners
 * rather than to whichever sector is currently loaded.
 *
 * <p>Each listener is told behind a boundary of its own. Nexerelin tells its listeners in a bare
 * loop at the end of its market transfer, from inside an invasion, a rebellion or a transfer dialog
 * that has no catch of its own, so a throw escaping here would reach the engine and end the game
 * with a stack through Nexerelin - for a fault that is KMU's. What a listener here does is a fast
 * path the map's own poll covers within seconds, so a throw costs nothing worth telling the player:
 * it is logged with its trace, the listeners after it are still told, and it is told again on the
 * next transfer.
 */
public class NexerelinMarketTransferRelay implements InvasionListener {

    private static final Logger LOG = Global.getLogger(NexerelinMarketTransferRelay.class);

    private final SectorAPI sector;

    /**
     * @param sector the sector this relay is installed on, whose listeners a transfer here reaches
     */
    public NexerelinMarketTransferRelay(SectorAPI sector) {
        this.sector = sector;
    }

    // Named to match Nexerelin's interface, which misspells "transferred" with a single r; the
    // override must reproduce that exact signature.
    @Override
    public void reportMarketTransfered(
            MarketAPI market,
            FactionAPI newHolder,
            FactionAPI oldHolder,
            boolean playerInvolved,
            boolean isCapture,
            List<String> factionsToNotify,
            float repChangeStrength) {

        // Asked per transfer rather than held, so the set of listeners is whatever is registered as
        // the transfer lands: a layer stood down mid-campaign has taken its listener back and is not
        // reached.
        var listenerManager = sector.getListenerManager();

        if (listenerManager == null) {
            return;
        }
        for (var listener : listenerManager.getListeners(MarketTransferListener.class)) {
            // Caught per listener rather than around the loop, so one listener failing costs only
            // what it would have done and the listeners after it are still told. Logged with the
            // trace each time: transfers are rare events, not a per-frame read, so there is nothing
            // to throttle.
            try {
                listener.reportMarketTransferred(market, oldHolder, newHolder, isCapture);

            } catch (LinkageError | RuntimeException listenerFailure) {

                LOG.error(
                    "A colony changing hands was not passed on to " + listener.getClass().getName(),
                    listenerFailure);
            }
        }
    }

    // The loot handed over on a successful invasion changes no holder; the transfer itself is
    // reported on its own, so there is nothing to relay.
    @Override
    public void reportInvadeLoot(
        InteractionDialogAPI dialog,
        MarketAPI market,
        Nex_MarketCMD.TempDataInvasion actionData,
        CargoAPI cargo) {
    }

    // Per-round strength during an invasion, before any holder has changed.
    @Override
    public void reportInvasionRound(
        InvasionRound.InvasionRoundResult result,
        CampaignFleetAPI fleet,
        MarketAPI defender,
        float attackerStrength,
        float defenderStrength) {
    }

    // An invasion finishing transfers nothing by itself - a failed one changes no holder, and a
    // successful one is reported through the transfer above.
    @Override
    public void reportInvasionFinished(
        CampaignFleetAPI fleet,
        FactionAPI attackerFaction,
        MarketAPI market,
        float numRounds,
        boolean success) {
    }
}
