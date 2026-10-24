package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolve;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

/**
 * The political map's per-system holder read: the market contest, asked one system at a time -
 * under a spotlight, the presence-aware contest that keeps the spotlit bloc drawn wherever it lives.
 *
 * <p>The per-system half of {@link DominanceHolderProvider}. The weighting rule is sampled once
 * when the batch's resolve is opened, so every marked system in that batch is re-derived under the
 * same rule - and over the batch's own pass, so under the same grouping and the same walk the
 * holder source answers the batch's other questions from. That is what makes a single-system
 * refresh land the winning bloc, and the hatching, the bulk pass would have.
 */
public final class DominanceSystemHolderResolve
        implements SystemHolderResolve {

    private final DominancePass pass;

    // The spotlight the standing build was painted under, or null off filter. Resolved once per
    // batch, as the bulk pass resolves it once per walk.
    private final FilteredPolitics.Spotlight spotlight;

    private DominanceSystemHolderResolve(DominancePass pass, FilteredPolitics.Spotlight spotlight) {

        this.pass = pass;
        this.spotlight = spotlight;
    }

    /**
     * Opens one batch's dominance pass over the batch's reading of the sector, and the resolve
     * above it.
     *
     * @param pass          the batch's reading of the sector, under the grouping the standing
     *                      build was folded by
     * @param spotlitBlocId the bloc the standing build was spotlighting, or null off filter
     * @return the batch's resolve
     */
    public static SystemHolderResolve openResolveOver(HolderPass pass, String spotlitBlocId) {

        var dominancePass = DominancePass.readRulesFromLunaSettings(pass);

        return new DominanceSystemHolderResolve(
            dominancePass,
            spotlitBlocId == null
                ? null
                : FilteredPolitics.createSpotlight(dominancePass, spotlitBlocId));
    }

    @Override
    public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {

        return spotlight == null
            ? ResolvedSystemOwner.createSolidlyFilledBy(SectorPolitics.resolveDominantHolder(system, pass))
            : FilteredPolitics.resolveFilteredHolderIn(system, pass, spotlight);
    }
}
