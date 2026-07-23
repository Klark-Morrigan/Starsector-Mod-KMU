package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.ClaimReader;
import kmlib.starsector.systems.VanillaClaimReader;

import kmu.maplayers.politicalmap.base.dominance.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorClaims;

import java.util.Set;

/**
 * The ownership source the Claims view resolves under: every claimed star system painted solid
 * in its claimant's colours, with nothing held-derived. This is the claim mechanic standing on
 * its own - where {@link ClaimAugmentedOwnershipProvider} layers claims onto held dominance as
 * unfilled extensions, this paints claims as the whole territory, so a claimed system fills solid
 * exactly as a held one does on the faction view.
 *
 * <p>It reads no filter. The Claims view offers no spotlight - claim presence is not the market
 * presence the shared picker derives its selectable set from - so the selected-bloc argument is
 * ignored and every claimed system paints at full strength. The resolution therefore carries no
 * fill exceptions, which lets the fill split take its whole-region-solid fast path.
 */
public final class ClaimsOwnershipProvider implements OwnershipProvider {

    /**
     * The one shared instance, reading vanilla claims. Stateless once built - the claim reader it
     * wraps is stateless - so every pass reuses it, and the Claims view resolves ownership through
     * it.
     */
    public static final ClaimsOwnershipProvider INSTANCE =
            new ClaimsOwnershipProvider(new VanillaClaimReader());

    private final ClaimReader claimReader;

    ClaimsOwnershipProvider(ClaimReader claimReader) {
        this.claimReader = claimReader;
    }

    @Override
    public OwnershipResolution resolveOwnership(
            SectorAPI sector, OwnershipGrouping grouping, String selectedBlocId) {
        // Claim ownership is the whole territory here, so every claimed system paints solid: no
        // contested and no unfilled systems, which the fill split reads as its whole-region-solid
        // fast path. The selected bloc is ignored, since this view offers no spotlight.
        return new OwnershipResolution(
                SectorClaims.resolveClaimingOwnerBySystemId(sector, grouping, claimReader),
                Set.of(),
                Set.of());
    }
}
