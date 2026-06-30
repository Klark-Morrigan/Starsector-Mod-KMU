package kmu.politicalmap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.listeners.DiscoverEntityListener;

import org.apache.log4j.Logger;

/**
 * Refreshes the political map's drawables when the player discovers a market, so
 * a concealed colony or station (e.g. Knights of Ludd's Battlestar Libra) paints
 * its system the moment it is found, not only on reload.
 *
 * <p>Only a discovered market matters here: it changes who is visible over the
 * existing geometry, a drawables-only restyle. Discoveries that could change
 * which systems are reachable (a jump point, a gate) are not handled here -
 * gate activation in particular is a separate, later step from discovery - so
 * that accessibility is left to {@link PoliticalMapAccessWatcher}, the single
 * place that judges it.
 */
public class PoliticalMapDiscoveryListener implements DiscoverEntityListener {
    private static final Logger LOG = Global.getLogger(PoliticalMapDiscoveryListener.class);

    @Override
    public void reportEntityDiscovered(SectorEntityToken entity) {
        if (entity == null || entity.getMarket() == null) {
            return;
        }
        // A discovered market restyles the existing geometry. Logged with the
        // entity and market id so a colony that does (or does not) paint on
        // discovery can be traced to this event.
        LOG.debug("Political map content refresh on discovered market; entity="
                + entity.getId() + " market=" + entity.getMarket().getId());
        PoliticalMapRefresh.requestContentRefresh();
    }
}
