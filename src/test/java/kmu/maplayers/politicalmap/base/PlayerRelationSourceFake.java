package kmu.maplayers.politicalmap.base;

import kmlib.starsector.factions.relation.FactionRelation;

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
 * @param establishedPlayerFactionId the player's own faction, or null for a game in which no player
 *                                   faction is established yet
 * @param standingByFactionId        each faction that answers, and what it answers
 */
record PlayerRelationSourceFake(
    String establishedPlayerFactionId,
    Map<String, FactionRelation> standingByFactionId)
    implements BlocStandingReader.PlayerRelationSource {

    @Override
    public Optional<String> resolveEstablishedPlayerFactionId() {
        return Optional.ofNullable(establishedPlayerFactionId);
    }

    @Override
    public Optional<FactionRelation> readRelationWithPlayer(String factionId) {
        return Optional.ofNullable(standingByFactionId.get(factionId));
    }
}
