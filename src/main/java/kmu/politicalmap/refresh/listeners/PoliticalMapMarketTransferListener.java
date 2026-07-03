package kmu.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.CargoAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.InteractionDialogAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.Nex_MarketCMD;

import kmu.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.politicalmap.refresh.PoliticalMapRefresh;
import kmu.politicalmap.refresh.PoliticalMapSectorWatcher;

import java.util.List;

import exerelin.campaign.InvasionRound;
import exerelin.utilities.InvasionListener;

/**
 * Marks a conquered colony's system politics-stale when Nexerelin transfers its
 * ownership, so a colony that changes hands in a Nex invasion or war repaints its
 * new faction color live rather than only on reload.
 *
 * <p>A market changing owner is the central political-map event - it is how war
 * redraws borders - but vanilla fires no ownership-transfer listener, so the
 * sibling listeners cannot catch it: an invasion keeps the colony's size, never
 * decivilises it, and reveals nothing. Nexerelin is the only source of such
 * transfers and it does report them through {@link InvasionListener}, so this
 * translates that single Nex event into the same targeted refresh
 * ({@link PoliticalMapRefresh#markSystemPoliticsStale}) the ownership-axis siblings
 * use: only the transferred colony's system and its neighbours are re-derived,
 * reading the post-transfer owner.
 *
 * <p>This is a Nexerelin-only integration. It implements a Nex interface, so the
 * class is loaded only after a mod-enabled gate has confirmed Nex is present -
 * {@code kmu.starsector.nexerelin.NexerelinInvasionListenerInstaller} defers the
 * reference behind that gate, keeping a Nex-free install from ever resolving
 * {@link InvasionListener}. Only {@code reportMarketTransferred} touches the map;
 * the other invasion callbacks (loot, per-round strength, invasion finished) do
 * not change owner and are left as no-ops. Reachability changes remain
 * {@link PoliticalMapSectorWatcher}'s axis.
 */
public class PoliticalMapMarketTransferListener implements InvasionListener {

    // Named to match Nexerelin's interface, which misspells "transferred" with a
    // single r; the override must reproduce that exact signature.
    @Override
    public void reportMarketTransfered(MarketAPI market, FactionAPI newOwner, FactionAPI oldOwner,
            boolean playerInvolved, boolean isCapture, List<String> factionsToNotify,
            float repChangeStrength) {
        // The from/to factions and capture flag ride along in the log so a cell
        // that does (or does not) repaint on conquest can be traced to this
        // transfer; the shared refresh filters an unseated market.
        MarketPoliticsRefresh.markSystemStaleForMarket(market, "market transferred",
                "from=" + factionId(oldOwner) + " to=" + factionId(newOwner)
                        + " capture=" + isCapture);
    }

    // The loot handed over on a successful invasion does not change the colony's
    // owner; the transfer itself is reported separately, so this is a no-op here.
    @Override
    public void reportInvadeLoot(InteractionDialogAPI dialog, MarketAPI market,
            Nex_MarketCMD.TempDataInvasion actionData, CargoAPI cargo) {
    }

    // Per-round attacker/defender strength during an invasion; ownership has not
    // changed yet, so nothing to refresh.
    @Override
    public void reportInvasionRound(InvasionRound.InvasionRoundResult result, CampaignFleetAPI fleet,
            MarketAPI defender, float attackerStrength, float defenderStrength) {
    }

    // An invasion finishing does not by itself transfer the market - a failed
    // invasion changes no owner, and a successful one is reported through
    // reportMarketTransferred - so the refresh keys off the transfer, not this.
    @Override
    public void reportInvasionFinished(CampaignFleetAPI fleet, FactionAPI attackerFaction,
            MarketAPI market, float numRounds, boolean success) {
    }

    // Null-safe faction id for the trace line; a transfer to or from an unowned
    // state is logged as "null" rather than crashing the callback.
    private static String factionId(FactionAPI faction) {
        return faction == null ? "null" : faction.getId();
    }
}
