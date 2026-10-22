package kmu.maplayers.ownermap.ribbon;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;

import java.awt.Color;
import java.util.Optional;
import java.util.Set;

/**
 * The owner-generic values every band suite is posed against: the owners, the shades they draw in,
 * the design's own proportions, and who stands with whom.
 *
 * <p>Shared because the core's own rule suite and every suite reading a band back through a layer's
 * owners pose the same map and read the same runs back. Held apart, an owner could draw one colour
 * in the rule's suite and another in a layer's, and two suites could disagree about what "the
 * design's proportions" are while both passed.
 *
 * <p>The colours are exported for a case to assert with, since what a case reads back is a run's
 * colour. The widths are not.
 */
public final class OwnerRibbonRunsFixtures {

    public static final String HEGEMONY = "hegemony";
    public static final String TRITACHYON = "tritachyon";
    public static final String DIKTAT = "sindria";

    public static final Color HEGEMONY_BRIGHT = new Color(140, 160, 220);
    public static final Color HEGEMONY_DARK = new Color(40, 60, 120);
    public static final Color TRITACHYON_BRIGHT = new Color(120, 220, 200);
    public static final Color TRITACHYON_DARK = new Color(20, 90, 80);
    public static final Color DIKTAT_BRIGHT = new Color(220, 150, 120);
    public static final Color DIKTAT_DARK = new Color(90, 50, 40);

    // The shortening switch's two answers, named so each rules value below reads which it poses.
    private static final boolean UNCONTESTED_RUNS_SHORTENED = true;
    private static final boolean UNCONTESTED_RUNS_AUTHORED = false;

    /**
     * The design's own proportions - a stake three widths long, parted by one width - paired with
     * the uncontested shortening switched off, so every cell's runs come out at those same lengths
     * whether a rival is in it or not. What the shortening does to them is the plan rules' own
     * suite's.
     *
     * <p>The widths are not exported for a case to assert with. A case states the run lengths it
     * expects as its own literals, so an edit to these proportions fails the suites that read a
     * band back rather than moving their expectations along with it.
     */
    public static final RibbonPlanRules STANDARD_RULES =
        RibbonPlanRules.createFromAuthoredLengths(
            new RibbonSegmentLengths(3, 1),
            UNCONTESTED_RUNS_AUTHORED);

    /**
     * The same proportions with the shortening switched on, which is the shipped answer and the
     * only witness to which reading a cell fell under: a cell nobody contests draws each stake one
     * width long, where a contested one keeps the authored run. A suite reaching for these is posing
     * that question and nothing else.
     *
     * <p>The proportions come off the rules above rather than being restated, so the two cannot
     * drift into disagreeing about what an unshortened run is.
     */
    public static final RibbonPlanRules SHORTENED_UNCONTESTED_RULES =
        RibbonPlanRules.createFromAuthoredLengths(
            STANDARD_RULES.getContestedLengths(),
            UNCONTESTED_RUNS_SHORTENED);

    // The two owners standing together under the affiliation below.
    private static final Set<String> ALLIED_PAIR = Set.of(HEGEMONY, TRITACHYON);

    /**
     * The affiliation every case about who counts as a rival is judged against: the Hegemony and
     * Tri-Tachyon standing together, with every other owner outside it, so one value poses the
     * ally, the rival and the painter's own owner at once.
     */
    public static final OwnerAffiliation ALLIED_HEGEMONY_AND_TRITACHYON =
        (firstOwnerId, secondOwnerId) -> !firstOwnerId.equals(secondOwnerId)
            && ALLIED_PAIR.contains(firstOwnerId)
            && ALLIED_PAIR.contains(secondOwnerId);

    /**
     * A cell no owner's fill covers. There is no painter for an owner to be a rival of, so what
     * makes such a cell contested is how many owners are in it.
     */
    public static final Optional<String> NO_PAINTER = Optional.empty();

    private OwnerRibbonRunsFixtures() {
    }
}
