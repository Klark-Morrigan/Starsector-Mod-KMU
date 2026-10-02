package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.ClaimReaderSource;

import kmu.maplayers.ownermap.holding.HolderPass;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolve;
import kmu.maplayers.ownermap.owners.holders.SystemHolderResolveSource;
import kmu.maplayers.ownermap.render.style.SectorBlocPalettes;
import kmu.maplayers.politicalmap.claims.PassClaimReaders;
import kmu.maplayers.politicalmap.claims.SectorClaims;

/**
 * The Claims view's per-system holder read: the claim mechanic, asked one system at a time.
 *
 * <p>The per-system half of {@link ClaimsHolderProvider}, so a marked system is re-derived to its
 * claimant exactly as the whole-sector resolve paints it - folded into its bloc and coloured by the
 * one rule {@link SectorClaims} states for both. The claim reader is opened over the batch's own
 * pass, so it reads the colonies the batch's other answers read rather than walking them again.
 *
 * <p>Unfiltered only: the incremental refresh folds nothing while a spotlight is up, so the
 * spotlight's rekeying - the one thing {@link ClaimsHolderProvider} adds under a filter - never
 * reaches a batch.
 */
public final class ClaimSystemHolderResolve implements SystemHolderResolve {

    private final HolderPass pass;
    private final ClaimReader claimReader;
    private final SectorBlocPalettes palettes;

    private ClaimSystemHolderResolve(HolderPass pass, ClaimReader claimReader) {
        this.pass = pass;
        this.claimReader = claimReader;
        this.palettes = new SectorBlocPalettes(pass.sector(), pass.grouping());
    }

    /**
     * Where a batch opens its claim read, through the given means of opening a claim reader.
     *
     * @param claimReaderSource the means of opening a reader over one pass - the same one the
     *                          view's whole-sector resolve reads claims through
     * @return the source a batch opens its resolve from
     */
    public static SystemHolderResolveSource createSourceReadingThrough(
            ClaimReaderSource claimReaderSource) {

        return pass -> new ClaimSystemHolderResolve(
            pass,
            PassClaimReaders.openClaimReaderOver(pass, claimReaderSource));
    }

    @Override
    public SystemOwner resolveHolderIn(StarSystemAPI system) {
        return SectorClaims.resolveClaimingHolderIn(system, pass, claimReader, palettes);
    }
}
