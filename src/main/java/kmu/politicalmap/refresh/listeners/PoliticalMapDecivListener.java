package kmu.politicalmap.refresh.listeners;

import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.campaign.listeners.ColonyDecivListener;

import kmu.politicalmap.refresh.MarketPoliticsRefresh;
import kmu.politicalmap.refresh.PoliticalMapAccessWatcher;
import kmu.politicalmap.refresh.PoliticalMapRefresh;

/**
 * Marks a decivilised colony's system politics-stale, so a colony that dies mid
 * play drops its faction color and repaints neutral at once rather than only on
 * reload.
 *
 * <p>Decivilisation strips the owning faction - the engine turns the market
 * neutral and removes it from the economy - so the system's dominant owner
 * vanishes, the same per-system ownership change a colony resize or a discovery
 * makes. It therefore routes through the same targeted refresh
 * ({@link PoliticalMapRefresh#markSystemPoliticsStale}): only that system and its
 * neighbours are re-derived and re-shaped, the re-derivation reading the now
 * ownerless economy and dropping the faction fill.
 *
 * <p>Decivilisation can also change whether the system stays on the map at all,
 * a visibility-set change owned by {@link PoliticalMapAccessWatcher} rather than
 * this listener. A deciv that leaves a revealed ruin on a planet keeps the system
 * on the map as a neutral dead colony; one that leaves none drops a system nothing
 * else keeps on the map. A partial deciv (fullyDestroyed false) leaves that ruin,
 * a full destroy does not, and a station never leaves one (the revealed-ruin rule
 * scans planets only) - so a full destroy, or any station deciv, can drop a hidden
 * single-colony system. The watcher is the single authority on map membership: its
 * fingerprint moves on both a draw-class flip and a set-membership change, so it
 * reconciles the geometry on its next poll. This listener stays on the ownership
 * axis alone, mirroring {@link PoliticalMapDiscoveryListener}.
 *
 * <p>Reacts to {@code reportColonyDecivilized} (fired after the engine has turned
 * the market neutral and pulled it from the economy) so the re-derivation reads
 * the post-deciv state; the {@code aboutToBe} phase would still see the colony
 * faction-owned, so it is a no-op here.
 */
public class PoliticalMapDecivListener implements ColonyDecivListener {

    // The colony is still faction-owned at this point, so re-deriving now would
    // read the pre-deciv owner. The stale mark is deferred to the completed
    // event below, which sees the neutral, economy-removed market.
    @Override
    public void reportColonyAboutToBeDecivilized(MarketAPI market, boolean fullyDestroyed) {
    }

    @Override
    public void reportColonyDecivilized(MarketAPI market, boolean fullyDestroyed) {
        // The completed event sees the neutral, economy-removed market, and the
        // system still resolves post-removal since the primary entity (the planet)
        // is preserved. The full-destroy flag rides along in the log so a cell that
        // does (or does not) repaint neutral on death can be traced to this event.
        MarketPoliticsRefresh.markSystemStaleForMarket(market, "decivilised colony",
                "fullyDestroyed=" + fullyDestroyed);
    }
}
