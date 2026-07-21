package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.ClaimReader;
import kmlib.starsector.systems.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;

/**
 * The ownership source the faction and alliance views resolve under: held dominance from a
 * base provider, with each claimed-but-unheld system folded in as an unfilled extension of
 * its claimant's territory.
 *
 * <p>Wraps the held-dominance {@link DefaultOwnershipProvider} rather than replacing it, so
 * the held resolve stays one concern and claims layer on top as a second. A system a bloc
 * claims but does not hold joins that bloc's owner map under the same key its held systems
 * carry - so the agnostic geometry fuses claimed and held ground into one bordered territory -
 * and is marked unfilled, so the fill split paints the held systems solid and leaves the
 * claimed ones empty inside that one frontier. On the alliances view the claimant folds to its
 * alliance bloc through the grouping exactly as a held system does, so an allied claimant's
 * claims roll into the alliance automatically.
 *
 * <p>Under a spotlight claims recede with the map, not against it. The spotlighted bloc's own
 * claims take its spotlight owner, so they fuse into its one territory and draw at full strength -
 * unfilled - beside its solid and hatched held systems; every other bloc's claims keep their plain
 * bloc owner, which the style layer mutes into the receded background exactly as it mutes that
 * bloc's held ground. So a claim always shares the fate of the territory it belongs to.
 */
public final class ClaimAugmentedOwnershipProvider implements OwnershipProvider {

    /**
     * The shared instance: the held-dominance default extended with vanilla claims. Stateless
     * once built - the base provider and the claim reader it wraps are both stateless - so every
     * pass reuses it, and it is the default {@code PoliticalMapView} resolves ownership through.
     */
    public static final ClaimAugmentedOwnershipProvider INSTANCE =
            new ClaimAugmentedOwnershipProvider(
                    DefaultOwnershipProvider.INSTANCE, new VanillaClaimReader());

    private final OwnershipProvider heldOwnershipProvider;
    private final ClaimReader claimReader;

    ClaimAugmentedOwnershipProvider(
            OwnershipProvider heldOwnershipProvider, ClaimReader claimReader) {
        this.heldOwnershipProvider = heldOwnershipProvider;
        this.claimReader = claimReader;
    }

    @Override
    public OwnershipResolution resolveOwnership(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId) {
        var held = heldOwnershipProvider.resolveOwnership(sector, grouping, selectedBlocId);
        var claimingOwnerBySystemId =
                SectorClaims.resolveClaimingOwnerBySystemId(sector, grouping, claimReader);
        // The spotlighted bloc's own claims fuse into its spotlight territory rather than their
        // plain bloc colour, so its spotlight owner is resolved once here and shared across every
        // system it claims - null off filter, or when there is no sector to read a palette from.
        var spotlitClaimOwner = selectedBlocId == null || sector == null
                ? null
                : FilteredPolitics.resolveSpotlitOwner(sector, grouping, selectedBlocId);
        return foldClaimsIntoHeld(held, claimingOwnerBySystemId, selectedBlocId, spotlitClaimOwner);
    }

    // Adds each claimed system the held resolve left unowned to the owner map - so held and claimed
    // ground of one bloc fuse into a single territory - and records it unfilled, so the fill split
    // leaves it empty inside that shared border. A system already held keeps its held owner and
    // solid fill: the held signal is the stronger one. Pure over its inputs; the spotlight owner it
    // rekeys the selected bloc's own claims onto is resolved by the caller.
    private static OwnershipResolution foldClaimsIntoHeld(
            OwnershipResolution held,
            Map<String, DominantOwner> claimingOwnerBySystemId,
            String selectedBlocId,
            DominantOwner spotlitClaimOwner) {
        var ownerBySystemId = new LinkedHashMap<>(held.ownerBySystemId());
        var unfilledSystemIds = new LinkedHashSet<>(held.unfilledSystemIds());
        for (var claim : claimingOwnerBySystemId.entrySet()) {
            if (ownerBySystemId.containsKey(claim.getKey())) {
                continue;
            }
            var owner = resolveClaimOwner(claim.getValue(), selectedBlocId, spotlitClaimOwner);
            if (owner == null) {
                continue;
            }
            ownerBySystemId.put(claim.getKey(), owner);
            unfilledSystemIds.add(claim.getKey());
        }
        return new OwnershipResolution(
                ownerBySystemId, held.contestedSystemIds(), unfilledSystemIds);
    }

    // The owner a claimed system draws under. Off filter, or for any bloc other than the
    // spotlighted one, that is the claim's plain bloc owner - full colour off filter, and receded
    // by the style layer under a filter because its key is not the spotlight's. The spotlighted
    // bloc's own claims instead take its spotlight owner, fusing into its one territory at full
    // strength; a null spotlight owner (its palette gone) drops the claim, as the presence pass
    // drops a spotlit system whose colour will not resolve.
    private static DominantOwner resolveClaimOwner(
            DominantOwner claimOwner,
            String selectedBlocId,
            DominantOwner spotlitClaimOwner) {
        if (selectedBlocId == null || !selectedBlocId.equals(claimOwner.factionId())) {
            return claimOwner;
        }
        return spotlitClaimOwner;
    }
}
