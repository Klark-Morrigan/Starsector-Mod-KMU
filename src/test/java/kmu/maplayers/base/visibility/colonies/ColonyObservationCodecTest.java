package kmu.maplayers.base.visibility.colonies;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the stored form of a colony sighting, spelt out literally: the moment, the separator, then
 * the place to the end of the entry.
 *
 * <p>Written against the text rather than through the register, because the text is save state.
 * Every entry in every existing save is spelt this way, and a change to either the field order or
 * the separator would read as a player who had been nowhere rather than as a fault - so the one
 * place it can be caught is a case asserting the characters themselves.
 */
final class ColonyObservationCodecTest {

    private static final long OBSERVED_MOMENT = 4_200L;
    private static final String OBSERVED_PLACE = "kumari_kandam";

    // A location id spelt with the separator in it - the one spelling that parts in the wrong place
    // unless the moment leads and the place runs to the end.
    private static final String PLACE_HOLDING_THE_SEPARATOR = "outer@kumari_kandam";

    private ColonyObservationCodec codec;

    @BeforeEach
    void openCodec() {
        codec = new ColonyObservationCodec();
    }

    @Nested
    class DecodeObservation {

        @Test
        void readsBackWhereAndWhenAColonyWasSeen() {

            assertThat(codec.decodeObservation("4200@kumari_kandam"))
                .isEqualTo(ColonyObservation.createObservationAt(OBSERVED_PLACE, OBSERVED_MOMENT));
        }

        @Test
        void readsAPlaceHoldingTheSeparatorWhole() {
            // The fixed-fields-first convention from the reading side: a separator inside the place
            // is part of what it says, not a boundary.
            assertThat(codec.decodeObservation("4200@outer@kumari_kandam"))
                .isEqualTo(ColonyObservation.createObservationAt(
                    PLACE_HOLDING_THE_SEPARATOR,
                    OBSERVED_MOMENT));
        }

        @Test
        void readsAnEntryNamingAPlaceAloneAsSeenAtNoStatedMoment() {
            // The migration case: a save made before observations were timed names a place alone,
            // and that is a complete observation with one half missing rather than a broken one.
            assertThat(codec.decodeObservation("kumari_kandam"))
                .isEqualTo(ColonyObservation.createUndatedObservation(OBSERVED_PLACE));
        }

        @Test
        void readsAnUntimedEntryWholeWhereItsPlaceHoldsTheSeparator() {
            // The same case for a place that reads like a timed entry and is not one. Parted at the
            // separator it would name somewhere that does not exist, so the whole of it is the place.
            assertThat(codec.decodeObservation("outer@kumari_kandam"))
                .isEqualTo(ColonyObservation.createUndatedObservation(PLACE_HOLDING_THE_SEPARATOR));
        }

        @Test
        void readsNothingAtAllOutOfAnEntryNamingNowhere() {
            // The far end of the same posture. There is no weaker reading of an entry with no place
            // in it - the place is the half every visibility rule spends - so the colony reads as
            // never seen rather than as seen somewhere unstated.
            assertThat(codec.decodeObservation("  "))
                .isNull();
        }
    }

    @Nested
    class EncodeObservation {

        @Test
        void writesTheMomentAheadOfThePlaceItNames() {

            assertThat(codec.encodeObservation(
                    ColonyObservation.createObservationAt(OBSERVED_PLACE, OBSERVED_MOMENT)))
                .isEqualTo("4200@kumari_kandam");
        }

        @Test
        void writesThePlaceAloneWhereTheObservationCarriesNoMoment() {
            // What a sector with no clock to read records, and what every entry written before
            // observations were timed already looks like.
            assertThat(codec.encodeObservation(
                    ColonyObservation.createUndatedObservation(OBSERVED_PLACE)))
                .isEqualTo("kumari_kandam");
        }

        @Test
        void writesAPlaceHoldingTheSeparatorWhereItReadsBackWhole() {
            // The convention's other end, written and read by the same codec: a place the game
            // spelt with the separator survives the round trip rather than being mangled in a save.
            var observation = ColonyObservation.createObservationAt(
                PLACE_HOLDING_THE_SEPARATOR,
                OBSERVED_MOMENT);

            assertThat(codec.decodeObservation(codec.encodeObservation(observation)))
                .isEqualTo(observation);
        }
    }
}
