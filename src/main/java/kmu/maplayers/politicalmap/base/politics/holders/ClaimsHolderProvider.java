package kmu.maplayers.politicalmap.base.politics.holders;

import kmlib.starsector.systems.claims.ClaimReaderSource;
import kmlib.starsector.systems.claims.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.dominance.HolderPass;
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
 * desaturates every other claimant.
 *
 * <p>A pick with no claims anywhere therefore rekeys nothing, and this provider reports no
 * territory for it at all. That is the honest answer to "what does this faction claim" - the
 * picker offers every bloc that claims <em>or</em> holds something, so a colony-holding faction
 * that claims nowhere is pickable, and an empty claim map is what it has to say. Nothing here has
 * to detect the case; the rekey simply finds no system to move.
 *
 * <p>What such a pick is <em>not</em> left with is a wholly sunken sector. The systems it lives in
 * without claiming reach the render pass holderless, and the factionless cell builder spares those
 * the recede on the strength of the bloc's presence, so its colonies stay legible in the neutral
 * paint they carry off filter. That happens downstream of this provider and without a holder,
 * which is what keeps presence from reading here as a claim.
 *
 * <p>The resolution carries no fill exceptions either way. A system has exactly one claimant, so
 * there is no dominated-but-present state to hatch and nothing to leave empty - which lets the fill
 * split take its whole-cluster-solid fast path even under a filter.
 */
public final class ClaimsHolderProvider implements HolderProvider {

    /**
     * The one shared instance, reading vanilla claims. Stateless once built - what it holds is
     * the means of opening a reader rather than a reader - so every pass reuses it, and the
     * Claims view resolves holding through it.
     */
    public static final ClaimsHolderProvider INSTANCE =
        new ClaimsHolderProvider(VanillaClaimReader::new);

    private final ClaimReaderSource claimReaderSource;

    ClaimsHolderProvider(ClaimReaderSource claimReaderSource) {
        this.claimReaderSource = claimReaderSource;
    }

    @Override
    public HolderResolution resolveHolder(HolderPass pass, String selectedBlocId) {

        // A claim covers the whole territory here, so every claimed system paints solid: no
        // contested and no unfilled systems, which the fill split reads as its whole-cluster-solid
        // fast path. The spotlight only changes which key a claim carries, never its fill.
        return new HolderResolution(
                FilteredClaims.resolveFilteredClaims(
                        pass,
                        claimReaderSource.openReaderOver(pass.colonies()),
                        selectedBlocId),
                Set.of(),
                Set.of());
    }
}
