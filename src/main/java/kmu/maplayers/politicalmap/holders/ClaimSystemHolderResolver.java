package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolverSource;
import kmu.maplayers.politicalmap.claims.FilteredClaimReader;
import kmu.maplayers.politicalmap.claims.SectorClaims;

/**
 * The Claims view's holding: every claimed star system painted solid in its claimant's colours, with
 * nothing held-derived. This is the claim mechanic standing on its own - where
 * {@link ClaimAugmentedSystemHolderResolver} layers claims onto held dominance as unfilled
 * extensions, this paints claims as the whole territory, so a claimed system fills solid exactly as
 * a held one does on the faction view.
 *
 * <p>A claimant is folded into its bloc and coloured by the one rule {@link SectorClaims} states, and
 * under a spotlight rekeyed by the one rule {@link FilteredClaimReader} states. A claimant's systems
 * already share one key and so already trace one border, so the rekey regroups nothing. What it buys
 * is the answer to {@code isSpotlitBloc}, which spares the pick the recede that then mutes and
 * desaturates every other claimant.
 *
 * <p>A pick with no claims anywhere therefore rekeys nothing and paints no territory. That is the
 * honest answer to "what does this faction claim": the picker offers every bloc that claims
 * <em>or</em> holds something, so a colony-holding faction that claims nowhere is pickable, and
 * nothing here has to detect the case. Its colonies are not left in a wholly sunken sector either:
 * the systems it lives in reach the render pass holderless, and the factionless cell builder spares
 * those the recede on the strength of the bloc's presence. That happens downstream and without a
 * holder, which keeps presence from reading here as a claim.
 *
 * <p>Every claim fills solid. A system has one claimant, so there is no contested state to hatch and
 * nothing to leave empty, which lets the fill split take its whole-cluster-solid fast path even under
 * a filter. The claim reader is opened over the walk's own pass, so it reads the colonies the walk's
 * other answers read rather than walking them again.
 */
public final class ClaimSystemHolderResolver
        implements SystemHolderResolver {

    private final FilteredClaimReader claimReader;

    private ClaimSystemHolderResolver(FilteredClaimReader claimReader) {
        this.claimReader = claimReader;
    }

    /**
     * Where a rebuild or batch opens its claim read, through the given means of opening a claim
     * reader.
     *
     * @param claimReaderSource the means of opening a reader over one pass - the same one the
     *                          view's bands read claims through
     * @return the source a rebuild or batch opens its resolver from
     */
    public static SystemHolderResolverSource createSourceReadingThrough(
            ClaimReaderSource claimReaderSource) {

        return (pass, spotlitBlocId) -> new ClaimSystemHolderResolver(
            FilteredClaimReader.openOver(
                pass,
                claimReaderSource,
                BlocSpotlight.openOver(pass, spotlitBlocId)));
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        return ResolvedSystemOwner.createSolidlyFilledBy(claimReader.resolveClaimIn(system));
    }
}
