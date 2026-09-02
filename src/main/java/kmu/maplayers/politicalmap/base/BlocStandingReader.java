package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.StarsectorPlayerFactionResolver;
import kmlib.starsector.relation.PlayerStanding;
import kmlib.starsector.relation.StarsectorPlayerStandings;
import kmlib.text.KmlibStrings;

import kmu.maplayers.politicalmap.base.dominance.HolderGrouping;

import java.util.Objects;
import java.util.Optional;

/**
 * Reads a bloc's {@link BlocStanding}: which of the three positions it holds, and - where it holds
 * a measured one - the two ends its members' standings fold to.
 *
 * <p>Relations with the player are the one thing a picker ranks blocs by that no per-system fold
 * computes. They are a bloc-level fact, read off the sector rather than summed over systems, and
 * identical under every view - so the reading is stated here once instead of on each vocabulary's
 * metrics record.
 *
 * <p>Membership comes from the grouping the map painted by, so a bloc is asked about the same
 * factions it was drawn from: read off any other fold, the range could be taken over members no
 * listed bloc has. It is the bloc's whole membership rather than whoever holds a colony somewhere,
 * on the same reasoning every other bloc-level rule reads it whole - a range over whichever subset
 * happened to be present is not the fact it claims to be.
 *
 * <p>The ends are folded on the reputation rather than on the level the game names, that being the
 * finer of the two: two members a level apart in name and a point apart in fact still order, and
 * the level rides along on the standing that won either end.
 *
 * <p>The sector reads bind behind {@link PlayerStandingSource}, so the fold itself is arithmetic
 * over hand-built standings. Stateless past that seam and the grouping - both are read afresh on
 * every call, so a reader outlives the reputations it is asked about.
 */
public final class BlocStandingReader {

    private final HolderGrouping grouping;
    private final PlayerStandingSource standingSource;

    /**
     * Binds the two things a bloc's standing is read from.
     *
     * @param grouping       the fold naming each bloc's membership - the one the map painted by
     * @param standingSource where the player's own faction and each member's standing come from
     */
    public BlocStandingReader(HolderGrouping grouping, PlayerStandingSource standingSource) {

        this.grouping = Objects.requireNonNull(grouping, "grouping");
        this.standingSource = Objects.requireNonNull(standingSource, "standingSource");
    }

    /**
     * A reader over one live sector's factions and relations.
     *
     * @param sector   the sector the factions are looked up in, so a reader built over a second
     *                 sector reports that sector's relations
     * @param grouping the fold naming each bloc's membership
     * @return the reader
     */
    public static BlocStandingReader createForSector(SectorAPI sector, HolderGrouping grouping) {

        return new BlocStandingReader(
            grouping,
            new SectorPlayerStandingSource(Objects.requireNonNull(sector, "sector")));
    }

    /**
     * Where one bloc stands with the player.
     *
     * @param blocId the bloc to read; a bloc with no id names no membership to read one from
     * @return the bloc's position on the scale
     */
    public BlocStanding readBlocStanding(String blocId) {

        // A bloc nothing named is unreadable rather than left to the lookups below: its membership
        // comes back empty anyway, and the player check would match it against an equally absent
        // player bloc id, filing a bloc with no name as the player's own.
        if (!KmlibStrings.hasText(blocId)) {
            return BlocStanding.UNREADABLE;
        }

        // Asked before the members are walked, because the player's own bloc is the point the scale
        // is measured from rather than a position on it - folding its members would put the engine's
        // answer for the player's standing with themselves onto the scale.
        if (isPlayersOwnBloc(blocId)) {
            return BlocStanding.PLAYERS_OWN;
        }
        return foldMemberStandings(blocId);
    }

