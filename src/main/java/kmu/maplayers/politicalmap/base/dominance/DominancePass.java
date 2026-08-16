package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.systems.SystemColonies;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.politicalmap.base.PoliticalMapDevToggles;
import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import java.util.Comparator;
import java.util.Map;
import java.util.Objects;

/**
 * One dominance pass over the sector: the three settings-derived knobs it resolves under, and
 * the one walk of each system it resolves them from.
 *
 * <p>The dominance pipeline resolves holders under three knobs that always travel together:
 * the weighting rule that scores each market, the dev reveal that decides whether undiscovered
 * colonies count, and the grouping that folds factions into blocs. Threading them as three loose
 * parameters bred a ladder of overloads and a seven-argument inner resolve; bundling them here
 * lets a caller read the player's settings once ({@link #readFromLunaSettings}) and hand the whole
 * pass down, so no system in the walk can drift onto a different rule mid-pass.
 *
 * <p>The colony index is here for the same reason and one more. A pass reads each system for
 * several things at once - who holds it, what its blocs contribute, what a hover has to account
 * for - and each of those used to walk the system itself, so a rebuild paid the walk two or three
 * times over per system. Held on the pass, the walk is paid once and shared by everything the pass
 * reads, which is why the per-system reads below take a system rather than a sector: a read that
 * could reach the sector is a read that could walk it again.
 *
 * <p>That also fixes a pass to the sector it was opened over and to the moment it was opened.
 * A pass is built for one rebuild and discarded with it, and one kept past that would go on
 * answering off a sector that has since moved on.
 *
 * @param rules                            the weighting rule scoring each market's dominance worth
 * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward dominance (the
 *                                         "show all factions" dev reveal); false applies the normal
 *                                         known-to-player filter
 * @param grouping                         the grouping that folds factions into blocs before
 *                                         dominance is compared; the identity grouping resolves the
 *                                         plain faction view
 * @param colonies                         the pass's one walk of each system, shared by every read
 *                                         below
 */
public record DominancePass(
    DominanceRules rules,
    boolean shouldIncludeUndiscoveredMarkets,
    HolderGrouping grouping,
    SystemColoniesIndex colonies) {

    public DominancePass {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(grouping, "grouping");
        Objects.requireNonNull(colonies, "colonies");
    }

    /**
     * A pass over one sector under explicit knobs, opening the colony index the reads below
     * share - the entry a caller that resolves the knobs itself builds a pass through.
     *
     * @param sector                           the sector this pass reads; null yields a pass
     *                                         answering an empty colony set for every system,
     *                                         matching how the reads treat an unreachable sector
     * @param rules                            the weighting rule scoring each market's worth
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward dominance
     * @param grouping                         the grouping this pass folds factions into blocs under
     * @return a pass over that sector carrying those knobs
     */
    public static DominancePass over(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets,
            HolderGrouping grouping) {

        return new DominancePass(
            rules,
            shouldIncludeUndiscoveredMarkets,
            grouping,
            new SystemColoniesIndex(sector));
    }

    /**
     * A pass reading the player's live LunaLib settings under an explicit grouping: the weighting
     * rule and the show-all-factions reveal are sampled once here so the whole pass resolves under
     * the settings in force when it began, even if the player changes a toggle mid-walk.
     *
     * @param sector   the sector this pass reads
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live rule and reveal paired with the grouping
     */
    public static DominancePass readFromLunaSettings(SectorAPI sector, HolderGrouping grouping) {
        return over(
            sector,
            DominanceRules.readFromLunaSettings(),
            PoliticalMapDevToggles.readFromLunaSettings().isShowingAllFactions(),
            grouping);
    }

    /**
     * A pass reading the player's live settings under the faction (identity) grouping - every
     * faction its own bloc - for the plain faction view.
     *
     * @param sector the sector this pass reads
     * @return a pass carrying the live rule and reveal under the identity grouping
     */
    public static DominancePass readFromLunaSettings(SectorAPI sector) {
        return readFromLunaSettings(sector, HolderGrouping.identity());
    }

    /**
     * The colonies in one system, off this pass's single walk of it - what a reader needing the
     * colonies themselves rather than their weights takes, so it shares the walk with the reads
     * below instead of adding one.
     *
     * @param system the system to read; null yields an empty set
     * @return the system's colony set
     */
    public SystemColonies readColoniesIn(StarSystemAPI system) {
        return colonies.readColoniesIn(system);
    }

    /**
     * This system's per-faction footprints under the pass's rule and reveal, before any grouping:
     * each faction's known markets folded into its own footprint. The unowned read the per-bloc
     * read below builds on, and the one a two-tier standings breakdown needs whole so it can rank a
     * bloc's members individually - both taking the read from the pass rather than re-deriving it
     * from the loose knobs.
     *
     * @param system the system whose markets are folded
     * @return each present faction's footprint in the system; empty when the system holds no known
     *         owned market
     */
    public Map<String, MarketFootprint> readFootprintsByFaction(StarSystemAPI system) {
        return KnownMarketFootprints.readByFaction(
            readColoniesIn(system),
            rules,
            shouldIncludeUndiscoveredMarkets);
    }

    /**
     * This system's per-bloc footprints under the pass's rule, reveal, and grouping: the per-faction
     * footprints regrouped into per-bloc footprints (a no-op fold under identity, a member-summing
     * merge under an alliance grouping). The one read shared by the holder resolve and the
     * filter's presence resolve, so both rank the same footprints.
     *
     * @param system the system whose markets are folded
     * @return each present bloc's footprint in the system; empty when no bloc holds a folded market
     */
    public Map<String, MarketFootprint> readBlocFootprints(StarSystemAPI system) {
        return grouping.regroupByBloc(
            readFootprintsByFaction(system),
            MarketFootprint.EMPTY,
            MarketFootprint::merge);
    }

    /**
     * This system's per-bloc contributions under the pass's rule, reveal, and grouping: each
     * faction's markets folded into its dominance footprint and raw colony size, then regrouped so
     * an alliance's members sum into the alliance's one contribution.
     *
     * <p>The whole read {@link #readBlocFootprints} projects down to, for the stats aggregations
     * that also need raw colony size. Held here rather than at each aggregation because assembling
     * it means naming the rule, the reveal, the grouping, and the fold identity together - four
     * knobs a caller would otherwise thread by hand, and four chances for one aggregation to read
     * the economy under a different set than another.
     *
     * @param system the system whose markets are folded
     * @return each present bloc's contribution in the system; empty when no bloc holds a folded
     *         market
     */
    public Map<String, FactionMarketContribution> readBlocContributions(StarSystemAPI system) {

        return grouping.regroupByBloc(
            KnownMarketFootprints.readContributionsByFaction(
                readColoniesIn(system),
                rules,
                shouldIncludeUndiscoveredMarkets),
            FactionMarketContribution.EMPTY,
            FactionMarketContribution::merge);
    }

    /**
     * The market-proximity tie-break for this system under the pass's reveal and grouping,
     * consulted only when blocs tie on every weight level so it resolves a dead heat by who holds
     * the market nearest the system centre. Lazy - it reads no geometry unless a tie forces it - so
     * every pass shares one on-demand tie-break rather than each resolver building its own.
     *
     * @param sector the sector whose economy and geometry the tie-break reads
     * @param system the system the tie-break ranks blocs within
     * @return the comparator that orders tied bloc ids for this system
     */
    public Comparator<String> tieBreakFor(SectorAPI sector, StarSystemAPI system) {
        return MarketProximityTieBreak.forSystem(
            sector,
            system,
            shouldIncludeUndiscoveredMarkets,
            grouping);
    }
}
