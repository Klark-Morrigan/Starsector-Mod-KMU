package kmu.maplayers.politicalmap.base.politics;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderPass;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resolves who paints each claimed star system while the filter spotlights one bloc: the selected
 * bloc's claims carry its one synthetic spotlight key, every rival's claim keeps its own.
 *
 * <p>The claim counterpart to {@link FilteredPolitics}, standing to {@link SectorClaims} exactly as
 * that class stands to {@link SectorPolitics} - the same resolve, answered under a spotlight. The
 * key is what the style layer reads: a rekeyed claim draws at full strength, a claim left on its
 * plain bloc key mutes and desaturates, so a claim always shares the fate of the territory it
 * belongs to.
 *
 * <p>How much the rekey moves depends on the caller. Where claims extend held dominance it is a
 * true fusion - the bloc's spotlight footprint spans systems it does not hold, which its claims
 * could never join under their own bloc key. Where claims are the whole layer the claimant's
 * systems already share a key, so the swap regroups nothing and buys only the spotlit answer.
 *
 * <p>Both claim-reading holder providers resolve through this, whether claims are the whole layer
 * or an unfilled extension of held dominance, so the two cannot drift into spotlighting the same
 * claim differently on two views of one sector.
 *
 * <p>Unlike the held resolve there is no contested set to report: a system has exactly one
 * claimant, so there is no present-but-dominated state a claim can be in.
 */
public final class FilteredClaims {

    private FilteredClaims() {
    }

    /**
     * Builds the claiming holder per system with the selected bloc's claims put under its
     * spotlight - the entry the claim-reading providers call in place of
     * {@link SectorClaims#resolveClaimingHolderBySystemKey}.
     *
     * <p>Off filter, or with no sector to read a palette from, this is that plain claim resolve
     * unchanged. A claim of the selected bloc whose spotlight holder does not resolve is dropped,
     * matching how the presence pass drops a spotlit system whose colour faction has gone.
     *
     * @param pass           the rebuild's reading of the sector - the systems walked, and the
     *                       grouping that folds a claimant into its bloc before the palette is
     *                       resolved and that names the selected bloc's colour faction; a pass
     *                       over no sector yields an empty map
     * @param claimReader    the claim source, read once per system
     * @param selectedBlocId the spotlighted bloc's id, or null off filter
     * @return the claiming holder keyed by {@link SystemKey}, in star-system walk order
     */
    public static Map<SystemKey, DominantHolder> resolveFilteredClaims(
            HolderPass pass,
            ClaimReader claimReader,
            String selectedBlocId) {

        var claimingHolderBySystemKey = SectorClaims.resolveClaimingHolderBySystemKey(
            pass,
            claimReader);
        var sector = pass.sector();

        if (sector == null || selectedBlocId == null) {
            return claimingHolderBySystemKey;
        }

        // Resolved once for the whole walk: every system the bloc claims joins the same spotlight
        // territory, so they all carry the identical holder.
        var spotlitHolder = FilteredPolitics.resolveSpotlitHolder(
            sector,
            pass.grouping(),
            selectedBlocId);
        var spotlitClaims = new LinkedHashMap<SystemKey, DominantHolder>();

        for (var claim : claimingHolderBySystemKey.entrySet()) {
            var holder = selectedBlocId.equals(claim.getValue().factionId())
                ? spotlitHolder
                : claim.getValue();

            // A null spotlight holder only happens in the degenerate case where the selected bloc's
            // colour faction vanished mid-session; its claims drop rather than paint colourless.
            if (holder != null) {
                spotlitClaims.put(claim.getKey(), holder);
            }
        }
        return spotlitClaims;
    }
}
