package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Puts a claim resolve under the spotlight: the selected bloc's claims take its one synthetic
 * spotlight key, every rival's claim keeps its own.
 *
 * <p>Both claim-reading providers owe their claims the same treatment - a claim shares the fate of
 * the territory it belongs to, whether claims are the whole layer or an unfilled extension of held
 * dominance - so the rule lives here rather than once per provider, where the two could drift into
 * spotlighting the same claim differently on two views of the same sector.
 */
final class ClaimSpotlight {

    private ClaimSpotlight() {
    }

    /**
     * Rekeys the selected bloc's claims onto its spotlight holder, leaving every other claim
     * untouched.
     *
     * <p>A rekeyed claim fuses into the bloc's single bordered spotlight territory and draws at
     * full strength; a claim left on its plain bloc key is what the style layer reads to mute and
     * desaturate it, exactly as it recedes that bloc's held cells. Off filter nothing is rekeyed,
     * so the claim resolve passes straight through.
     *
     * @param sector                    the sector whose faction palette the spotlight holder is
     *                                  read from; null rekeys nothing
     * @param grouping                  the grouping naming the selected bloc's colour faction
     * @param claimingHolderBySystemId  the claim resolve to put under the spotlight
     * @param selectedBlocId            the spotlighted bloc's id, or null off filter
     * @return the claim resolve with the selected bloc's systems rekeyed; the input map itself
     *         when there is nothing to rekey
     */
    static Map<String, DominantHolder> rekeyClaimsOntoSpotlight(
            SectorAPI sector,
            HolderGrouping grouping,
            Map<String, DominantHolder> claimingHolderBySystemId,
            String selectedBlocId) {

        if (sector == null || selectedBlocId == null) {
            return claimingHolderBySystemId;
        }

        // Resolved once for the whole walk: every system the bloc claims joins the same spotlight
        // territory, so they all carry the identical holder.
        var spotlitHolder = FilteredPolitics.resolveSpotlitHolder(sector, grouping, selectedBlocId);
        var rekeyed = new LinkedHashMap<String, DominantHolder>();

        for (var claim : claimingHolderBySystemId.entrySet()) {
            var holder = selectedBlocId.equals(claim.getValue().factionId())
                ? spotlitHolder
                : claim.getValue();

            // A null spotlight holder only happens in the degenerate case where the selected bloc's
            // colour faction vanished mid-session; its claims drop rather than paint colourless,
            // matching how the presence pass drops a spotlit system whose palette will not resolve.
            if (holder != null) {
                rekeyed.put(claim.getKey(), holder);
            }
        }
        return rekeyed;
    }
}
