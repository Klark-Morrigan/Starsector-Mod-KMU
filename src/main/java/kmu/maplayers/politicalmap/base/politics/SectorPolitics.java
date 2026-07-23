package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.BinaryOperator;

/**
 * Resolves which faction owns each star system and in which colours that owner
 * paints.
 *
 * <p>The dominance-and-palette half of the ownership pipeline: it takes the known
 * market footprints {@link KnownMarketFootprints} reads from the economy, asks
 * {@link SystemDominance} which faction holds the system, and resolves that
 * winner's authored UI shades into a {@link DominantOwner} the render layer draws.
 * Confining the winning owner's {@code FactionAPI} palette lookup here keeps the
 * render layer clear of Starsector economy and faction types.
 *
 * <p>The rule, dev reveal, and grouping a pass resolves under travel together as a
 * {@link DominancePass}: the live entry points read the player's settings into one,
 * and every explicit caller hands its own down, so a whole pass resolves under one
 * consistent set of knobs.
 */
public final class SectorPolitics {

    private SectorPolitics() {
    }

    /**
     * Builds the dominant owner - faction id and draw colour - for every inhabited
     * star system, under the player's live settings and the faction (identity) grouping.
     *
     * <p>The render-ready output of the ownership pipeline: resolving the faction
     * and its palette here confines {@code FactionAPI} access to this adapter, so
     * the render layer consumes a plain {@link DominantOwner} and never reaches
     * into the economy. Both facts come from one dominance pass, and the id is
     * kept beside the colour so per-owner styling - and later per-owner behaviour -
     * reads the same winner the fill was decided by.
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
     * grouping and the player's live settings.
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
                sector, DominancePass.readFromLunaSettings(grouping));
    }

    /**
     * Builds the dominant owner for every inhabited star system under an explicit
     * dominance pass, for a caller that has already sampled the player's settings.
     *
     * @param sector the sector whose economy is read; null yields an empty map
     * @param pass   the rule, dev reveal, and grouping this pass resolves under
     * @return the dominant owner keyed by system id; a system with no folded
     *         markets is absent from the map (uninhabited)
     */
    public static Map<String, DominantOwner> resolveDominantOwnerBySystemId(
            SectorAPI sector, DominancePass pass) {
        var ownerBySystemId = new LinkedHashMap<String, DominantOwner>();
        if (sector == null) {
            return ownerBySystemId;
        }
        for (var system : sector.getStarSystems()) {
            var owner = resolveDominantOwner(sector, system, pass);
            if (owner != null) {
                ownerBySystemId.put(system.getId(), owner);
            }
        }
        return ownerBySystemId;
    }

    /**
     * Resolves the dominant owner of one star system under the player's live settings
     * and the faction (identity) grouping - the same result the bulk pass would put
     * under this system's id, computed for it alone.
     *
     * <p>The single-system entry point the incremental refresh path leans on:
     * when one colony's size changes, only that system's ownership can shift, so
     * only it is re-derived rather than re-walking the whole economy. Shares the
     * footprint, dominance rule, and palette lookup with
     * {@link #resolveDominantOwnerBySystemId}, so a system resolves the same
     * winner and colours whether it is refreshed alone or in the full pass.
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
     * Resolves the dominant owner of one star system under an explicit grouping and
     * the player's live settings.
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
            SectorAPI sector, StarSystemAPI system, OwnershipGrouping grouping) {
        return resolveDominantOwner(sector, system, DominancePass.readFromLunaSettings(grouping));
    }

    /**
     * Resolves the dominant owner of one star system under an explicit dominance pass.
     *
     * <p>The core of the ownership pipeline: it reads each bloc's footprint under the
     * pass's grouping (a no-op fold under the identity grouping, a member-summing merge
     * under an alliance grouping), ranks the blocs, then colours the winning bloc through
     * the faction the grouping names for its palette. Under identity the bloc id is the
     * faction id and its colour faction is itself, so the result is the plain faction owner.
     *
     * @param sector the sector whose economy is read; null (or a null economy) yields null
     * @param system the system to resolve; null yields null
     * @param pass   the rule, dev reveal, and grouping this pass resolves under
     * @return the dominant owner, or null when the system holds no folded market
     *         (uninhabited)
     */
    public static DominantOwner resolveDominantOwner(
            SectorAPI sector, StarSystemAPI system, DominancePass pass) {
        if (sector == null || system == null || sector.getEconomy() == null) {
            return null;
        }
        var footprintByBlocId = pass.readBlocFootprints(sector, system);
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
                footprintByBlocId, pass.tieBreakFor(sector, system));
        if (dominantBlocId == null) {
            return null;
        }
        return resolveBlocOwner(sector, pass.grouping(), dominantBlocId);
    }

    /**
     * The whole-sector {@link BlocStats} for every bloc holding a visible market somewhere, under a
     * pass - the filter picker's selectable set (a bloc present here has presence of at least one,
     * which is the {@code presence > 0} gate) paired with the four numbers the picker sorts and
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
     * @param sector the sector whose economy is read; null (or a null economy) yields an empty map
     * @param pass   the rule, dev reveal, and grouping this read resolves under, sampled once by the
     *               caller so the whole read resolves under one set of knobs
     * @return each present bloc's stats, keyed by bloc id in economy-walk order; empty when no bloc
     *         holds a visible market
     */
    public static Map<String, BlocStats> aggregateBlocStats(SectorAPI sector, DominancePass pass) {
        var statsByBlocId = new LinkedHashMap<String, BlocStats>();
        if (sector == null || sector.getEconomy() == null) {
            return statsByBlocId;
        }
        for (var system : sector.getStarSystems()) {
            accumulateSystemStats(statsByBlocId, sector, system, pass);
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
            DominancePass pass) {

        // One regroup folds the footprint and the raw market size together (a bloc holding markets in
        // several systems, or an alliance's members, accumulates rather than overwrites), then the
        // dominance rule reads the footprint half of each bloc's folded contribution.
        var contributionByBlocId = regroupByBloc(
                KnownMarketFootprints.readContributionsByFaction(
                        sector, system, pass.rules(), pass.shouldIncludeUndiscoveredMarkets()),
                pass.grouping(),
                FactionMarketContribution.EMPTY,
                FactionMarketContribution::merge);

        // The one winner among the system's present blocs; null only when no bloc is present here,
        // in which case the loop below has nothing to fold and the system contributes no stats.
        // Ties resolve by market proximity, the same as the render pass, so a picker's domination
        // count matches the territory that actually paints.
        var dominantBlocId = SystemDominance.resolveDominantFactionId(
                extractFootprints(contributionByBlocId),
                pass.tieBreakFor(sector, system));
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
