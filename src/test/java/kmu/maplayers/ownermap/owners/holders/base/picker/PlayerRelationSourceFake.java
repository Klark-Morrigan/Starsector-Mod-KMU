package kmu.maplayers.ownermap.owners.holders.base.picker;

import kmlib.starsector.factions.relation.FactionRelation;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The two sector reads a bloc's standing folds from, stated outright: who the player is, and which
 * factions answer a standing at all.
 *
 * <p>Lets a case say what the sector holds rather than build a faction graph to imply it - a faction
 * missing from the map is one the sector cannot look up, which is the only way a member answers
 * nothing.
 *
 * <p>Counts every standing it is asked for, so a case can pin how often a sort reads the sector: one
 * standing read can walk the whole economy.
 */
final class PlayerRelationSourceFake
        implements BlocStandingReader.PlayerRelationSource {

    private final String establishedPlayerFactionId;
    private final Map<String, Integer> readCountByFactionId = new HashMap<>();
    private final Map<String, FactionRelation> standingByFactionId;

    /**
     * @param establishedPlayerFactionId the player's own faction, or null for a game in which no
     *                                   player faction is established yet
     * @param standingByFactionId        each faction that answers, and what it answers
     */
    PlayerRelationSourceFake(
            String establishedPlayerFactionId,
            Map<String, FactionRelation> standingByFactionId) {

        this.establishedPlayerFactionId = establishedPlayerFactionId;
        this.standingByFactionId = standingByFactionId;
    }

    @Override
    public Optional<FactionRelation> readRelationWithPlayer(String factionId) {

        readCountByFactionId.merge(factionId, 1, Integer::sum);

        return Optional.ofNullable(standingByFactionId.get(factionId));
    }

    @Override
    public Optional<String> resolveEstablishedPlayerFactionId() {
        return Optional.ofNullable(establishedPlayerFactionId);
    }

    /**
     * @return how many times each faction's standing was asked for; a faction never asked is absent
     */
    Map<String, Integer> readCountByFactionId() {
        return Map.copyOf(readCountByFactionId);
    }
}
