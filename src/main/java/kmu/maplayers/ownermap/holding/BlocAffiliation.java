package kmu.maplayers.ownermap.holding;

import java.util.Objects;

/**
 * A grouping read as one question - do two blocs stand together - consulted only where a
 * contest is judged: how long a band's runs are laid, and which block of a hover box a group is
 * listed under.
 *
 * <p>A second axis beside the {@link HolderGrouping} a holder pass carries, because what fuses
 * into a cluster group and who stands together are different questions, and only the second decides a
 * contest. A layer can pin its pass grouping to identity so fills, runs, colours and rows stay per
 * faction - right for what it paints, and exactly why the contest it reports must read who stands
 * together through this type instead: judged on the paint grouping, two allies sharing a system
 * read as rivals fighting over it.
 *
 * <p>A wrapper rather than a second bare {@code HolderGrouping} so a paint grouping cannot be
 * handed where an affiliation is wanted: turning a grouping into an affiliation is an explicit act
 * at a binding point, and a holder of this type has the one predicate - never the fold, which
 * would merge allied runs, fills and rows and is the job of a layer whose paint grouping folds
 * the groups.
 */
public final class BlocAffiliation {

    /**
     * The affiliation under which no two blocs stand together - the identity grouping read as an
     * affiliation. The one value an install with nothing grouping factions and a caller with no
     * groups to judge by both use, so the absence of groups is a value rather than a null every
     * judging site would have to guard.
     */
    public static final BlocAffiliation NONE = new BlocAffiliation(HolderGrouping.identity());

    private final HolderGrouping affiliationGrouping;

    /**
     * Reads a grouping as who stands with whom. Meant for the binding point that samples the live
     * groups - a pass's paint grouping wrapped here would judge the contest by what fuses into
     * a cluster group, which is the confusion this type exists to make explicit.
     *
     * @param affiliationGrouping the grouping to read; {@link HolderGrouping#identity()} where
     *                            nothing groups factions, though {@link #NONE} already names that
     *                            value
     */
    public BlocAffiliation(HolderGrouping affiliationGrouping) {

        this.affiliationGrouping = Objects.requireNonNull(affiliationGrouping, "affiliationGrouping");
    }

    /**
     * Whether two distinct blocs stand together - both fold into one bloc under the affiliation
     * grouping.
     *
     * <p>A bloc ID the affiliation grouping names no faction for falls through to itself, which is
     * what keeps the rule uniform across layers with no per-layer branch: on a layer whose paint
     * grouping folds the same groups, the pass's bloc IDs are group IDs, members are already folded
     * into them, and so no two distinct blocs there ever stand together.
     *
     * @param firstBlocId  one bloc of the pair; a bloc with no ID stands with nothing, there being
     *                     nothing to have allied it
     * @param secondBlocId the other bloc of the pair
     * @return true when the two are distinct blocs folded into one under the affiliation grouping
     */
    public boolean areBlocsAllied(String firstBlocId, String secondBlocId) {

        // One bloc named twice is one side of a contest, not two standing together. The judging
        // sites ask "is this the painter" as a question of its own, so a self-affiliation here
        // would only blur that check.
        if (Objects.equals(firstBlocId, secondBlocId)) {
            return false;
        }
        var firstAffiliatedBlocId = affiliationGrouping.resolveBlocId(firstBlocId);

        // resolveBlocId answers null for an ID with no text, and two IDs that both resolved to
        // nothing must not read as standing together for it.
        return firstAffiliatedBlocId != null
            && firstAffiliatedBlocId.equals(affiliationGrouping.resolveBlocId(secondBlocId));
    }
}
