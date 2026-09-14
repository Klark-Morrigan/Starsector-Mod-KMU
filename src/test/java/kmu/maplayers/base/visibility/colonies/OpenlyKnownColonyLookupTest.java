package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.econ.MarketAPI;

import kmlib.starsector.markets.colonies.Colonies;
import kmlib.starsector.markets.colonies.Colony;
import kmlib.testfixtures.starsector.markets.colonies.ColonyMarketFixture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.ACADEMY_ENTITY_ID;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.clearRegistrations;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.registerTheAcademy;
import static kmu.maplayers.base.visibility.colonies.OpenlyKnownColonyFixture.standOnEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a reader holding an ID and no colony is told about whether a concealment is real: the
 * fold off a colony set, and what an ID the fold never met reads as.
 *
 * <p>Two things are asserted that no other suite can. The concealment is read before the registry,
 * so a colony held in the open is never excused whatever its entity is registered as - the word
 * being excused is one an open colony never earns. And the unmet ID reads as a secret, for the
 * reason its sibling folds err the way they do ({@link ColonyDiscoveryLookupTest}): excusing a
 * concealment on the strength of a fold nobody made is a finding the box has nothing behind.
 */
final class OpenlyKnownColonyLookupTest {

    private static final String ACADEMY_ID = "galatia_academy";
    private static final String BASE_ID = "daybreak";

    @BeforeEach
    void vouchForTheAcademy() {
        registerTheAcademy();
    }

    @AfterEach
    void clearRegisteredEntityIds() {
        clearRegistrations();
    }

    @Nested
    class ReadOpenlyKnownIn {

        @Test
        void holdsTheConcealedColoniesOfThePlaceTheSectorOpenlyPointsAt() {

            var academy = standOnEntity(
                nameColony(ColonyMarketFixture.buildFoundConcealedColony("independent"), ACADEMY_ID),
                ACADEMY_ENTITY_ID);

            var base = standOnEntity(
                nameColony(ColonyMarketFixture.buildFoundConcealedColony("pirates"), BASE_ID),
                "station_daybreak");

            var lookup = OpenlyKnownColonyLookup.readOpenlyKnownIn(new Colonies(List.of(
                new Colony(academy, false),
                new Colony(base, false))));

            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isTrue();
            assertThat(lookup.isOpenlyKnownColony(BASE_ID))
                .isFalse();
        }

        @Test
        void passesOverAColonyHeldInTheOpenOnARegisteredEntity() {
            // The concealment is asked first, so the registry is consulted for the few colonies a
            // finding could ever be made about - and an open colony, which never earns the word,
            // cannot be excused of it.
            var openColony = standOnEntity(
                nameColony(ColonyMarketFixture.buildVisibleColony("independent"), ACADEMY_ID),
                ACADEMY_ENTITY_ID);

            var lookup = OpenlyKnownColonyLookup.readOpenlyKnownIn(
                new Colonies(List.of(new Colony(openColony, true))));

            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isFalse();
        }

        @Test
        void passesOverAConcealedColonyStandingOnNoEntity() {
            // A market whose entity the sector has taken away is still a market a box lists, so the
            // fold answers rather than failing on it.
            var marketMock = nameColony(mock(MarketAPI.class), BASE_ID);

            when(marketMock.isHidden())
                .thenReturn(true);

            var lookup = OpenlyKnownColonyLookup.readOpenlyKnownIn(
                new Colonies(List.of(new Colony(marketMock, false))));

            assertThat(lookup.isOpenlyKnownColony(BASE_ID))
                .isFalse();
        }

        @Test
        void passesOverAColonyWithNoIdToKeyItOn() {
            // Nothing to pair a row with, so nothing is stored - which is not the same as storing
            // an entry under nothing and excusing every other unnamed colony.
            var academy = standOnEntity(
                ColonyMarketFixture.buildFoundConcealedColony("independent"),
                ACADEMY_ENTITY_ID);

            when(academy.getId())
                .thenReturn(null);

            var lookup = OpenlyKnownColonyLookup.readOpenlyKnownIn(
                new Colonies(List.of(new Colony(academy, false))));

            assertThat(lookup.openlyKnownColonyIds())
                .isEmpty();
        }

        @Test
        void readsAPlaceThatCouldNotBeWalkedAsNothingKnown() {

            assertThat(OpenlyKnownColonyLookup.readOpenlyKnownIn(null))
                .isEqualTo(OpenlyKnownColonyLookup.NONE);
        }
    }

    @Nested
    class IsOpenlyKnownColony {

        @Test
        void readsAnIdTheFoldNeverMetAsASecret() {

            assertThat(OpenlyKnownColonyLookup.NONE.isOpenlyKnownColony(ACADEMY_ID))
                .isFalse();
        }

        @Test
        void readsARowCarryingNoIdAsASecret() {

            assertThat(new OpenlyKnownColonyLookup(Set.of(ACADEMY_ID)).isOpenlyKnownColony(null))
                .isFalse();
        }
    }

    @Nested
    class Construct {

        @Test
        void readsAnAbsentSetAsNothingKnown() {

            assertThat(new OpenlyKnownColonyLookup(null).openlyKnownColonyIds())
                .isEmpty();
        }

        @Test
        void keepsTheLandmarksItWasBuiltWithWhenTheSourceSetChangesLater() {

            var openlyKnownColonyIds = new HashSet<String>();
            openlyKnownColonyIds.add(ACADEMY_ID);

            var lookup = new OpenlyKnownColonyLookup(openlyKnownColonyIds);

            openlyKnownColonyIds.clear();

            assertThat(lookup.isOpenlyKnownColony(ACADEMY_ID))
                .isTrue();
        }

        @Test
        void rejectsAnAttemptToChangeTheLandmarks() {
            // Folded once for a whole box and read by every line in it, so one line able to change
            // it would be re-reading the system underneath the others.
            var lookup = new OpenlyKnownColonyLookup(Set.of(ACADEMY_ID));

            assertThatThrownBy(() -> lookup.openlyKnownColonyIds().clear())
                .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    // The ID a row is paired back to its colony by. Colonies are built without one because almost
    // nothing reads it, and every unnamed colony would otherwise share one entry.
    private static MarketAPI nameColony(MarketAPI colony, String colonyId) {

        when(colony.getId())
            .thenReturn(colonyId);

        return colony;
    }
}
