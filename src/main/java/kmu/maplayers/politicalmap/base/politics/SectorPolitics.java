package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmu.maplayers.politicalmap.base.PoliticalMapDevOverrides;
import kmu.maplayers.politicalmap.base.politics.weighting.DominanceRules;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * Resolves which faction owns each star system and in which colors that owner
 * paints.
 *
 * <p>The dominance-and-palette half of the ownership pipeline: it takes the known
 * market footprints {@link KnownMarketFootprints} reads from the economy, asks
 * {@link SystemDominance} which faction holds the system, and resolves that
 * winner's authored UI shades into a {@link DominantOwner} the render layer draws.
 * Confining the winning owner's {@code FactionAPI} palette lookup here keeps the
 * render layer clear of Starsector economy and faction types.
 */
public final class SectorPolitics {

    private SectorPolitics() {
    }

    /**
     * Builds the dominant owner - faction id and draw color - for every inhabited
     * star system.
     *
     * <p>The render-ready output of the ownership pipeline: resolving the faction
     * and its palette here confines {@code FactionAPI} access to this adapter, so
     * the render layer consumes a plain {@link DominantOwner} and never reaches
     * into the economy. Both facts come from one dominance pass, and the id is
     * kept beside the color so per-owner styling - and later per-owner behaviour -
     * reads the same winner the fill was decided by.
     *
     * <p>Reads the player's dominance-weighting rules once up front, so every
     * system in the pass resolves under the same rule even if the player applies
     * a settings change mid-walk.
     *
     * @param sector the sector whose economy is read; null yields an empty map
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(SectorAPI sector) {
        return resolveDominantOwnerBySystemId(sector, OwnershipGrouping.identity());
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * ownership grouping and the player's live settings.
     *
     * <p>The view-aware live entry point: a political-map view supplies its grouping
     * (identity for the faction view, alliance blocs for the alliances view) and the
     * dominance rule and dev reveal are read from the player's current settings, so
     * the winning bloc per system reflects both the active view and the live toggles.
     *
     * @param sector   the sector whose economy is read; null yields an empty map
     * @param grouping the ownership grouping that collapses factions into blocs for
     *                 this pass
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector, OwnershipGrouping grouping) {
        return resolveDominantOwnerBySystemId(
                sector,
                DominanceRules.readFromLunaSettings(),
                PoliticalMapDevOverrides.readFromLunaSettings().isShowingAllFactions(),
                grouping);
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * weighting rule, the normal known-to-player filter, and the faction (identity)
     * grouping.
     *
     * @param sector the sector whose economy is read; null yields an empty map
     * @param rules  the dominance-weighting rules for this pass
     * @return the dominant owner keyed by system id; a system with no owned
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector, DominanceRules rules) {
        return resolveDominantOwnerBySystemId(
                sector,
                rules,
                false,
                OwnershipGrouping.identity());
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * weighting rule and the faction (identity) grouping, for a caller that has
     * already read the player's toggles for the surrounding pass.
     *
     * @param sector                       the sector whose economy is read; null yields
     *                                     an empty map
     * @param rules                        the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @return the dominant owner keyed by system id; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        return resolveDominantOwnerBySystemId(
                sector,
                rules,
                shouldIncludeUndiscoveredMarkets,
                OwnershipGrouping.identity());
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * weighting rule and ownership grouping, for a caller that has already read the
     * player's toggles for the surrounding pass.
     *
     * @param sector                       the sector whose economy is read; null yields
     *                                     an empty map
     * @param rules                        the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @param grouping                     the ownership grouping that collapses factions
     *                                     into blocs before dominance is compared; the
     *                                     identity grouping resolves the faction view
     * @return the dominant owner keyed by system id; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        if (sector == null) {
            return ownerBySystemId;
        }

        for (var system : sector.getStarSystems()) {
            var owner = resolveDominantOwner(
                    sector,
                    system,
                    rules,
                    shouldIncludeUndiscoveredMarkets,
                    grouping);
            if (owner != null) {
                ownerBySystemId.put(system.getId(), owner);
            }
        }
        return ownerBySystemId;
    }

    /**
     * Resolves the dominant owner of one star system - the same result the bulk
     * pass would put under this system's id, computed for it alone.
     *
     * <p>The single-system entry point the incremental refresh path leans on:
     * when one colony's size changes, only that system's ownership can shift, so
     * only it is re-derived rather than re-walking the whole economy. Shares the
     * footprint, dominance rule, and palette lookup with
     * {@link #resolveDominantOwnerBySystemId}, so a system resolves the same
     * winner and colors whether it is refreshed alone or in the full pass.
     *
     * <p>Reads the player's dominance-weighting rules live, so a single-system
     * refresh resolves under the player's current rule.
     *
     * @param sector the sector whose economy is read; null (or a null economy)
     *               yields null
     * @param system the system to resolve; null yields null
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(SectorAPI sector, StarSystemAPI system) {
        return resolveDominantOwner(sector, system, OwnershipGrouping.identity());
    }

    /**
     * Resolves the dominant owner of one star system under an explicit ownership
     * grouping and the player's live settings.
     *
     * <p>The view-aware single-system entry point: the incremental refresh path
     * hands in the active view's grouping so a re-derived system resolves the same
     * winning bloc the bulk pass would under that view, reading the dominance rule
     * and dev reveal live like the parameterless entry.
     *
     * @param sector   the sector whose economy is read; null (or a null economy)
     *                 yields null
     * @param system   the system to resolve; null yields null
     * @param grouping the ownership grouping that collapses factions into blocs for
     *                 this pass
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(
            SectorAPI sector,
            StarSystemAPI system,
            OwnershipGrouping grouping) {
        return resolveDominantOwner(
                sector,
                system,
                DominanceRules.readFromLunaSettings(),
                PoliticalMapDevOverrides.readFromLunaSettings().isShowingAllFactions(),
                grouping);
    }

    /**
     * Resolves the dominant owner of one star system under an explicit weighting
     * rule, the normal known-to-player filter, and the faction (identity) grouping.
     *
     * @param sector the sector whose economy is read; null (or a null economy)
     *               yields null
     * @param system the system to resolve; null yields null
     * @param rules  the dominance-weighting rules for this pass
     * @return the dominant owner, or null when the system holds no owned market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules) {
        return resolveDominantOwner(
                sector,
                system,
                rules,
                false,
                OwnershipGrouping.identity());
    }

    /**
     * Resolves the dominant owner of one star system under an explicit weighting
     * rule and the faction (identity) grouping, for a caller that has already read
     * the player's toggles for the surrounding pass.
     *
     * @param sector                       the sector whose economy is read; null (or a
     *                                     null economy) yields null
     * @param system                       the system to resolve; null yields null
     * @param rules                        the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @return the dominant owner, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets) {
        return resolveDominantOwner(
                sector,
                system,
                rules,
                shouldIncludeUndiscoveredMarkets,
                OwnershipGrouping.identity());
    }

    /**
     * Resolves the dominant owner of one star system under an explicit weighting
     * rule and ownership grouping, for a caller that has already read the player's
     * toggles for the surrounding pass.
     *
     * <p>The core of the ownership pipeline: it reads each faction's footprint,
     * regroups those footprints into per-bloc footprints under the grouping (a
     * no-op fold under the identity grouping, a member-summing merge under an
     * alliance grouping), ranks the blocs, then colours the winning bloc through
     * the faction the grouping names for its palette. Under identity the bloc id is
     * the faction id and its colour faction is itself, so the result is the plain
     * faction owner.
     *
     * @param sector                       the sector whose economy is read; null (or a
     *                                     null economy) yields null
     * @param system                       the system to resolve; null yields null
     * @param rules                        the dominance-weighting rules for this pass -
     *                                     whether stability scales each rating and
     *                                     whether an attached station lifts it - before
     *                                     dominance is compared
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count toward
     *                                     dominance (the "show all factions" dev reveal);
     *                                     false applies the normal known-to-player filter
     * @param grouping                     the ownership grouping that collapses factions
     *                                     into blocs before dominance is compared; the
     *                                     identity grouping resolves the faction view
     * @return the dominant owner, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return null;
        }
        var footprintByFactionId = KnownMarketFootprints.readByFaction(
                sector,
                system,
                rules,
                shouldIncludeUndiscoveredMarkets);
        var footprintByBlocId = regroupByBloc(
                footprintByFactionId,
                grouping,
                MarketFootprint.EMPTY,
                MarketFootprint::merge);
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
                footprintByBlocId,
                MarketProximityTieBreak.forSystem(
                        sector,
                        system,
                        shouldIncludeUndiscoveredMarkets,
                        grouping));
        if (dominantBlocId == null) {
            return null;
        }
        return resolveBlocOwner(sector, grouping, dominantBlocId);
    }

    /**
     * The whole-sector {@link BlocStats} for every bloc holding a visible market somewhere, under a
     * grouping - the filter picker's selectable set (a bloc present here has presence of at least
     * one, which is the {@code presence > 0} gate) paired with the four numbers the picker sorts and
     * displays them by, all from one grouped per-system dominance pass.
     *
     * <p>One walk yields all four metrics so the picker never re-reads the economy per number: each
     * system's per-faction contributions are regrouped into per-bloc footprints and market sizes, the
     * one dominant bloc is resolved, and every present bloc takes a present-system entry (the dominant
     * one also a domination count). Reading the same footprint, dominance inputs, and grouping the
     * per-system ownership pass uses keeps the selectable gate honest - a bloc is offered exactly when
     * it holds territory it could paint - rather than a second, drifting definition of presence. The
     * order follows the economy walk, which each view then maps into its own picker options.
     *
     * @param sector                       the sector whose economy is read; null (or a null economy)
     *                                     yields an empty map
     * @param rules                        the dominance-weighting rules for this read, read once by
     *                                     the caller so the whole read resolves under one rule
     * @param shouldIncludeUndiscoveredMarkets whether undiscovered colonies count (the "show all
     *                                     factions" dev reveal); false applies the normal
     *                                     known-to-player filter
     * @param grouping                     the ownership grouping that collapses factions into blocs;
     *                                     the identity grouping keeps every faction its own bloc
     * @return each present bloc's stats, keyed by bloc id in economy-walk order; empty when no bloc
     *         holds a visible market
     */
    public static Map<String, BlocStats> aggregateBlocStats(
            SectorAPI sector,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping) {
        var statsByBlocId = new LinkedHashMap<String, BlocStats>();
        if (sector == null || sector.getEconomy() == null) {
            return statsByBlocId;
        }
        for (var system : sector.getStarSystems()) {
            accumulateSystemStats(
                    statsByBlocId,
                    sector,
                    system,
                    rules,
                    shouldIncludeUndiscoveredMarkets,
                    grouping);
        }
        return statsByBlocId;
    }

