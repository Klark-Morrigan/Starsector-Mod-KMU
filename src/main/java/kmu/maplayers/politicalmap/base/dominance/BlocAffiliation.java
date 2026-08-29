package kmu.maplayers.politicalmap.base.dominance;

import java.util.Objects;

/**
 * The alliance set read as one question - do two blocs stand together - consulted only where a
 * contest is judged: how long a band's runs are laid, and which block of a hover box a group is
 * listed under.
 *
 * <p>A second axis beside the {@link HolderGrouping} a holder pass carries, because what fuses
 * into a territory and who stands together are different questions, and only the second decides a
 * contest. The faction and claims layers pin their pass grouping to identity so fills, runs,
 * colours and rows stay per faction - right for what they paint, and exactly why the contest they
 * report must read the alliance set through this type instead: judged on the paint grouping, two
 * allies sharing a system read as rivals fighting over it.
 *
 * <p>A wrapper rather than a second bare {@code HolderGrouping} so a paint grouping cannot be
 * handed where an affiliation is wanted: turning a grouping into an affiliation is an explicit act
 * at a binding point, and a holder of this type has the one predicate - never the fold, which
 * would merge allied runs, fills and rows and is the alliances layer's job alone.
 */
public final class BlocAffiliation {

    /**
     * The affiliation under which no two blocs stand together - the identity grouping read as an
     * alliance set. The one value an install with nothing grouping factions and a caller posing a
     * case without an alliance set both use, so the absence of alliances is a value rather than a
     * null every judging site would have to guard.
     */
    public static final BlocAffiliation NONE = new BlocAffiliation(HolderGrouping.identity());

    private final HolderGrouping allianceSet;

    /**
     * Reads a grouping as an alliance set. Meant for the binding point that samples the live
     * alliances - a pass's paint grouping wrapped here would judge the contest by what fuses into
     * a territory, which is the confusion this type exists to make explicit.
     *
     * @param allianceSet the grouping to read; {@link HolderGrouping#identity()} where nothing
     *                    groups factions, though {@link #NONE} already names that case
     */
    public BlocAffiliation(HolderGrouping allianceSet) {

        this.allianceSet = Objects.requireNonNull(allianceSet, "allianceSet");
    }

    /**
     * Whether two distinct blocs stand together - both fold into one bloc under the alliance set.
     *
     * <p>A bloc id the alliance set names no faction for falls through to itself, which is what
     * keeps the rule uniform across the layers with no per-layer branch: on the alliances layer
     * the pass's bloc ids are alliance ids, members are already folded into them, and so no two
     * distinct blocs there ever stand together.
     *
     * @param firstBlocId  one bloc of the pair; a bloc with no id stands with nothing, there being
     *                     nothing to have allied it
     * @param secondBlocId the other bloc of the pair
     * @return true when the two are distinct blocs folded into one under the alliance set
     */
    public boolean areBlocsAllied(String firstBlocId, String secondBlocId) {

        // One bloc named twice is one side of a contest, not two standing together. The judging
        // sites ask "is this the painter" as a question of its own, so a self-affiliation here
        // would only blur that check.
        if (Objects.equals(firstBlocId, secondBlocId)) {
            return false;
        }
        var firstAllianceBlocId = allianceSet.resolveBlocId(firstBlocId);

        // resolveBlocId answers null for an id with no text, and two ids that both resolved to
        // nothing must not read as standing together for it.
        return firstAllianceBlocId != null
            && firstAllianceBlocId.equals(allianceSet.resolveBlocId(secondBlocId));
    }
}
