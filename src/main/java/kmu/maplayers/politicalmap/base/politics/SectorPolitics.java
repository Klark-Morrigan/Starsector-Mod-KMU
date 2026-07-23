package kmu.maplayers.politicalmap.base.politics;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import java.util.LinkedHashMap;
import java.util.Map;

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
 * <p>Resolves one render owner per system; the picker's whole-sector bloc totals are
 * {@link BlocStatsAggregator}'s job, walking the same economy through the same
 * {@link DominancePass} so the two never drift on which blocs hold territory.
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
}
