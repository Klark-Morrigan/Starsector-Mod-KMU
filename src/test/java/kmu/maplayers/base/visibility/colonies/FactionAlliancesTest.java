package kmu.maplayers.base.visibility.colonies;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the one question the alliance set answers - whether two factions stand together - and the
 * direction it errs in where it has nothing to say.
 *
 * <p>The erring is the half worth pinning. Every answer here decides whether a faction's colony is
 * credited with announcing the concealed base beside it, so a set that guessed would silence a
 * witness the player should have heard from.
 */
final class FactionAlliancesTest {

    private static final String HEGEMONY = "hegemony";
    private static final String PERSEAN_LEAGUE = "persean_league";
    private static final String PIRATES = "pirates";

    private static final String LEAGUE_ALLIANCE = "alliance_league";
    private static final String RIVAL_ALLIANCE = "alliance_rival";

    @Nested
    class AreFactionsAllied {

        @Test
        void reads_two_members_of_one_alliance_as_allied() {

            var alliances = new FactionAlliances(Map.of(
                HEGEMONY, LEAGUE_ALLIANCE,
                PERSEAN_LEAGUE, LEAGUE_ALLIANCE));

            assertThat(alliances.areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isTrue();
        }

        @Test
        void reads_members_of_two_alliances_as_unallied() {
            // Being in an alliance is not being in this one, which is the whole of what the rule
            // above turns on.
            var alliances = new FactionAlliances(Map.of(
                HEGEMONY, LEAGUE_ALLIANCE,
                PIRATES, RIVAL_ALLIANCE));

            assertThat(alliances.areFactionsAllied(HEGEMONY, PIRATES))
                .isFalse();
        }

        @Test
        void reads_a_faction_in_no_alliance_as_unallied() {

            var alliances = new FactionAlliances(Map.of(HEGEMONY, LEAGUE_ALLIANCE));

            assertThat(alliances.areFactionsAllied(HEGEMONY, PIRATES))
                .isFalse();
        }

        @Test
        void reads_a_faction_in_no_alliance_as_unallied_with_itself() {
            // Standing alone is not a relationship, so a faction nothing names answers no
            // differently when asked about itself. Whether a colony's own owner settles its place
            // is the caller's own comparison, which this is never asked to stand in for.
            assertThat(FactionAlliances.NONE.areFactionsAllied(PIRATES, PIRATES))
                .isFalse();
        }

        @Test
        void reads_a_colony_no_faction_holds_as_unallied() {
            // What a derelict reaches this with. The map the answer is read out of refuses a null
            // key outright, so the question is turned away before it is asked.
            var alliances = new FactionAlliances(Map.of(HEGEMONY, LEAGUE_ALLIANCE));

            assertThat(alliances.areFactionsAllied(HEGEMONY, null))
                .isFalse();

            assertThat(alliances.areFactionsAllied(null, HEGEMONY))
                .isFalse();
        }

        @Test
        void reads_every_faction_as_unallied_where_no_alliances_were_stated() {
            // What an installation with nothing wired answers, which is the rule as it stood
            // before alliances were read at all.
            assertThat(new FactionAlliances(null).areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isFalse();
        }
    }

    @Nested
    class AllianceIdByFactionId {

        @Test
        void keeps_the_memberships_it_was_built_with_when_the_source_map_changes_later() {
            // Folded once per pass and read by every projection in it, so a caller still holding
            // the map must not be able to dissolve an alliance underneath them.
            var memberships = new HashMap<String, String>();
            memberships.put(HEGEMONY, LEAGUE_ALLIANCE);
            memberships.put(PERSEAN_LEAGUE, LEAGUE_ALLIANCE);

            var alliances = new FactionAlliances(memberships);
            memberships.clear();

            assertThat(alliances.areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isTrue();
        }
    }
}
