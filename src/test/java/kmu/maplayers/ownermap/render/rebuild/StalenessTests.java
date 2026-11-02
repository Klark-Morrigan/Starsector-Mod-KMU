package kmu.maplayers.ownermap.render.rebuild;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

final class StalenessTests {

    @Nested
    class IsCellCutStale {

        // A recut is the one value that reseeds the cells; owing the content alone keeps them.
        @ParameterizedTest
        @EnumSource(
            value = Staleness.class,
            names = "CUT_AND_CONTENT")
        void isTrueForTheRecut(Staleness staleness) {

            // ACT
            var actualIsCellCutStale = staleness.isCellCutStale();

            // ASSERT
            assertThat(actualIsCellCutStale)
                .isTrue();
        }

        // The other values leave the cells standing, so a rebuild under them keeps its cut number.
        @ParameterizedTest
        @EnumSource(
            value = Staleness.class,
            names = "CUT_AND_CONTENT",
            mode = EnumSource.Mode.EXCLUDE)
        void isFalseForEveryOtherValue(Staleness staleness) {

            // ACT
            var actualIsCellCutStale = staleness.isCellCutStale();

            // ASSERT
            assertThat(actualIsCellCutStale)
                .isFalse();
        }
    }
}
