package kmu.maplayers.politicalmap.base.politics.holders;

import kmlib.starsector.systems.claims.ClaimReaderSource;
import kmlib.starsector.systems.claims.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredClaims;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The holding source the faction and alliance views resolve under: held dominance from a
 * base provider, with each claimed-but-unheld system folded in as an unfilled extension of
 * its claimant's territory.
 *
 * <p>Wraps the held-dominance {@link DefaultHolderProvider} rather than replacing it, so
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
public final class ClaimAugmentedHolderProvider implements HolderProvider {

    /**
     * The shared instance: the held-dominance default extended with vanilla claims. Stateless
     * once built - the base provider is stateless, and what stands in for the claim reader is
     * the means of opening one rather than a reader - so every pass reuses it, and it is the
     * default {@code PoliticalMapView} resolves holding through.
     */
    public static final ClaimAugmentedHolderProvider INSTANCE =
        new ClaimAugmentedHolderProvider(
            DefaultHolderProvider.INSTANCE,
            VanillaClaimReader::new);

    private final HolderProvider heldHolderProvider;
    private final ClaimReaderSource claimReaderSource;

    ClaimAugmentedHolderProvider(
            HolderProvider heldHolderProvider,
            ClaimReaderSource claimReaderSource) {
        this.heldHolderProvider = heldHolderProvider;
        this.claimReaderSource = claimReaderSource;
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {

        var held = heldHolderProvider.resolveHolder(pass, selectedBlocId);

        // The claims arrive already under whatever spotlight is active, so the fold only has to
        // answer which claimed systems join the holder map, never which key each one carries.
        // The reader is opened over this pass rather than held across passes, so it reads the
        // colonies the held half just read rather than walking every system a second time.
        var claimingHolderBySystemId = FilteredClaims.resolveFilteredClaims(
            pass,
            claimReaderSource.openReaderOver(pass.colonies()),
            selectedBlocId);

        return foldClaimsIntoHeld(held, claimingHolderBySystemId);
    }

    // Adds each claimed system the held resolve left unowned to the holder map - so held and claimed
    // cells of one bloc fuse into a single territory - and records it unfilled, so the fill split
    // leaves it empty inside that shared border. A system already held keeps its held holder and
    // solid fill: the held signal is the stronger one. Pure over its inputs; the claims arrive
    // already keyed for whatever spotlight is active, so the fold reads no filter of its own.
    private static HolderResolution foldClaimsIntoHeld(
            HolderResolution held,
            Map<String, DominantHolder> claimingHolderBySystemId) {

        var ownerBySystemId = new LinkedHashMap<>(held.ownerBySystemId());
        var unfilledSystemIds = new LinkedHashSet<>(held.unfilledSystemIds());

        for (var claim : claimingHolderBySystemId.entrySet()) {
            if (ownerBySystemId.containsKey(claim.getKey())) {
                continue;
            }
            ownerBySystemId.put(claim.getKey(), claim.getValue());
            unfilledSystemIds.add(claim.getKey());
        }
        return new HolderResolution(
            ownerBySystemId,
            held.contestedSystemIds(),
            unfilledSystemIds);
    }
}
