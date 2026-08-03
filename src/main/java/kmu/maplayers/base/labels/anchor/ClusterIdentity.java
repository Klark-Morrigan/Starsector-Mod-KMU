package kmu.maplayers.base.labels.anchor;

import java.util.Set;

/**
 * Which cluster a label placement was fitted to: the opaque owner key the cluster's members
 * all share, and the exact systems whose cells bounded the search. Two placements describe
 * the same cluster exactly when their identities are equal, which is the whole question a
 * standing anchor has to answer about a fresh rebuild's clusters.
 *
 * <p>The members are a {@link Set} rather than the ordered list the search is handed so that
 * equality answers that question and nothing else. Which order a cluster's members were
 * walked in is an accident of how the grouping was traversed and must not read as a
 * different cluster; membership itself is not an accident, so a cluster that split or merged
 * carries a member set matching nothing it came from - the two cases that would otherwise
 * need spotting by hand fall out of equality instead.
 *
 * @param ownerKey        the key every member of the cluster shares, opaque here
 * @param memberSystemIds the cluster's member systems, order-free
 */
public record ClusterIdentity(
    String ownerKey,
    Set<String> memberSystemIds) {

    /**
     * Takes an unmodifiable copy of the members, so an identity cannot be altered through
     * the collection it was built from - it is used as a map key, where a set that shifted
     * underneath would lose the placement filed under it rather than fail loudly.
     */
    public ClusterIdentity {
        memberSystemIds = Set.copyOf(memberSystemIds);
    }
}
