package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.DominantHolder;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

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
 */
public final class ClaimAugmentedHolderProvider implements HolderProvider {

    /**
     * The shared instance: the held-dominance default extended with vanilla claims. Stateless
     * once built - the base provider and the claim reader it wraps are both stateless - so every
     * pass reuses it, and it is the default {@code PoliticalMapView} resolves holding through.
     */
    public static final ClaimAugmentedHolderProvider INSTANCE =
        new ClaimAugmentedHolderProvider(
            DefaultHolderProvider.INSTANCE,
            new VanillaClaimReader());

    private final HolderProvider heldHolderProvider;
    private final ClaimReader claimReader;

    ClaimAugmentedHolderProvider(
            HolderProvider heldHolderProvider,
            ClaimReader claimReader) {
        this.heldHolderProvider = heldHolderProvider;
        this.claimReader = claimReader;
    }

    @Override
    public HolderResolution resolveHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            String selectedBlocId) {

        var held = heldHolderProvider.resolveHolder(sector, grouping, selectedBlocId);
        var claimingHolderBySystemId = SectorClaims.resolveClaimingHolderBySystemId(
            sector,
            grouping,
            claimReader);

        // The spotlighted bloc's own claims fuse into its spotlight territory rather than their
        // plain bloc colour, so its spotlight holder is resolved once here and shared across every
        // system it claims - null off filter, or when there is no sector to read a palette from.
        var spotlitClaimHolder = selectedBlocId == null || sector == null
            ? null
            : FilteredPolitics.resolveSpotlitHolder(sector, grouping, selectedBlocId);

        return foldClaimsIntoHeld(
            held,
            claimingHolderBySystemId,
            selectedBlocId,
            spotlitClaimHolder);
    }

    // Adds each claimed system the held resolve left unowned to the holder map - so held and claimed
    // cells of one bloc fuse into a single territory - and records it unfilled, so the fill split
    // leaves it empty inside that shared border. A system already held keeps its held holder and
    // solid fill: the held signal is the stronger one. Pure over its inputs; the spotlight holder it
    // rekeys the selected bloc's own claims onto is resolved by the caller.
    private static HolderResolution foldClaimsIntoHeld(
            HolderResolution held,
            Map<String, DominantHolder> claimingHolderBySystemId,
            String selectedBlocId,
            DominantHolder spotlitClaimHolder) {

        var ownerBySystemId = new LinkedHashMap<>(held.ownerBySystemId());
        var unfilledSystemIds = new LinkedHashSet<>(held.unfilledSystemIds());

        for (var claim : claimingHolderBySystemId.entrySet()) {
            if (ownerBySystemId.containsKey(claim.getKey())) {
                continue;
            }
            var holder = resolveClaimHolder(claim.getValue(), selectedBlocId, spotlitClaimHolder);
            if (holder == null) {
                continue;
            }
            ownerBySystemId.put(claim.getKey(), holder);
            unfilledSystemIds.add(claim.getKey());
        }
        return new HolderResolution(
            ownerBySystemId,
            held.contestedSystemIds(),
            unfilledSystemIds);
    }

    // The holder a claimed system draws under. Off filter, or for any bloc other than the
    // spotlighted one, that is the claim's plain bloc holder - full colour off filter, and receded
    // by the style layer under a filter because its key is not the spotlight's. The spotlighted
    // bloc's own claims instead take its spotlight holder, fusing into its one territory at full
    // strength; a null spotlight holder (its palette gone) drops the claim, as the presence pass
    // drops a spotlit system whose colour will not resolve.
    private static DominantHolder resolveClaimHolder(
            DominantHolder claimHolder,
            String selectedBlocId,
            DominantHolder spotlitClaimHolder) {
                
        if (selectedBlocId == null || !selectedBlocId.equals(claimHolder.factionId())) {
            return claimHolder;
        }
        return spotlitClaimHolder;
    }
}
