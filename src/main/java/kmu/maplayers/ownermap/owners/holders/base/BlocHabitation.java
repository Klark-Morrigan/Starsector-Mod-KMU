package kmu.maplayers.ownermap.owners.holders.base;

import kmlib.starsector.markets.colonies.Colony;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * What one bloc lives on in one system: whose colonies, and their summed raw size.
 *
 * <p>One value rather than a map apiece, because the band reads the colonies and the picker reads
 * the size, and the two must name the same blocs. Held side by side in one entry, neither can list a
 * bloc the other left out.
 *
 * <p>Each colony is held as the faction owning it rather than as a count. Under a grouping, two
 * members of one bloc live in one entry, and a band colouring a segment by something the bloc does
 * not decide needs to tell their colonies apart.
 *
 * @param colonyOwnerIds the faction owning each colony the bloc lives on there, one entry per
 *                       colony, in faction ID order so one faction's colonies sit together
 * @param colonySize     those colonies' summed raw sizes
 */
public record BlocHabitation(
    List<String> colonyOwnerIds,
    int colonySize) {

    /** A bloc living on nothing, which is the fold's identity. */
    public static final BlocHabitation EMPTY = new BlocHabitation(List.of(), 0);

    /**
     * Takes the owners in faction ID order, as an immutable copy, so two folds of the same colonies
     * compare equal whichever member the walk reached first.
     */
    public BlocHabitation {

        colonyOwnerIds = colonyOwnerIds
            .stream()
            .sorted()
            .toList();
    }

    /**
     * @param colony a colony the bloc lives on; its owner must carry an ID, which every colony
     *               folded into a bloc does
     * @return this habitation with one more colony, of that colony's size and owner
     */
    public BlocHabitation addColony(Colony colony) {

        var ownerIds = new ArrayList<>(colonyOwnerIds);
        ownerIds.add(colony.readOwnerId());

        return new BlocHabitation(
            ownerIds,
            colonySize + colony.market().getSize());
    }

    /**
     * Merges two members' habitations into the bloc they fold into.
     *
     * @param other the other member's habitation
     * @return both habitations summed
     */
    public BlocHabitation addHabitation(BlocHabitation other) {

        return new BlocHabitation(
            Stream.concat(
                    colonyOwnerIds.stream(),
                    other.colonyOwnerIds.stream())
                .toList(),
            colonySize + other.colonySize);
    }
}
