package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.systems.SystemColoniesIndex;

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

    private final ColonyKnowledge colonyKnowledge;
    private final SystemColoniesIndex colonies;
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
     * @param colonies     the pass's one walk of each system, which the habitation half is read
     *                     through; also the sector every read is made against
     * @param visibleStars the pass's hyperspace scan of which stars the vanilla map draws
     * @param rules        the rules in force for this pass - what may be shown of a colony, and
     *                     whether a system is forced onto the drawn set
     */
    public MapVisibilityPass(
            SystemColoniesIndex colonies,
            VisibleStars visibleStars,
            MapVisibilityRules rules) {

        this.colonies = Objects.requireNonNull(colonies, "colonies");
        this.visibleStars = Objects.requireNonNull(visibleStars, "visibleStars");
        this.rules = Objects.requireNonNull(rules, "rules");

        // The rule is paired with the sector's sighting register here, which is the one point at
        // which both are in hand: the index names the sector, and a projection asked of a colony
        // set later would have nowhere to read what has been observed from.
        this.colonyKnowledge = ColonyKnowledge.over(
            colonies.getSector(),
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
            new SystemColoniesIndex(sector),
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
     * The colony index this pass walks each system through.
     *
     * @return the index, so a reader needing something of its own off the same walk can open it
     */
    public SystemColoniesIndex colonies() {
        return colonies;
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
        return colonies.getSector();
    }

    /**
     * Whether the map draws one system - the drawn-set membership rule, applied over this pass's
     * own reading of the sector.
     *
     * <p>The one place that rule is answered, so the geometry sites, the motion tracker and the
     * fingerprint scan share it rather than each re-deriving it and drifting.
     *
     * @param system the system to test
     * @return true when the map draws the system under this pass's rules
     */
    public boolean isDrawn(StarSystemAPI system) {

        return MapVisibility.shouldAppearOnMap(
            system,
            visibleStars,
            isSystemInhabited(system),
            rules);
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
                .readInhabitingColonies(colonies.readColoniesIn(system))) {

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

        return colonyKnowledge.hasInhabitingColony(colonies.readColoniesIn(system));
    }
}
