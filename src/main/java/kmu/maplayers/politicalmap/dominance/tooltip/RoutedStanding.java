package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.politicalmap.dominance.standings.GroupStanding;
import kmu.maplayers.politicalmap.dominance.standings.StandingFraction;

import java.util.Map;
import java.util.Objects;

/**
 * One group as a block lists it: the group itself - holding the members on that block's side of it -
 * and how far the heading over it reaches, at both tiers.
 *
 * <p>A bloc its members do not agree about is listed under both headings rather than under either
 * one, each listing holding the members on its own side. What keeps neither heading overreaching is
 * the fraction stated on the row, so the bloc stays one named thing under both and nothing is
 * orphaned from it.
 *
 * <p>The two tiers count different things - a bloc's row counts its own membership, a faction's the
 * holder's - so a member's fraction cannot be read off the group's. They are held apart and keyed by
 * the faction each is about rather than laid out beside the members in order, so nothing can pair one
 * faction's row with another's count.
 *
 * @param standing        the group the block lists, holding the members on this block's side of it
 * @param fraction        how far this block's heading reaches over the group, or
 *                        {@link StandingFraction#NOTHING_TO_STATE} where nothing qualifies it
 * @param memberFractions how far it reaches over each member, keyed by faction ID; a member missing
 *                        from it states nothing
 */
public record RoutedStanding(
    GroupStanding standing,
    StandingFraction fraction,
    Map<String, StandingFraction> memberFractions) {

    public RoutedStanding {
        Objects.requireNonNull(standing, "standing");
        Objects.requireNonNull(fraction, "fraction");
        memberFractions = Map.copyOf(memberFractions);
    }

    /**
     * Lists a group under a heading true of the whole of it, which is what every block placed by
     * membership does: a row there states no fraction and neither does anything under it.
     *
     * <p>Named where the qualified case is not, because this is the one that fills absences in - a
     * block placed by disposition works both fractions out and states them outright.
     *
     * @param standing the group
     * @return the group listed with nothing qualifying it
     */
    public static RoutedStanding routeWhole(GroupStanding standing) {

        return new RoutedStanding(standing, StandingFraction.NOTHING_TO_STATE, Map.of());
    }

    /**
     * How far this block's heading reaches over one of the group's members.
     *
     * <p>Answered for every faction rather than only for those a fraction was worked out for, so a
     * row under a heading true of all of it asks the same question as any other and gets an answer
     * stating nothing.
     *
     * @param factionId the member being listed
     * @return its fraction, or {@link StandingFraction#NOTHING_TO_STATE} where it states none
     */
    public StandingFraction readFractionFor(String factionId) {

        return memberFractions.getOrDefault(factionId, StandingFraction.NOTHING_TO_STATE);
    }
}