    // Whether this bloc is the one the player's own faction folds into - false while no player
    // faction is established, there being no identity for a bloc to be recognised as yet.
    //
    // Matched through the grouping rather than against the faction id itself, so a player faction
    // standing in an alliance is recognised as that alliance: under an alliance grouping the player
    // is drawn as part of it, and a bloc the map paints as the player's would otherwise be ranked
    // by how its other members feel about them.
    private boolean isPlayersOwnBloc(String blocId) {

        return standingSource.resolveEstablishedPlayerFactionId()
            .map(grouping::resolveBlocId)
            .filter(blocId::equals)
            .isPresent();
    }

    // The bloc's members folded to the two ends of the range they hold, or unreadable where not one
    // of them answered a standing. A member the sector cannot look up is passed over rather than
    // counted at nought, which would drag an end to the scale's centre on a faction nothing is known
    // about.
    private BlocStanding foldMemberStandings(String blocId) {

        PlayerStanding lowest = null;
        PlayerStanding highest = null;

        for (var memberFactionId : grouping.resolveMemberFactionIds(blocId)) {

            var memberStanding = standingSource.readStandingWithPlayer(memberFactionId);

            if (memberStanding.isPresent()) {
                lowest = selectLowerStanding(lowest, memberStanding.get());
                highest = selectHigherStanding(highest, memberStanding.get());
            }
        }
        return lowest == null
            ? BlocStanding.UNREADABLE
            : new BlocStanding.Measured(lowest, highest);
    }

    // The lower of the range's near end so far and one more member's standing, the member's own
    // where no end has been set yet - so the first member answering sets both ends and every later
    // one only widens them.
    private static PlayerStanding selectLowerStanding(
            PlayerStanding lowest,
            PlayerStanding memberStanding) {

        return lowest == null || memberStanding.reputation() < lowest.reputation()
            ? memberStanding
            : lowest;
    }

    // The far end's counterpart, read the same way off the same walk.
    private static PlayerStanding selectHigherStanding(
            PlayerStanding highest,
            PlayerStanding memberStanding) {

        return highest == null || memberStanding.reputation() > highest.reputation()
            ? memberStanding
            : highest;
    }

    /**
     * The two sector reads a bloc's standing is folded from: who the player is, and where one
     * faction stands with them.
     *
     * <p>One interface rather than two seams passed side by side, because both are readings of the
     * same sector at the same moment - handed over separately, a fold could recognise one sector's
     * player faction and rank another sector's relations.
     */
    public interface PlayerStandingSource {

        /**
         * The player's own faction, once they have an identity to be recognised by.
         *
         * @return the player faction's id, or nothing while no player faction is established - in
         *         which case no bloc is the player's own
         */
        Optional<String> resolveEstablishedPlayerFactionId();

        /**
         * Where one faction stands with the player.
         *
         * @param factionId the faction to read
         * @return its standing, or nothing where the faction cannot be looked up at all
         */
        Optional<PlayerStanding> readStandingWithPlayer(String factionId);
    }

    // The live reads, over one sector.
    private record SectorPlayerStandingSource(SectorAPI sector) implements PlayerStandingSource {

        @Override
        public Optional<String> resolveEstablishedPlayerFactionId() {

            // Whether an identity has been finalised is asked of the running game, KMLib offering
            // no sector-bound form of the test. Which faction it is comes off the handed-in sector,
            // so the standings measured from it are that sector's own.
            if (!StarsectorPlayerFactionResolver.isPlayerFactionEstablished()) {
                return Optional.empty();
            }
            var playerFaction = sector.getPlayerFaction();

            return playerFaction == null
                ? Optional.empty()
                : Optional.ofNullable(playerFaction.getId()).filter(KmlibStrings::hasText);
        }

        @Override
        public Optional<PlayerStanding> readStandingWithPlayer(String factionId) {

            // An id naming nobody is looked up as nobody rather than handed to the sector, which is
            // free to fault on it.
            return KmlibStrings.hasText(factionId)
                ? StarsectorPlayerStandings.readPlayerStanding(sector.getFaction(factionId))
                : Optional.empty();
        }
    }
}
