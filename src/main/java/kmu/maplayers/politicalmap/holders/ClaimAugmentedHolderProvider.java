package kmu.maplayers.politicalmap.holders;

import kmlib.starsector.systems.SystemKey;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderProvider;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.claims.FilteredClaims;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The holding source the faction and alliance views resolve under: held dominance from a
 * base provider, with each claimed-but-unheld system folded in as an unfilled extension of
 * its claimant's territory.
 *
 * <p>Wraps the held-dominance {@link DominanceHolderProvider} rather than replacing it, so
 * the held resolve stays one concern and claims layer on top as a second. A system a bloc
 * claims but does not hold joins that bloc's holder map under the same key its held systems
 * carry - so the agnostic geometry fuses claimed and held cells into one bordered territory -
 * and is marked unfilled, so the fill split paints the held systems solid and leaves the
 * claimed ones empty inside that one frontier. On the alliances view the claimant folds to its
 * alliance bloc through the grouping exactly as a held system does, so an allied claimant's
 * claims roll into the alliance automatically.
 *
 * <p>Under a spotlight claims recede with the map, not against it. The spotlighted bloc's own
 * claims take its spotlight holder, so they fuse into its one territory and draw at full strength -
 * unfilled - beside its solid and hatched held systems; every other bloc's claims keep their plain
 * bloc holder, which the style layer mutes into the receded background exactly as it mutes that
 * bloc's held cells. So a claim always shares the fate of the territory it belongs to.
 *
 * <p>Both halves answer off the one pass, which is what makes this the cheaper of the two
 * arrangements rather than the dearer one: the held resolve and the claim resolve each read every
 * system, and sharing the pass's walk means the sector is traversed once between them instead of
 * once apiece. It also means both halves describe the sector as it stood at one moment, so the
 * fill and the claim extending it into the same territory cannot disagree about what is there.
 */
public final class ClaimAugmentedHolderProvider
        implements HolderProvider {

    private final HolderProvider heldHolderProvider;
    private final ClaimReaderSource claimReaderSource;

    ClaimAugmentedHolderProvider(
            HolderProvider heldHolderProvider,
            ClaimReaderSource claimReaderSource) {

        this.heldHolderProvider = heldHolderProvider;
        this.claimReaderSource = claimReaderSource;
    }

    /**
     * The held-dominance default extended with the claims a view reads. Stateless once built - the
     * base provider is stateless, and what stands in for the claim reader is the means of opening
     * one rather than a reader - so every pass reuses one.
     *
     * @param claimReaderSource the source the view reads every claim through
     * @return the provider folding those claims onto held dominance
     */
    public static ClaimAugmentedHolderProvider createReadingThrough(ClaimReaderSource claimReaderSource) {
        return new ClaimAugmentedHolderProvider(DominanceHolderProvider.INSTANCE, claimReaderSource);
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {

        var held = heldHolderProvider.resolveHolder(pass, selectedBlocId);

        // The claims arrive already under whatever spotlight is active, so the fold only has to
        // answer which claimed systems join the holder map, never which key each one carries.
        var claimingHolderBySystemKey = FilteredClaims.resolveFilteredClaimsThrough(
            pass,
            claimReaderSource,
            selectedBlocId);

        return foldClaimsIntoHeld(held, claimingHolderBySystemKey);
    }

    /**
     * One system's held answer extended by its claim: the rule both halves of this view fold claims
     * by, the whole-sector one here and the per-system one in
     * {@link ClaimAugmentedSystemHolderResolve}.
     *
     * <p>A held system keeps its held holder and fill: the held signal is the stronger one. A claimed
     * system the held resolve left unowned joins its claimant - so held and claimed cells of one bloc
     * fuse into a single territory - unfilled, so the fill split leaves it empty inside that shared
     * border. Pure over its inputs; the claim arrives already keyed for whatever spotlight is active,
     * so the fold reads no filter of its own.
     *
     * @param held           the system's held holder and fill state
     * @param claimingHolder the system's claimant, or null where nobody claims it
     * @return what the system draws as
     */
    static ResolvedSystemOwner extendHeldByClaim(ResolvedSystemOwner held, SystemOwner claimingHolder) {

        if (held.owner() != null || claimingHolder == null) {
            return held;
        }
        return new ResolvedSystemOwner(claimingHolder, FillState.UNFILLED);
    }

    // Extends every system the held resolve answered by its claim, under the one rule above.
    private static HolderResolution foldClaimsIntoHeld(
            HolderResolution held,
            Map<SystemKey, SystemOwner> claimingHolderBySystemKey) {

        var ownerBySystemKey = new LinkedHashMap<>(held.ownerBySystemKey());
        var fillStateBySystemKey = new LinkedHashMap<>(held.fillStates().getNonSolidFillStateBySystemKey());

        for (var claim : claimingHolderBySystemKey.entrySet()) {

            var systemKey = claim.getKey();
            var extended = extendHeldByClaim(readHeldAnswerOf(held, systemKey), claim.getValue());

            ownerBySystemKey.put(systemKey, extended.owner());
            fillStateBySystemKey.put(systemKey, extended.fillState());
        }

        return new HolderResolution(
            ownerBySystemKey,
            SystemFillStates.createOf(fillStateBySystemKey));
    }

    // One claimed system's held answer, read back out of the held resolution so the claim is folded
    // by the per-system rule.
    private static ResolvedSystemOwner readHeldAnswerOf(HolderResolution held, SystemKey systemKey) {

        var owner = held
            .ownerBySystemKey()
            .get(systemKey);

        return owner == null
            ? ResolvedSystemOwner.UNOWNED
            : new ResolvedSystemOwner(owner, held.fillStates().resolveFillStateOf(systemKey));
    }
}
