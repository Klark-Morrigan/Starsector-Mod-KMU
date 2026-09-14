package kmu.maplayers.base.visibility.systems;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SectorPassIndex;
import kmlib.starsector.systems.SystemKeyedMemo;

import kmu.maplayers.base.visibility.colonies.ColonyKind;
import kmu.maplayers.base.visibility.colonies.ColonyKnowledge;

import java.util.Objects;

/**
 * One reading of the sector, answering the two questions a map layer asks of a star
 * system: whether it is drawn, and whether anybody lives there.
 *
 * <p>Carries the three values {@link MapVisibility}'s membership rule is spent on - the
 * colony index, the hyperspace scan and the rules in force - because they are the inputs
 * to that one question rather than a bag a caller happens to hold. Handing them down
 * separately made every walk that wanted the answer restate the assembly, and made the
 * motion walk name a colony index it has no interest in beyond reaching the rule.
 *
 * <p>Several walks need the same drawn set: the geometry cache builds a cell per drawn
 * system, the motion tracker follows a drawn system that moves, and the political map's
 * fingerprint scan hashes it. Each takes a pass rather than the sector behind it, so a
 * caller running several walks in one tick pays for one reading per system between them -
 * a walk given the sector could open a reading of its own.
 *
 * <p>Every read goes through the index, which remembers each system's colonies for the
 * pass - so the pass is a snapshot of one moment, and is discarded with the tick that
 * opened it. A kept one would answer the next tick off the sector this one saw, which is
 * the change a poll exists to notice. Not safe for concurrent use, a tick being one
 * thread's work.
 *
 * <p>The sector is the index's rather than a field of its own, so a pass cannot be built
 * naming one sector while answering out of another.
 */
public final class MapVisibilityPass {

    // Several walks ask the drawn question of the same system in one tick, and the rule behind it
    // reads the sector directly - the system's gates, its jump points, its cut-off tag - where the
    // habitation half is already served off the index's own memo. Without this the second walk
    // pays for the first walk's answer again, per system, every rebuild.
    private final SystemKeyedMemo<Boolean> drawnBySystem = new SystemKeyedMemo<>();

    private final ColonyKnowledge colonyKnowledge;
    private final SectorPassIndex sectorIndex;
    private final VisibleStars visibleStars;
    private final MapVisibilityRules rules;

    /**
     * Opens a pass over an already-built reading of the sector.
     *
     * <p>Every part is required: a pass is opened where a rebuild or a poll begins, from values
     * the opener already holds, so a null is a fault at that one place rather than a caller with
     * nothing to state. Standing an empty scan in for a missing one would turn that fault into a
     * map that quietly draws less, which nothing on screen would report.
     *
     * @param sectorIndex  the pass's one reading of the sector, which the habitation half is read
     *                     through; also the sector every read is made against
     * @param visibleStars the pass's hyperspace scan of which stars the vanilla map draws
     * @param rules        the rules in force for this pass - what may be shown of a colony, and
     *                     whether a system is forced onto the drawn set
     */
    public MapVisibilityPass(
            SectorPassIndex sectorIndex,
            VisibleStars visibleStars,
            MapVisibilityRules rules) {

        this.sectorIndex = Objects.requireNonNull(sectorIndex, "sectorIndex");
        this.visibleStars = Objects.requireNonNull(visibleStars, "visibleStars");
        this.rules = Objects.requireNonNull(rules, "rules");

        // The rule is paired with the sector's sighting register here, which is the one point at
        // which both are in hand: the index names the sector, and a projection asked of a colony
        // set later would have nowhere to read what has been observed from.
        this.colonyKnowledge = ColonyKnowledge.over(
            sectorIndex.getSector(),
            rules.colonyVisibility());
    }

    /**
     * A pass over one sector under an explicit rule, opening the index and the hyperspace scan
     * its reads share.
     *
     * @param sector the sector this pass reads; null yields a pass answering an empty colony set
     *               and no visible star for every system, matching how the reads treat an
     *               unreachable sector
     * @param rules  the rules this pass answers under
     * @return a pass over that sector carrying those rules
     */
    public static MapVisibilityPass over(SectorAPI sector, MapVisibilityRules rules) {

        return new MapVisibilityPass(
            new SectorPassIndex(sector),
            VisibleStars.scan(sector),
            rules);
    }

    /**
     * A pass reading the player's live visibility settings: the rules are sampled once here so
     * the whole tick resolves under the settings in force when it began, even if the player
     * flips a toggle mid-walk. That is what keeps the walks sharing a pass from disagreeing -
     * one drawing a revealed system the next leaves off.
     *
     * @param sector the sector this pass reads
     * @return a pass over that sector carrying the live rules
     */
    public static MapVisibilityPass readFromLunaSettings(SectorAPI sector) {
        return over(sector, MapVisibilityRules.readFromLunaSettings());
    }

