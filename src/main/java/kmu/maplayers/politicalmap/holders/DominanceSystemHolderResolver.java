package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.ownermap.owners.ResolvedSystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.SystemHolderResolver;
import kmu.maplayers.politicalmap.dominance.DominancePass;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;
import kmu.maplayers.politicalmap.dominance.weighting.DominanceRules;

import java.util.Optional;

/**
 * The political map's per-system holder read: the market contest, asked one system at a time -
 * under a spotlight, the presence-aware contest that keeps the spotlit bloc drawn wherever it lives.
 *
 * <p>The held half of {@link ClaimAugmentedSystemHolderResolver}, which every view the contest paints
 * resolves under. The weighting rule is sampled once when the resolver is opened, so every system a
 * rebuild or batch asks is settled under the same rule - and over the walk's own pass, so under the
 * same grouping and the same walk the holder source answers its other questions from. That is what
 * makes a single-system refresh land the winning bloc, and the hatching, the rebuild would have.
 *
 * <p>Which of the two contests a resolver asks is settled when it opens, so no system asked of it
 * tests for a spotlight again.
 */
public final class DominanceSystemHolderResolver {

    private DominanceSystemHolderResolver() {
    }

    /**
     * Opens one rebuild's or batch's dominance pass over its reading of the sector, and the
     * resolver above it.
     *
     * @param pass      the walk's reading of the sector, under the grouping the build is folded by
     * @param spotlight the spotlight the build is painted under, opened over that same pass; empty
     *                  off filter
     * @return the walk's resolver
     */
    public static SystemHolderResolver openResolverUnder(HolderPass pass, Optional<BlocSpotlight> spotlight) {

        var dominancePass = DominancePass.readRulesFromLunaSettings(pass);

        var contest = spotlight
            .map(openedSpotlight -> openSpotlitContest(dominancePass, openedSpotlight))
            .orElseGet(() -> openPlainContest(dominancePass));

        return new RuleDescribingResolver(contest, dominancePass.rules());
    }

    // Off filter each system goes to its single dominant holder, filled solid: with nobody spotlit
    // there is no presence to hatch.
    private static SystemHolderResolver openPlainContest(DominancePass pass) {

        return system -> ResolvedSystemOwner.createSolidlyFilledBy(
            SectorPolitics.resolveDominantHolder(system, pass));
    }

    private static SystemHolderResolver openSpotlitContest(DominancePass pass, BlocSpotlight spotlight) {
        return system -> FilteredPolitics.resolveFilteredHolderIn(system, pass, spotlight);
    }

    /**
     * Either contest, carrying the weighting rule it was opened under so the batch's line can name it.
     *
     * @param contest the contest the resolver asks of each system
     * @param rules   the weighting rule sampled when the resolver opened
     */
    private record RuleDescribingResolver(
        SystemHolderResolver contest,
        DominanceRules rules)
            implements SystemHolderResolver {

        @Override
        public ResolvedSystemOwner resolveHolderIn(StarSystemAPI system) {
            return contest.resolveHolderIn(system);
        }

        @Override
        public String describeSampledRules() {
            return rules.toString();
        }
    }
}
