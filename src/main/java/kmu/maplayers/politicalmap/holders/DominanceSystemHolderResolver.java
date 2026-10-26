package kmu.maplayers.politicalmap.holders;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import java.util.Optional;

/**
 * The political map's per-system holder read: the market contest, asked one system at a time -
 * under a spotlight, the presence-aware contest that keeps the spotlit bloc drawn wherever it lives.
 *
 * <p>The per-system half of {@link DominanceHolderProvider}. The weighting rule is sampled once
 * when the batch's resolver is opened, so every marked system in that batch is re-derived under the
 * same rule - and over the batch's own pass, so under the same grouping and the same walk the
 * holder source answers the batch's other questions from. That is what makes a single-system
 * refresh land the winning bloc, and the hatching, the bulk pass would have.
 *
 * <p>Which of the two contests a batch asks is settled when its resolver opens, as the bulk pass
 * settles it once per walk.
 */
public final class DominanceSystemHolderResolver {

    private DominanceSystemHolderResolver() {
    }

    /**
     * Opens one batch's dominance pass over the batch's reading of the sector, and the resolver
     * above it.
     *
     * @param pass      the batch's reading of the sector, under the grouping the standing build was
     *                  folded by
     * @param spotlight the spotlight the standing build was painted under, opened over that same
     *                  pass; empty off filter
     * @return the batch's resolver
     */
    public static SystemHolderResolver openResolverUnder(HolderPass pass, Optional<BlocSpotlight> spotlight) {

        var dominancePass = DominancePass.readRulesFromLunaSettings(pass);

        return spotlight
            .map(openedSpotlight -> openSpotlitResolver(dominancePass, openedSpotlight))
            .orElseGet(() -> openPlainResolver(dominancePass));
    }

    // Off filter each system goes to its single dominant holder, filled solid as the bulk pass
    // fills every system it gives anybody.
    private static SystemHolderResolver openPlainResolver(DominancePass pass) {

        return system -> ResolvedSystemOwner.createSolidlyFilledBy(
            SectorPolitics.resolveDominantHolder(system, pass));
    }

    private static SystemHolderResolver openSpotlitResolver(DominancePass pass, BlocSpotlight spotlight) {
        return system -> FilteredPolitics.resolveFilteredHolderIn(system, pass, spotlight);
    }
}
