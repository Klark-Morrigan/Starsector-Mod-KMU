package kmu.maplayers.base.visibility;

import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;

import kmlib.starsector.map.VisibleStars;
import kmlib.starsector.markets.DecivilisedMarkets;
import kmlib.starsector.systems.SystemColoniesIndex;
import kmlib.text.KmlibStrings;

import java.util.HashMap;
import java.util.Map;
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
 * <p>A class rather than a record because it remembers as well as carries. Both the memo
 * and the index are a snapshot of one moment, which is why a pass is discarded with the
 * tick that opened it - a kept one would answer the next tick off the sector this one saw,
 * which is the change a poll exists to notice. Not safe for concurrent use, a tick being
 * one thread's work.
 *
 * <p>The sector is the index's rather than a field of its own, so a pass cannot be built
 * naming one sector while answering out of another.
 */
public final class MapVisibilityPass {

    // Each system's revealed-ruin answer, resolved on first ask and remembered for the rest of
    // the pass. The one read beneath a pass that has no memo of its own: the colony walk is
    // memoised in the index, while this walks every planet in the system afresh each time.
    //
    // It is asked of the same system more than once per tick - the fingerprint scan needs it to
    // salt a drawn system's contribution, and both that scan and the motion walk reach it again
    // through the inhabitation read.
    //
    // Keyed by system id on the same terms the colony index is, and for the same reason: an
    // unkeyable system is resolved afresh rather than pooled with every other under a shared key.
    private final Map<String, Boolean> ruinBySystemId = new HashMap<>();

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
     * Whether the system holds a dead colony the player has already seen.
     *
     * <p>Published rather than kept private because inhabitation folds it in and cannot report
     * it, and a reader hashing the drawn set needs it on its own - a live-to-dead flip has to
     * move that hash without the drawn set changing. Asking here rather than reading the planets
     * again is what keeps the ruin walk to one per system per pass.
     *
     * @param system the system to read; null yields false
     * @return true when a planet there carries a revealed decivilised condition
     */
    public boolean isRevealedDecivilised(StarSystemAPI system) {

        if (system == null) {
            return DecivilisedMarkets.hasRevealedDecivilisedPlanet(null);
        }
        var systemId = system.getId();

        if (!KmlibStrings.hasText(systemId)) {
            // Nothing to key the memo on. Reading afresh costs a planet walk a later ask would
            // have saved, which is the honest price of an unkeyable system - pooling every one of
            // them under a shared key would hand one system's ruins to another.
            return DecivilisedMarkets.hasRevealedDecivilisedPlanet(system);
        }
        return ruinBySystemId.computeIfAbsent(
            systemId,
            id -> DecivilisedMarkets.hasRevealedDecivilisedPlanet(system));
    }

    /**
     * Whether anybody lives in one system, composed off this pass's own reading of it.
     *
     * <p>{@link MapVisibility} owns what inhabitation <em>is</em> and takes the two answers rather
     * than the walks that produce them - which is what keeps a second walk of every system out of
     * a membership test - so somewhere has to make those two reads, and a pass holding the index
     * and the ruin memo is that place.
     *
     * <p>Only the colony half of the rules is read here, since forcing a system onto the map does
     * not make it inhabited.
     *
     * @param system the system to read; null yields false
     * @return true when the system holds a colony somebody lives on or a known dead colony
     */
    public boolean isSystemInhabited(StarSystemAPI system) {

        return MapVisibility.isInhabited(
            colonies
                .readColoniesIn(system)
                .hasInhabitingColony(rules.colonyVisibility()),
            isRevealedDecivilised(system));
    }
}
