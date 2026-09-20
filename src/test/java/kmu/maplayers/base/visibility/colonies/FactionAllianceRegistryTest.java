package kmu.maplayers.base.visibility.colonies;

import kmlib.starsector.factions.alliances.FactionAlliances;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.FactionAllianceFixture.registerAllianceOf;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the seam between the revelation rule and whatever maintains an alliance answers: the
 * registered supplier's own reading, read afresh each time, and no alliance at all where nothing was
 * wired.
 *
 * <p>That last is the case worth having. An install without the mod that keeps alliances has to
 * answer exactly as the rule did before it read any, so a registry inventing a partnership would
 * silence a witness on an install that has none.
 */
final class FactionAllianceRegistryTest {

    private static final String HEGEMONY = "hegemony";
    private static final String PERSEAN_LEAGUE = "persean_league";

    @AfterEach
    void clearRegisteredAllianceSource() {
        clearRegistrations();
    }

    @Nested
    class ReadAlliances {

        @Test
        void readsTheRegisteredSource() {

            registerAllianceOf(HEGEMONY, PERSEAN_LEAGUE);

            assertThat(FactionAllianceRegistry.readAlliances()
                    .areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isTrue();
        }

        @Test
        void readsTheSourceAfreshSoAnAllianceFormedLaterIsSeen() {
            // Alliances form and dissolve in play, so the registry holds where to ask rather than
            // what was answered once - a snapshot taken at start-up would credit yesterday's
            // arrangement for the rest of the session.
            var memberships = new HashMap<String, String>();

            FactionAllianceRegistry.registerAllianceSource(
                () -> new FactionAlliances(memberships));

            assertThat(FactionAllianceRegistry.readAlliances()
                    .areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isFalse();

            memberships.put(HEGEMONY, "alliance_formed_since");
            memberships.put(PERSEAN_LEAGUE, "alliance_formed_since");

            assertThat(FactionAllianceRegistry.readAlliances()
                    .areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isTrue();
        }

        @Test
        void readsNobodyAsAlliedBeforeAnythingIsRegistered() {

            FactionAllianceRegistry.registerAllianceSource(null);

            assertThat(FactionAllianceRegistry.readAlliances()
                    .areFactionsAllied(HEGEMONY, PERSEAN_LEAGUE))
                .isFalse();
        }

        @Test
        void readsASourceAnsweringNothingAsNoAlliances() {
            // A supplier may be reached before whatever it reads exists. That is no reason to fail
            // a render pass, and every faction present speaking is the reading the rule shipped
            // with.
            FactionAllianceRegistry.registerAllianceSource(() -> null);

            assertThat(FactionAllianceRegistry.readAlliances())
                .isEqualTo(new FactionAlliances(Map.of()));
        }
    }
}