    /**
     * The reading of the sector this pass answers out of - its systems, and the colonies in each.
     *
     * @return the index, so a reader needing something of its own off the same walk can open it
     */
    public SectorPassIndex sectorIndex() {
        return sectorIndex;
    }

    /**
     * The rules this pass answers under.
     *
     * @return the colony rule and the force override every read through this pass takes
     */
    public MapVisibilityRules rules() {
        return rules;
    }

    /**
     * What the player may be told about the colonies this pass walks: its rule, read against the
     * sector's own record of what has been seen and where.
     *
     * <p>Published so a reader taking something of its own off the same walk - a fingerprint scan
     * folding market weights, say - projects the colonies exactly as this pass's own membership
     * answer did. A reader pairing the rule with a register for itself is a reader that can pair
     * them differently.
     *
     * @return the knowledge every colony projection through this pass is taken under
     */
    public ColonyKnowledge colonyKnowledge() {
        return colonyKnowledge;
    }

    /**
     * The sector this pass reads, as the index it walks names it.
     *
     * @return the sector; null when the pass was opened over none
     */
    public SectorAPI sector() {
        return sectorIndex.getSector();
    }

    /**
     * Whether the map draws one system - the drawn-set membership rule, applied over this pass's
     * own reading of the sector.
     *
     * <p>The one place that rule is answered, so the geometry sites, the motion tracker and the
     * fingerprint scan share it rather than each re-deriving it and drifting. Remembered per
     * system for the life of the pass, so sharing it costs one reading between them rather than
     * one each.
     *
     * @param system the system to test
     * @return true when the map draws the system under this pass's rules
     */
    public boolean isDrawn(StarSystemAPI system) {

        if (system == null) {
            // Nothing to remember it under, and the rule already states its own answer for no
            // system - a forced pass draws one, anything else does not.
            return resolveIsDrawn(null);
        }
        return drawnBySystem.readValueFor(system, this::resolveIsDrawn);
    }

    /**
     * Whether the map shows one system only because hidden systems are being shown - a system with
     * no access the player can see and nobody living in it.
     *
     * <p>Asked where two systems compete for something only one of them can have, the partition's
     * shared hyperspace point among them. A hidden system is one the player has not been shown and
     * stops being shown the moment the override goes off, so letting it take anything from a system
     * the map keeps either way would make what the settled system is drawn as depend on a toggle
     * about something else.
     *
     * <p>Not remembered per system the way {@link #isDrawn} is. Its callers ask it of the few
     * systems that collide over one thing rather than of every system in the sector, so a memo
     * would cost a map to save a read nothing repeats.
     *
     * @param system the system to test; null reads as hidden, there being no system to show
     * @return true when the system is on this pass's map by the override alone
     */
    public boolean isHiddenSystem(StarSystemAPI system) {

        return !MapVisibility.hasOwnPlaceOnMap(
            system,
            visibleStars,
            isSystemInhabited(system));
    }

    /**
     * Whether the system holds a collapsed colony the player may be shown.
     *
     * <p>Published rather than kept private because inhabitation folds it in and cannot report
     * it, and a reader hashing the drawn set needs it on its own - a live-to-dead flip has to
     * move that hash without the drawn set changing.
     *
     * <p>Read off the pass's own colony walk rather than off the system's planets, which is what
     * makes it the same answer the cell and the box beside it are drawn from. A collapse is a kind of
     * colony, so the set already knows both that it is one and whether the player has surveyed it
     * closely enough to be told - and a planet walk of its own would be a second reading of both,
     * free to disagree with the one the map is painted by.
     *
     * @param system the system to read; null yields false
     * @return true when the system's colonies include a collapsed one this pass's rule admits
     */
    public boolean isRevealedDecivilised(StarSystemAPI system) {

        for (var colony : colonyKnowledge
                .readInhabitingColonies(sectorIndex.readColoniesIn(system))) {

            if (colonyKnowledge.readKindOf(colony) == ColonyKind.UNGOVERNED_COLONY) {
                return true;
            }
        }
        return false;
    }

    /**
     * Whether anybody lives in one system, or did, off this pass's own reading of it.
     *
     * <p>The habitation projection is the whole of the answer, a collapsed colony being one of the
     * colonies it admits - so there is nothing to compose here beyond choosing the projection,
     * and no second reading of who is present for the box over the same cell to disagree with.
     *
     * <p>Only the colony half of the rules is read here, since forcing a system onto the map does
     * not make it inhabited.
     *
     * @param system the system to read; null yields false
     * @return true when the system holds a colony somebody lives on or a known collapsed one
     */
    public boolean isSystemInhabited(StarSystemAPI system) {

        return colonyKnowledge.hasInhabitingColony(sectorIndex.readColoniesIn(system));
    }

    // The rule itself, applied over this pass's own values. Split out so the memo has something to
    // call on a miss and the null case has the same answer to fall back on.
    private boolean resolveIsDrawn(StarSystemAPI system) {

        return MapVisibility.shouldAppearOnMap(
            system,
            visibleStars,
            isSystemInhabited(system),
            rules);
    }
}
