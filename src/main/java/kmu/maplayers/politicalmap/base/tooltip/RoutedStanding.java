package kmu.maplayers.politicalmap.base.tooltip;

import kmu.maplayers.politicalmap.base.dominance.FactionStanding;
import kmu.maplayers.politicalmap.base.dominance.GroupStanding;

import java.util.List;
import java.util.Objects;

/**
 * One group as a block lists it: the standing itself, and the bloc it was taken out of where the
 * routing broke one up.
 *
 * <p>A bloc whose members do not all stand the same way toward the holder cannot be listed whole
 * under any one heading, so each of its present members is listed on its own - and the bloc it came
 * out of has to travel with it, or the listing loses the very grouping the map paints that territory
 * by. Carried as the bloc's id rather than as its name and crest, so whatever names a bloc reads
 * them off the one grouping the ranking was taken under instead of being handed a second copy free
 * to disagree with it.
 *
 * @param standing       the group the block lists - a bloc as it ranked, or the single faction a
 *                       broken-up bloc left standing alone
 * @param allianceBlocId the bloc this standing was taken out of, or null where the group ranked and
 *                       routed whole, which is every group on every block but the two disposition
 *                       sorts
 */
public record RoutedStanding(
    GroupStanding standing,
    String allianceBlocId) {

    // What a group that was never broken up carries where the bloc it came out of would be. Named
    // rather than passed as a bare null, so the factory below says the group ranked as itself
    // instead of handing the constructor an unexplained absence.
    private static final String NO_ALLIANCE = null;

    public RoutedStanding {
        Objects.requireNonNull(standing, "standing");
    }

    /**
     * Lists a group as it ranked, which is what every block but the two placed by disposition does
     * and what those two do with a bloc its members agree about.
     *
     * @param standing the group
     * @return the group listed as itself
     */
    public static RoutedStanding routeWhole(GroupStanding standing) {
        return new RoutedStanding(standing, NO_ALLIANCE);
    }

    /**
     * Lists one member of a bloc the routing broke up, as the lone faction it now stands as, still
     * stating the bloc it belongs to.
     *
     * <p>The member's own standing is carried through rather than restated, so the faction is listed
     * at exactly what the pass weighed it - and a member it weighed nothing for keeps that reading,
     * which is what draws its nought quietly wherever the row is finally laid.
     *
     * @param member         the member being listed on its own
     * @param allianceBlocId the bloc it was taken out of
     * @return that member as a group of one
     */
    public static RoutedStanding dissolveFrom(FactionStanding member, String allianceBlocId) {

        return new RoutedStanding(
            new GroupStanding(member.factionId(), member.score(), List.of(member)),
            allianceBlocId);
    }

    /**
     * Whether this standing is one member of a bloc the routing broke up rather than a group that
     * ranked whole. The one place that reading is judged, so nothing goes looking for the name of a
     * bloc a group was never taken out of.
     *
     * @return true where the standing states the bloc it came from
     */
    public boolean isDissolvedFromAlliance() {
        return allianceBlocId != null;
    }
}