    /**
     * Colours a bloc into a render-ready {@link DominantOwner}: the bloc's id paired with
     * the two shades it paints in.
     *
     * <p>The bloc paints in a real faction's palette - itself for a lone faction bloc, the
     * alliance's dominant member for an alliance bloc - so resolving the colour faction here
     * keeps the bloc id, which for an alliance is not a faction id, out of the
     * {@code FactionAPI} lookup. The two palette slots are that faction's own authored UI
     * shades: the bright colour as primary and the dark colour as secondary, each specified
     * directly in the {@code .faction} file, so a map element pointed at either stays true to
     * the palette; which element uses which is the player's choice, made downstream in the
     * render layer.
     *
     * <p>Shared with the filter's presence resolver ({@link FilteredPolitics}), which reuses
     * the selected bloc's palette under its own synthetic key. Returns null when the colour
     * faction does not resolve, which drops the system as unowned.
     *
     * @param sector   the sector whose {@code FactionAPI} palette is read
     * @param grouping the grouping that names the bloc's colour faction
     * @param blocId   the bloc to colour, carried on the returned owner as its id
     * @return the render-ready owner, or null when the colour faction does not resolve
     */
    static DominantOwner resolveBlocOwner(
            SectorAPI sector, OwnershipGrouping grouping, String blocId) {
        var faction = sector.getFaction(grouping.resolveColorFactionId(blocId));
        if (faction == null) {
            return null;
        }
        return new DominantOwner(blocId, faction.getBrightUIColor(), faction.getDarkUIColor());
    }

