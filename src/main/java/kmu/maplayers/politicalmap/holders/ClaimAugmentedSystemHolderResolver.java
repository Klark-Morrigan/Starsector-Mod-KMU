package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolverSource;
import kmu.maplayers.politicalmap.claims.FilteredClaims;
import kmu.maplayers.politicalmap.claims.PassClaimReaders;

/**
 * The faction and alliance views' per-system holder read: held dominance one system at a time,
 * with a claimed-but-unheld system drawn as an unfilled part of its claimant's territory.
 *
 * <p>The per-system half of {@link ClaimAugmentedHolderProvider}, folding a claim by the one rule
 * that provider states, so a marked system lands as the whole-sector resolve paints it. Without it
 * a system whose last colony left while its bloc still claimed it would be re-derived unowned,
 * where a rebuild draws it as that bloc's unfilled claim.
 *
 * <p>The claim is read only where the held resolve left the system unowned, since a held system
 * keeps its held holder whatever is claimed there. The claim reader is opened over the batch's own
 * pass, so it reads the colonies the held resolve read rather than walking them again.
 */
public final class ClaimAugmentedSystemHolderResolver
        implements SystemHolderResolver {

    private final SystemHolderResolver heldResolver;
    private final HolderPass pass;
    private final ClaimReader claimReader;
    private final String spotlitBlocId;

    private ClaimAugmentedSystemHolderResolver(
            SystemHolderResolver heldResolver,
            HolderPass pass,
            ClaimReader claimReader,
            String spotlitBlocId) {

        this.heldResolver = heldResolver;
        this.pass = pass;
        this.claimReader = claimReader;
        this.spotlitBlocId = spotlitBlocId;
    }

    /**
     * Where a batch opens the held-dominance resolver extended with the claims a view reads.
     *
     * @param claimReaderSource the source the view reads every claim through - the same one its
     *                          whole-sector resolve reads claims through
     * @return the source a batch opens its resolver from
     */
    public static SystemHolderResolverSource createSourceReadingThrough(ClaimReaderSource claimReaderSource) {

        return (pass, spotlitBlocId) -> new ClaimAugmentedSystemHolderResolver(
            DominanceSystemHolderResolver.openResolverOver(pass, spotlitBlocId),
            pass,
            PassClaimReaders.openClaimReaderOver(pass, claimReaderSource),
            spotlitBlocId);
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        var held = heldResolver.resolveHolderIn(system);
        if (held.owner() != null) {
            return held;
        }

        return ClaimAugmentedHolderProvider.extendHeldByClaim(
            held,
            FilteredClaims.resolveFilteredClaimIn(system, pass, claimReader, spotlitBlocId));
    }
}
