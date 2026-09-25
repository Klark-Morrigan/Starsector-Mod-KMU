package kmu.mods.nexerelin;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;

import kmu.starsector.listeners.MarketTransferListener;

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
 */
public class NexerelinMarketTransferRelay implements InvasionListener {

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
            listener.reportMarketTransferred(market, oldHolder, newHolder, isCapture);
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
