package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import java.util.Objects;

/**
 * One rebuild's walk of the sector and the rules it is read under: what the tier hands a
 * layer's owner source, so whatever reading the source opens shares the walk every other stage
 * of the rebuild is answered from.
 *
 * <p>The index names no colony rule and no owner - it is the sector's systems and what each
 * holds, walked once. The visibility rules beside it are the substrate's own: what the player may
 * be shown of a colony, and whether a system is forced onto the map. They are the rules the cells
 * were cut under, carried here so a source reading colonies reads them under the very rule that
 * decided which systems got cells - a source sampling the rules for itself could resolve owners
 * for a sector the standing geometry was not cut for.
 *
 * <p>Opened where a rebuild or a batch begins and discarded with it. A kept one would draw the
 * next rebuild off the sector this one saw, which is the change a rebuild exists to show.
 *
 * @param sectorIndex     the one walk of each system every reader shares; an index over no
 *                        sector walks nothing
 * @param visibilityRules the rules the cells were cut under, which every colony read through
 *                        this walk is taken under
 */
public record SectorWalk(
    SectorPassIndex sectorIndex,
    MapVisibilityRules visibilityRules) {

    /**
     * Refuses an unstated half rather than standing one in: both are values the opener already
     * holds, so a null is a fault at that one place, and a source handed the fog for a missing
     * rule would quietly draw less.
     */
    public SectorWalk {
        Objects.requireNonNull(sectorIndex, "sectorIndex");
        Objects.requireNonNull(visibilityRules, "visibilityRules");
    }

    /**
     * @return the sector this walk reads, as its index names it; null when opened over none
     */
    public SectorAPI sector() {
        return sectorIndex.getSector();
    }
}
