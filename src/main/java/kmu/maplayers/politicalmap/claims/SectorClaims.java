package kmu.maplayers.politicalmap.claims;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReader;

import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SectorBlocPalettes;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Resolves which faction lays claim to each star system and in which colours that claimant
 * paints - the claim counterpart to the dominance package's {@code SectorPolitics} held-dominance
 * resolve.
 *
 * <p>Where {@code SectorPolitics} reads who <em>holds</em> a system from the live economy,
 * this reads who <em>claims</em> it through the {@link ClaimReader} port, then routes the
 * claimant through the same grouping fold and palette lookup the held pass uses. Reusing
 * {@link SectorBlocPalettes#resolveOwnerOf} gives a claim the identical alliance rollup and
 * authored-palette resolve a held holder gets, so a claimed system and a held one of the same
 * bloc resolve to an equal {@link SystemOwner} - and therefore share a holder and
 * fuse into one territory downstream.
 *
 * <p>The claimant comes from the port, not the {@code Misc} static behind it, so the resolve
 * runs against a known set of claimants with no running game.
 *
 * <p>The sector walked is the pass's, which is also the sector the reader behind the port was
 * opened over. One naming of it rather than two is what keeps this from walking the systems of
 * one sector while the reader prices them against another.
 */
public final class SectorClaims {

    private SectorClaims() {
    }

    /**
     * Builds the claiming holder - claimant bloc ID paired with the shades it paints in - for
     * every claimed star system, under a holder grouping.
     *
     * <p>Each system's claimant faction ID is folded to its bloc under the grouping and
     * coloured through {@link SectorBlocPalettes#resolveOwnerOf}, so a claim carries the same
     * bloc key and palette a held system of that bloc would. A system with no claim is absent;
     * a claim whose colour faction does not resolve is dropped, exactly as an unresolved held
     * holder is, so it does not paint a colourless cluster.
     *
     * @param pass        the rebuild's reading of the sector - the systems walked, and the
     *                    grouping that collapses the claimant faction into its bloc before the
     *                    palette is resolved (identity for the faction view, alliance blocs for
     *                    the alliances view); a pass over no sector yields an empty map
     * @param claimReader the claim source, read once per system
     * @return the claiming holder keyed by {@link SystemKey}, in star-system walk order; a system
     *         with no resolvable claim is absent from the map
     */
    public static Map<SystemKey, SystemOwner> resolveClaimingHolderBySystemKey(
            HolderPass pass,
            ClaimReader claimReader) {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        for (var system : pass.readSystems()) {

            var holder = resolveClaimingHolderIn(system, pass, claimReader);

            if (holder != null) {
                ownerBySystemKey.put(SystemKey.readKeyOf(system), holder);
            }
        }
        return ownerBySystemKey;
    }

    /**
     * The claiming holder of one star system - the single-system arm of
     * {@link #resolveClaimingHolderBySystemKey}, for a batch re-deriving a marked system.
     *
     * <p>Answered here rather than at that caller, so both arms fold a claimant into its bloc and
     * colour it by one rule: a per-system read stating its own would land a claim on a bloc the
     * sector-wide resolve never painted it as.
     *
     * @param system      the system to read; null yields null
     * @param pass        the reading of the sector, whose grouping folds the claimant into its bloc
     *                    and whose palettes colour that bloc
     * @param claimReader the claim source, opened over that same pass
     * @return the claiming holder, or null where nobody claims the system or the claimant's colour
     *         faction does not resolve
     */
    public static SystemOwner resolveClaimingHolderIn(
            StarSystemAPI system,
            HolderPass pass,
            ClaimReader claimReader) {

        if (system == null) {
            return null;
        }

        return readClaimantBlocId(claimReader.readClaimingFactionId(system), pass.grouping())
            .map(pass.palettes()::resolveOwnerOf)
            .orElse(null);
    }

    /**
     * The bloc a claim lands on: the claimant faction folded to its bloc under a grouping.
     *
     * <p>Stated once for every reader of a claim - the fill, the picker's stats and the band - so a
     * claim cannot be credited to one bloc in the fill and to another in the stats or the band.
     *
     * @param claimantFactionId the faction holding the claim; null where nobody claims the system
     * @param grouping          the grouping the claimant is folded under
     * @return the claimant's bloc, or empty where nobody claims the system
     */
    public static Optional<String> readClaimantBlocId(String claimantFactionId, HolderGrouping grouping) {

        return Optional
            .ofNullable(claimantFactionId)
            .map(grouping::resolveBlocId);
    }
}
