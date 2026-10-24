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
import kmu.maplayers.politicalmap.claims.SectorClaims;

/**
 * The Claims view's per-system holder read: the claim mechanic, asked one system at a time.
 *
 * <p>The per-system half of {@link ClaimsHolderProvider}, so a marked system is re-derived to its
 * claimant exactly as the whole-sector resolve paints it - folded into its bloc and coloured by the
 * one rule {@link SectorClaims} states for both, and under a spotlight rekeyed by the one rule
 * {@link FilteredClaims} states for both. The claim reader is opened over the batch's own pass, so
 * it reads the colonies the batch's other answers read rather than walking them again.
 *
 * <p>Every claim fills solid, as the provider's do: a system has one claimant, so there is no
 * contested state to hatch.
 */
public final class ClaimSystemHolderResolver
        implements SystemHolderResolver {

    private final HolderPass pass;
    private final ClaimReader claimReader;
    private final String spotlitBlocId;

    private ClaimSystemHolderResolver(
            HolderPass pass,
            ClaimReader claimReader,
            String spotlitBlocId) {

        this.pass = pass;
        this.claimReader = claimReader;
        this.spotlitBlocId = spotlitBlocId;
    }

    /**
     * Where a batch opens its claim read, through the given means of opening a claim reader.
     *
     * @param claimReaderSource the means of opening a reader over one pass - the same one the
     *                          view's whole-sector resolve reads claims through
     * @return the source a batch opens its resolver from
     */
    public static SystemHolderResolverSource createSourceReadingThrough(
            ClaimReaderSource claimReaderSource) {

        return (pass, spotlitBlocId) -> new ClaimSystemHolderResolver(
            pass,
            PassClaimReaders.openClaimReaderOver(pass, claimReaderSource),
            spotlitBlocId);
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        return ResolvedSystemOwner.createSolidlyFilledBy(
            FilteredClaims.resolveFilteredClaimIn(system, pass, claimReader, spotlitBlocId));
    }
}
