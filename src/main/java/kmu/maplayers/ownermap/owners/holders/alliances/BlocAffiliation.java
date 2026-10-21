package kmu.maplayers.ownermap.owners.holders.alliances;

import kmlib.starsector.factions.alliances.FactionAlliances;

import kmu.maplayers.ownermap.owners.OwnerAffiliation;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;

import java.util.Objects;

/**
 * The alliances read as one question - do two blocs stand together - consulted only where a
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
 * <p>Built over the {@link FactionAlliances} memberships rather than a grouping, so a paint grouping
 * cannot be handed where an affiliation is wanted, and a holder of this type has the one predicate -
 * never the fold, which would merge allied runs, fills and rows and is the job of a layer whose paint
 * grouping folds the groups.
 *
 * <p>It is the tier's {@link OwnerAffiliation} for a layer painting holders, which is how a band
 * judges its contest without the tier knowing what a bloc is.
 */
public final class BlocAffiliation
        implements OwnerAffiliation {

    private final FactionAlliances alliances;

    /**
     * Reads the alliances as who stands with whom.
     *
     * @param alliances the memberships to read; {@link FactionAlliances#NONE} where nothing allies
     *                  factions, under which no two blocs stand together
     */
    public BlocAffiliation(FactionAlliances alliances) {

        this.alliances = Objects.requireNonNull(alliances, "alliances");
    }

    /**
     * Whether two distinct blocs stand together - both are factions of one alliance. A layer painting
     * holders paints blocs, so an owner ID is a bloc ID.
     *
     * @param firstBlocId  one bloc of the pair; a bloc with no ID stands with nothing, there being
     *                     nothing to have allied it
     * @param secondBlocId the other bloc of the pair
     * @return true when the two are distinct factions of one alliance
     */
    @Override
    public boolean areOwnersAllied(String firstBlocId, String secondBlocId) {

        // One bloc named twice is one side of a contest, not two standing together. The judging
        // sites ask "is this the painter" as a question of its own, so a self-affiliation here
        // would only blur that check.
        if (Objects.equals(firstBlocId, secondBlocId)) {
            return false;
        }

        // A group ID names no faction, so it stands with nothing. That is what keeps the rule uniform
        // across layers with no per-layer branch: on a layer whose paint grouping folds the alliances,
        // the pass's bloc IDs are group IDs, members are already folded into them, and so no two
        // distinct blocs there ever stand together.
        return alliances.areFactionsAllied(firstBlocId, secondBlocId);
    }
}
