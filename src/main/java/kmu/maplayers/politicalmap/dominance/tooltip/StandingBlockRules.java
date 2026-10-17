package kmu.maplayers.politicalmap.dominance.tooltip;

import kmu.maplayers.ownermap.owners.holders.alliances.BlocAffiliation;
import kmu.maplayers.ownermap.owners.holders.alliances.BlocRelations;

import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * The rules a box's blocks are placed by: who is in the running for the system at all, how a bloc
 * stands with the holder, and what a bloc is made of.
 *
 * <p>Bundled rather than threaded loose because all of them have to come off the one pass. A routing
 * handed relations sampled over one grouping and a membership from another would answer "who is in
 * this bloc" about blocs the ranking never named. Passed as loose arguments that is a mistake nobody
 * would notice at the call site; passed as one value it cannot be written.
 *
 * <p>Plain data with no Starsector types, so a rule taking these stays free of the live economy:
 * what bars a bloc and what a bloc is made of arrive as functions over IDs, and where either comes
 * from is the caller's business.
 *
 * @param candidacy      which blocs take part in the contest at all; the rest are set aside before a
 *                       holder is picked
 * @param blocRelations  how a bloc stands with the holder, by alliance and in disposition
 * @param blocMembership what a bloc is made of, keyed by bloc ID. A reader rather than the grouping
 *                       itself, because a grouping sat beside a {@link BlocAffiliation} is the very
 *                       confusion that wrapper exists to make impossible
 */
public record StandingBlockRules(
    Predicate<String> candidacy,
    BlocRelations blocRelations,
    Function<String, Set<String>> blocMembership) {

    public StandingBlockRules {
        Objects.requireNonNull(candidacy, "candidacy");
        Objects.requireNonNull(blocRelations, "blocRelations");
        Objects.requireNonNull(blocMembership, "blocMembership");
    }

    /**
     * The factions one bloc is made of - its whole membership, and not who happens to stand in the
     * system being hovered.
     *
     * @param blocId the bloc to read; a bloc with no ID is made of nobody
     * @return its member factions
     */
    public Set<String> readMemberFactionIds(String blocId) {
        return blocMembership.apply(blocId);
    }
}