    // Collapses the per-faction values into per-bloc values under the grouping: each faction's value
    // merges into its bloc's, so an alliance's members fold into one summed unit. Under the identity
    // grouping every faction is its own bloc and each value merges into the identity, leaving the
    // per-faction values unchanged, so the winning bloc equals today's winner. Generic over the
    // folded value so the dominance-only footprint regroup (the render's owner map, the filter's
    // presence resolver) and the picker's fuller footprint-plus-market-size regroup share one fold
    // rather than two copies of the same grouping idiom.
    static <T> Map<String, T> regroupByBloc(
            Map<String, T> valueByFactionId,
            OwnershipGrouping grouping,
            T identity,
            BinaryOperator<T> merge) {
        var valueByBlocId = new LinkedHashMap<String, T>();
        for (var entry : valueByFactionId.entrySet()) {
            var blocId = grouping.resolveBlocId(entry.getKey());
            valueByBlocId.put(blocId,
                    merge.apply(valueByBlocId.getOrDefault(blocId, identity), entry.getValue()));
        }
        return valueByBlocId;
    }

    // Folds one system into the running per-bloc stats: regroups the system's per-faction
    // contributions into per-bloc footprints and raw market sizes, resolves the one dominant bloc,
    // then adds a present-system entry to every bloc holding a market here - the dominant one also
    // taking a domination count. A bloc holding markets in several systems accumulates rather than
    // overwrites, and under an alliance grouping the members fold into the alliance's one bloc.
    private static void accumulateSystemStats(
            Map<String, BlocStats> statsByBlocId,
            SectorAPI sector,
            StarSystemAPI system,
            DominanceRules rules,
            boolean shouldIncludeUndiscoveredMarkets,
            OwnershipGrouping grouping) {

        // One regroup folds the footprint and the raw market size together (a bloc holding markets in
        // several systems, or an alliance's members, accumulates rather than overwrites), then the
        // dominance rule reads the footprint half of each bloc's folded contribution.
        var contributionByBlocId = regroupByBloc(
                KnownMarketFootprints.readContributionsByFaction(
                        sector, system, rules, shouldIncludeUndiscoveredMarkets),
                grouping,
                FactionMarketContribution.EMPTY,
                FactionMarketContribution::merge);

        // The one winner among the system's present blocs; null only when no bloc is present here,
        // in which case the loop below has nothing to fold and the system contributes no stats.
        // Ties resolve by market proximity, the same as the render pass, so a picker's domination
        // count matches the territory that actually paints.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
                extractFootprints(contributionByBlocId),
                MarketProximityTieBreak.forSystem(
                        sector,
                        system,
                        shouldIncludeUndiscoveredMarkets,
                        grouping));
        for (var entry : contributionByBlocId.entrySet()) {
            var blocId = entry.getKey();
            var stats = statsByBlocId.getOrDefault(blocId, BlocStats.EMPTY);
            statsByBlocId.put(
                    blocId,
                    stats.addSystem(
                            blocId.equals(dominantBlocId),
                            entry.getValue().footprint().totalWeight(),
                            entry.getValue().marketSize()));
        }
    }

    // The footprint half of each bloc's folded contribution, so the dominance rule - which ranks
    // footprints alone - reads them without the raw market size the stats pass also carries.
    private static Map<String, MarketFootprint> extractFootprints(
            Map<String, FactionMarketContribution> contributionByBlocId) {
        var footprintByBlocId = new LinkedHashMap<String, MarketFootprint>();
        for (var entry : contributionByBlocId.entrySet()) {
            footprintByBlocId.put(entry.getKey(), entry.getValue().footprint());
        }
        return footprintByBlocId;
    }
}
