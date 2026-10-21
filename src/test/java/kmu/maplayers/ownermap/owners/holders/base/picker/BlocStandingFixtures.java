package kmu.maplayers.ownermap.owners.holders.base.picker;

import com.fs.starfarer.api.campaign.RepLevel;

import kmlib.starsector.factions.relation.FactionRelation;

import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import java.awt.Color;
import java.util.Map;

/**
 * The standings and the group the picker's standing suites pose their blocs with.
 *
 * <p>Shared because the reader, the sort mode and the assembly binding it all read one bloc's range
 * back, and a range is only checkable against the members' own numbers and shades. Held per suite,
 * the three could each pose a different "hostile" and go on passing.
 */
final class BlocStandingFixtures {

    /** The shade a friendly relation resolves to. */
    static final Color GREEN = new Color(60, 180, 60);

    /**
     * The shade a hostile relation resolves to. Distinct from {@link #GREEN}, so an end carrying the
     * wrong member's standing shows as a colour rather than only as a number.
     */
    static final Color RED = new Color(200, 50, 50);

    /** A member warm towards the player, above the scale's centre. */
    static final FactionRelation FRIENDLY = new FactionRelation(RepLevel.FRIENDLY, 60, GREEN);

    /** A member set against the player, below the scale's centre. */
    static final FactionRelation HOSTILE = new FactionRelation(RepLevel.HOSTILE, -40, RED);

    /**
     * The Hegemony and Tri-Tachyon folded into the bloc {@code "pact"}. A group of two is the
     * smallest grouping in which a bloc's membership is anything other than the bloc's own ID, so a
     * standing read against it can only come from the grouping the read was handed.
     */
    static final HolderGrouping PACT_GROUPING = new HolderGrouping(
        Map.of("hegemony", "pact", "tritachyon", "pact"),
        Map.of("pact", "hegemony"),
        Map.of("pact", "Persean Pact"));

    private BlocStandingFixtures() {
    }
}
