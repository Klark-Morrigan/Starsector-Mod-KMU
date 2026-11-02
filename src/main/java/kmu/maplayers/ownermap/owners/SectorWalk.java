package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.systems.SectorPassIndex;

import kmu.maplayers.base.visibility.systems.MapVisibilityRules;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Supplier;

/**
 * One walk of the sector and the rules it is read under, shared by every stage of one rebuild or
 * one batch. The cell cut, the owners, the per-system answers and the band count each ask every
 * system what it holds, so one walk per system serves them all. A stage opening its own would walk
 * the sector again for answers this one already holds, since the sector cannot move within the
 * frame. It is what the tier hands a layer's owner source, so whatever reading the source opens
 * shares that walk.
 *
 * <p>The index names no owner rule and no owner - it is the sector's systems and what each
 * holds, walked once. The visibility rules beside it are the substrate's own: what the player may
 * be shown of what stands in a system, and whether a system is forced onto the map. They are the
 * rules the cells were cut under, carried here so a source reads the sector under the very rule
 * that decided which systems got cells - a source sampling the rules for itself could resolve
 * owners for a sector the standing geometry was not cut for.
 *
 * <p>It also keeps whatever readings a source opens over it, so the owners, the per-system answers
 * and the band count asked of one walk read one reading between them - and the source stays a
 * stateless set of rules, a reading living exactly as long as the walk it was opened over. That is
 * also the condition on sharing a walk: the bands are counted through the source the owners came
 * from, since a count under another sampling of the view's live inputs would plan against owners
 * the fills never drew.
 *
 * <p>Opened where a rebuild or a batch begins and discarded with it. A kept one would draw the
 * next rebuild off the sector this one saw, which is the change a rebuild exists to show. Not safe
 * for concurrent use, a rebuild being one thread's work.
 */
public final class SectorWalk {

    private final SectorPassIndex sectorIndex;
    private final MapVisibilityRules visibilityRules;

    // The readings opened over this walk, keyed by whoever opened them. By identity, because two
    // sources reading under different rules - two samplings of a live input - are two readings even
    // where their rules compare equal.
    private final Map<Object, Object> readingByOpener = new IdentityHashMap<>();

    /**
     * Refuses an unstated half rather than standing one in: both are values the opener already
     * holds, so a null is a fault at that one place, and a source handed the fog for a missing
     * rule would quietly draw less.
     *
     * @param sectorIndex     the one walk of each system every reader shares; an index over no
     *                        sector walks nothing
     * @param visibilityRules the rules the cells were cut under, which every read of the sector
     *                        through this walk is taken under
     */
    public SectorWalk(SectorPassIndex sectorIndex, MapVisibilityRules visibilityRules) {
        this.sectorIndex = Objects.requireNonNull(sectorIndex, "sectorIndex");
        this.visibilityRules = Objects.requireNonNull(visibilityRules, "visibilityRules");
    }

    /**
     * Opens a walk of one sector under the rules its cells were cut under, as a rebuild and a batch
     * each open one where they begin.
     *
     * <p>The index is made here rather than by each opener, so a walk is always over an index of its
     * own: one shared with a previous walk would answer from the sector that walk saw.
     *
     * @param sector          the sector to walk
     * @param visibilityRules the rules the cells were cut under
     * @return a walk over a fresh index of that sector
     */
    public static SectorWalk openOver(SectorAPI sector, MapVisibilityRules visibilityRules) {
        return new SectorWalk(new SectorPassIndex(sector), visibilityRules);
    }

    /**
     * @return the one walk of each system every reader shares
     */
    public SectorPassIndex sectorIndex() {
        return sectorIndex;
    }

    /**
     * @return the rules the cells were cut under
     */
    public MapVisibilityRules visibilityRules() {
        return visibilityRules;
    }

    /**
     * @return the sector this walk reads, as its index names it; null when opened over none
     */
    public SectorAPI sector() {
        return sectorIndex.getSector();
    }

    /**
     * The reading an opener has opened over this walk, opened on its first ask and kept for the
     * rest of the walk.
     *
     * @param <T>     the reading's type
     * @param opener  whoever the reading is opened for - a source asks with itself, so its reading is
     *                never another source's
     * @param type    the reading's type, which a reading kept under that opener must be
     * @param opening how to open the reading on the first ask
     * @return the opener's reading over this walk
     */
    public <T> T readReadingOpenedBy(Object opener, Class<T> type, Supplier<T> opening) {
        return type.cast(readingByOpener.computeIfAbsent(opener, absentOpener -> opening.get()));
    }
}
