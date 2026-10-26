package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolverSource;
import kmu.maplayers.politicalmap.claims.FilteredClaimReader;

/**
 * The faction and alliance views' holding: held dominance, with each claimed-but-unheld system
 * drawn as an unfilled part of its claimant's territory.
 *
 * <p>A held system keeps its held holder and fill, the held signal being the stronger one, so its
 * claim is never read. A system a bloc claims but does not hold joins that bloc under the same key
 * its held systems carry, so the agnostic geometry fuses claimed and held cells into one bordered
 * territory. It is marked unfilled, so the fill split paints the held systems solid and leaves the
 * claimed ones empty inside that one frontier. On the alliances view the claimant folds to its
 * alliance bloc through the grouping exactly as a held system does, so an allied claimant's claims
 * roll into the alliance.
 *
 * <p>Under a spotlight claims recede with the map, not against it. The spotlit bloc's own claims take
 * its spotlight holder, so they fuse into its one territory and draw at full strength beside its
 * solid and hatched held systems. Every other bloc's claims keep their plain bloc holder, which the
 * style layer mutes exactly as it mutes that bloc's held cells. Both halves are opened under one
 * spotlight, so a held cell and a claimed cell of the spotlit bloc fuse.
 *
 * <p>The claim reader is opened over the walk's own pass, so it reads the colonies the held resolve
 * read rather than walking them again, and both halves describe the sector as it stood at one
 * moment.
 */
public final class ClaimAugmentedSystemHolderResolver
        implements SystemHolderResolver {

    private final SystemHolderResolver heldResolver;
    private final FilteredClaimReader claimReader;

    private ClaimAugmentedSystemHolderResolver(
            SystemHolderResolver heldResolver,
            FilteredClaimReader claimReader) {

        this.heldResolver = heldResolver;
        this.claimReader = claimReader;
    }

    /**
     * Where a rebuild or batch opens the held-dominance resolver extended with the claims a view
     * reads.
     *
     * @param claimReaderSource the source the view reads every claim through - the same one its
     *                          bands read claims through
     * @return the source a rebuild or batch opens its resolver from
     */
    public static SystemHolderResolverSource createSourceReadingThrough(ClaimReaderSource claimReaderSource) {

        return (pass, spotlitBlocId) -> {

            var spotlight = BlocSpotlight.openOver(pass, spotlitBlocId);

            return new ClaimAugmentedSystemHolderResolver(
                DominanceSystemHolderResolver.openResolverUnder(pass, spotlight),
                FilteredClaimReader.openOver(pass, claimReaderSource, spotlight));
        };
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        var held = heldResolver.resolveHolderIn(system);
        if (held.owner() != null) {
            return held;
        }

        var claimingHolder = claimReader.resolveClaimIn(system);

        return claimingHolder == null
            ? held
            : new ResolvedSystemOwner(claimingHolder, FillState.UNFILLED);
    }
}
