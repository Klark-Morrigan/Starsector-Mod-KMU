package kmu.maplayers.politicalmap.base.dominance;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.colonies.Colonies;
import kmlib.starsector.systems.SystemColoniesIndex;

import kmu.maplayers.politicalmap.base.dominance.weighting.DominanceRules;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * One dominance pass over the sector: a rebuild's reading of it, plus the weighting rule that
 * scores each market.
 *
 * <p>The rule is the whole of what dominance adds to {@link HolderPass}. What the two halves have
 * in common - the sector, the grouping, the dev reveal, and the one walk of each system - is what
 * any owner-painted layer needs, and it is carried apart so the holder seam can take it without
 * naming this mechanic. What is here is what only a layer painted by market weights needs.
 *
 * <p>Bundled rather than threaded loose because the knobs always travel together: read once by
 * whoever opens the pass and handed down, so no system in the walk can drift onto a different rule
 * or a different reading of the sector mid-pass. The per-system reads below take a system rather
 * than a sector for the same reason the pass holds an index rather than one: a read that could
 * reach the sector is a read that could walk it again.
 *
 * @param rules   the weighting rule scoring each market's dominance worth
 * @param holding the rebuild's reading of the sector every layer shares - which sector, the
 *                grouping, the dev reveal, and the one walk of each system
 */
public record DominancePass(
    DominanceRules rules,
    HolderPass holding) {

    public DominancePass {
        Objects.requireNonNull(rules, "rules");
        Objects.requireNonNull(holding, "holding");
    }

    /**
     * The dominance pass a layer-generic one becomes once the weighting rule is named - what a
     * caller handed a {@link HolderPass} builds when it has to weigh markets with it, keeping the
     * one walk of each system the handed pass already holds.
     *
     * @param holding the rebuild's reading of the sector
     * @param rules   the weighting rule scoring each market's worth
     * @return that reading under that rule
     */
    public static DominancePass over(HolderPass holding, DominanceRules rules) {
        return new DominancePass(rules, holding);
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

        return over(
            HolderPass.over(sector, shouldIncludeUndiscoveredMarkets, grouping),
            rules);
    }

    /**
     * The weighting rule read from the player's live settings, applied to a reading of the sector
     * a caller already holds - what a dominance-painted resolve does with the pass the holder seam
     * handed it, the rule being the one knob that seam does not carry.
     *
     * @param holding the rebuild's reading of the sector
     * @return that reading under the live weighting rule
     */
    public static DominancePass readRulesFromLunaSettings(HolderPass holding) {
        return over(holding, DominanceRules.readFromLunaSettings());
    }

    /**
     * A pass reading the player's live LunaLib settings under an explicit grouping: the weighting
     * rule and the undiscovered-markets reveal are sampled once here, so the whole pass resolves
     * under the settings in force when it began even if the player moves a toggle mid-walk.
     *
     * @param sector   the sector this pass reads
     * @param grouping the grouping this pass folds factions into blocs under
     * @return a pass carrying the live rule and reveal paired with the grouping
     */
    public static DominancePass readFromLunaSettings(SectorAPI sector, HolderGrouping grouping) {
        return readRulesFromLunaSettings(HolderPass.readFromLunaSettings(sector, grouping));
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
     * The sector this pass reads.
     *
     * @return the sector; null when the pass was opened over none
     */
    public SectorAPI sector() {
        return holding.sector();
    }

    /**
     * The grouping this pass folds factions into blocs under.
     *
     * @return the grouping
     */
    public HolderGrouping grouping() {
        return holding.grouping();
    }

    /**
     * Whether undiscovered colonies count toward this pass's reads.
     *
     * @return true while the "show undiscovered markets" dev reveal is lifting the fog
     */
    public boolean shouldIncludeUndiscoveredMarkets() {
        return holding.shouldIncludeUndiscoveredMarkets();
    }

    /**
     * This pass's one walk of each system, for a reader that has to be handed the walk itself
     * rather than a read made through it.
     *
     * @return the colony index, discarded with this pass
     */
    public SystemColoniesIndex colonies() {
        return holding.colonies();
    }

    /**
     * The systems this pass walks, in the sector's own order; empty for a pass over no sector.
     *
     * @return the sector's star systems
     */
    public List<StarSystemAPI> readSystems() {
        return holding.readSystems();
    }

    /**
     * Whether this pass can read an economy at all - a sector to walk, with its economy up.
     *
     * @return true when both the sector and its economy are there to read
     */
    public boolean canReadEconomy() {
        return holding.canReadEconomy();
    }

    /**
     * The colonies in one system, off this pass's single walk of it - what a reader needing the
     * colonies themselves rather than their weights takes, so it shares the walk with the reads
     * below instead of adding one.
     *
     * @param system the system to read; null yields an empty set
     * @return the system's colony set
     */
    public Colonies readColoniesIn(StarSystemAPI system) {
        return holding.readColoniesIn(system);
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
            shouldIncludeUndiscoveredMarkets());
    }

    /**
     * The factions present in this system under the pass's reveal - everyone holding a colony the
     * player may be shown, whether or not this mechanic could weigh it.
     *
     * <p>Beside the footprints rather than derived from them, and that is the whole of the
     * separation: every term of a dominance weight is economy-fed, so a faction holding nothing the
     * economy lists raises no footprint at all, and reading presence off the weights would leave it
     * out of every listing while the band beneath the cell went on counting it. The wider set is
     * the one to read: it can only ever be a superset of what was weighed, so a caller ranking a
     * system cannot lose a faction to a filter applied on the weighed side.
     *
     * @param system the system whose colonies are read
     * @return the ids of the factions present there; empty when the player knows of no colony
     */
    public Set<String> readKnownColonyFactionIds(StarSystemAPI system) {
        return holding.readKnownColonyFactionIds(system);
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
        return grouping().regroupByBloc(
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

        return grouping().regroupByBloc(
            KnownMarketFootprints.readContributionsByFaction(
                readColoniesIn(system),
                rules,
                shouldIncludeUndiscoveredMarkets()),
            FactionMarketContribution.EMPTY,
            FactionMarketContribution::merge);
    }

    /**
     * The market-proximity tie-break for this system under the pass's reveal and grouping,
     * consulted only when blocs tie on every weight level so it resolves a dead heat by who holds
     * the market nearest the system centre. Lazy - it reads no geometry unless a tie forces it - so
     * every pass shares one on-demand tie-break rather than each resolver building its own.
     *
     * <p>Here rather than on the reading of the sector it is built from, because a tie is settled
     * among the colonies this mechanic weighs: the tie-break reaches the same weighed selection
     * the ranking that tied was folded from, and a reading of the sector that named it would be
     * carrying a mechanic no other layer's pass has any use for.
     *
     * @param system the system the tie-break ranks blocs within
     * @return the comparator that orders tied bloc ids for this system
     */
    public Comparator<String> tieBreakFor(StarSystemAPI system) {
        return MarketProximityTieBreak.forSystem(
            system,
            readColoniesIn(system),
            shouldIncludeUndiscoveredMarkets(),
            grouping());
    }
}
