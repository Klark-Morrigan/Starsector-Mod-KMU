package kmu.maplayers.politicalmap.base.politics.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.claims.ClaimReader;
import kmlib.starsector.systems.claims.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;
import kmu.maplayers.politicalmap.base.politics.FilteredClaims;

import java.util.Set;

/**
 * The holding source the Claims view resolves under: every claimed star system painted solid
 * in its claimant's colours, with nothing held-derived. This is the claim mechanic standing on
 * its own - where {@link ClaimAugmentedHolderProvider} layers claims onto held dominance as
 * unfilled extensions, this paints claims as the whole territory, so a claimed system fills solid
 * exactly as a held one does on the faction view.
 *
 * <p>Under a spotlight it recedes the sector the same way the held layers do, though by a smaller
 * change than theirs. A claimant's systems already share one key and so already trace one border;
 * moving them to the spotlight key is a one-for-one swap that regroups nothing. What it buys is the
 * answer to {@code isSpotlitBloc}, which is what spares the pick the recede that then mutes and
 * desaturates every other claimant. The Claims picker lists exactly the blocs that claim something,
 * so a pick always has territory here to hold at full strength.
 *
 * <p>The resolution carries no fill exceptions either way. A system has exactly one claimant, so
 * there is no dominated-but-present state to hatch and nothing to leave empty - which lets the fill
 * split take its whole-cluster-solid fast path even under a filter.
 */
public final class ClaimsHolderProvider implements HolderProvider {

    /**
     * The one shared instance, reading vanilla claims. Stateless once built - the claim reader it
     * wraps is stateless - so every pass reuses it, and the Claims view resolves holding through
     * it.
     */
    public static final ClaimsHolderProvider INSTANCE =
        new ClaimsHolderProvider(new VanillaClaimReader());

    private final ClaimReader claimReader;

    ClaimsHolderProvider(ClaimReader claimReader) {
        this.claimReader = claimReader;
    }

    @Override
    public HolderResolution resolveHolder(
            SectorAPI sector,
            HolderGrouping grouping,
            String selectedBlocId) {
                
        // A claim covers the whole territory here, so every claimed system paints solid: no
        // contested and no unfilled systems, which the fill split reads as its whole-cluster-solid
        // fast path. The spotlight only changes which key a claim carries, never its fill.
        return new HolderResolution(
                FilteredClaims.resolveFilteredClaims(
                        sector,
                        grouping,
                        claimReader,
                        selectedBlocId),
                Set.of(),
                Set.of());
    }
}
