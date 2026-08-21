package kmu;

import com.fs.starfarer.api.campaign.SectorAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins the two askings apart: a load brings a sector into line whatever was applied before, and a
 * settings change acts only when this switch is the one that moved.
 */
class KmuToggledFeatureTest {

    @Nested
    class ApplyTo {

        @Test
        void standsTheFeatureUpWhileTheSwitchIsOn() {

            var feature = new RecordedFeature(true);

            feature.toggledFeature.applyTo(feature.sectorMock);

            assertThat(feature.calls)
                .containsExactly("stand up");
        }

        @Test
        void takesTheFeatureBackWhileTheSwitchIsOff() {

            var feature = new RecordedFeature(false);

            feature.toggledFeature.applyTo(feature.sectorMock);

            assertThat(feature.calls)
                .containsExactly("take back");
        }

        @Test
        void appliesAgainOnASecondLoadThoughTheSwitchHasNotMoved() {
            // A load is not a settings change: the sector is new and carries none of the wiring the
            // last one was given, so an unchanged switch still has everything to stand up.
            var feature = new RecordedFeature(true);

            feature.toggledFeature.applyTo(feature.sectorMock);
            feature.toggledFeature.applyTo(feature.sectorMock);

            assertThat(feature.calls)
                .containsExactly("stand up", "stand up");
        }
    }

    @Nested
    class ApplyToIfSwitched {

        @Test
        void doesNothingWhileTheSwitchReadsAsItWasLastApplied() {
            // LunaLib announces that the settings changed rather than which setting did, so a
            // feature acting on every announcement would be torn down and rebuilt whenever the
            // player moved an unrelated slider.
            var feature = new RecordedFeature(true);

            feature.toggledFeature.applyTo(feature.sectorMock);
            feature.calls.clear();

            feature.toggledFeature.applyToIfSwitched(feature.sectorMock);

            assertThat(feature.calls)
                .isEmpty();
        }

        @Test
        void appliesWhereTheSwitchHasMovedSinceItWasLastApplied() {

            var feature = new RecordedFeature(true);

            feature.toggledFeature.applyTo(feature.sectorMock);
            feature.calls.clear();

            feature.isSwitchedOn = false;
            feature.toggledFeature.applyToIfSwitched(feature.sectorMock);

            assertThat(feature.calls)
                .containsExactly("take back");
        }

        @Test
        void appliesBeforeAnythingHasEverBeenApplied() {
            // Nothing has established a state to compare against, so the switch is answered rather
            // than assumed - the safe direction, since assuming leaves a feature at whatever the
            // last session left and no load has yet corrected it.
            var feature = new RecordedFeature(false);

            feature.toggledFeature.applyToIfSwitched(feature.sectorMock);

            assertThat(feature.calls)
                .containsExactly("take back");
        }
    }

    // One toggled feature and a record of what it did, so a case asserts on the calls made rather
    // than on a collaborator's own behaviour. The switch is a field the case moves, since what is
    // under test is precisely how a read taken now compares with the one taken last.
    private static final class RecordedFeature {

        private final List<String> calls = new ArrayList<>();
        private final SectorAPI sectorMock = mock(SectorAPI.class);
        private final KmuToggledFeature toggledFeature;

        private boolean isSwitchedOn;

        private RecordedFeature(boolean isSwitchedOn) {

            this.isSwitchedOn = isSwitchedOn;
            this.toggledFeature = new KmuToggledFeature(
                () -> this.isSwitchedOn,
                sector -> calls.add("stand up"),
                sector -> calls.add("take back"));
        }
    }
}
