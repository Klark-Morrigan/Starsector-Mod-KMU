package kmu.settings;

import kmlib.profiling.ProfileLevel;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins each option of the profiling-level Radio to the library level it stands for.
 *
 * <p>Nothing else can. The settings suite holds the option labels against the CSV, so a reworded
 * label fails there; what no walk over the file can see is which level a label is wired to. Off
 * mapped to anything else would leave a capture running for every player who never asked for one,
 * and the two others transposed would answer a request for whole frames with a clock read per item
 * - neither showing on screen as anything but the game running slower than it should.
 *
 * <p>The mapping is asserted per constant rather than by walking the enum against a list, since a
 * walk written from the same enum would agree with whatever it says.
 */
final class ProfilingLevelChoiceTest {

    @Nested
    class ResolveProfileLevel {

        @Test
        void resolveProfileLevelMapsOffToOff() {

            assertThat(ProfilingLevelChoice.OFF.resolveProfileLevel())
                .isEqualTo(ProfileLevel.OFF);
        }

        @Test
        void resolveProfileLevelMapsCoarseToCoarse() {

            assertThat(ProfilingLevelChoice.COARSE.resolveProfileLevel())
                .isEqualTo(ProfileLevel.COARSE);
        }

        @Test
        void resolveProfileLevelMapsFineToFine() {

            assertThat(ProfilingLevelChoice.FINE.resolveProfileLevel())
                .isEqualTo(ProfileLevel.FINE);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReadsTheWordingTheSettingsScreenStores() {
            // The stored key wearing a caption's costume. Held here as well as against the CSV
            // because a label edited on one side alone is a player's pick that silently stops
            // resolving.
            assertThat(ProfilingLevelChoice.OFF.getLabel())
                .isEqualTo("Off");
            assertThat(ProfilingLevelChoice.COARSE.getLabel())
                .isEqualTo("Coarse");
            assertThat(ProfilingLevelChoice.FINE.getLabel())
                .isEqualTo("Fine");
        }
    }
}
