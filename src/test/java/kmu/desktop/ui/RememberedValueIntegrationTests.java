package kmu.desktop.ui;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Color;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link RememberedValue} over each {@link SavedValueFormat}, against a real
 * {@link SavedValues} file: the owner is told what was remembered or the fallback, and a recorded
 * value is told and written down under the format's own spelling.
 */
final class RememberedValueIntegrationTests {

    private enum Size {
        SMALL,
        LARGE
    }

    @TempDir
    private Path store;

    private final List<Object> told = new ArrayList<>();

    private SavedValues saved;

    @BeforeEach
    void installTheStore() {
        saved = SavedValues.rememberIn(store.resolve("remembered.json"));
    }

    @Nested
    class ApplyRemembered {

        @Test
        void tellsTheOwnerTheFallbackWhereNothingIsRemembered() {

            var value = new RememberedValue<>("reach", 12.5, SavedValueFormat.MEASUREMENT, told::add);

            assertThat(value.applyRemembered())
                .isEqualTo(12.5);
            assertThat(told)
                .containsExactly(12.5);
        }

        @Test
        void tellsTheOwnerWhatWasRemembered() {

            saved.putBoolean("shown", true);

            var value = new RememberedValue<>("shown", false, SavedValueFormat.FLAG, told::add);

            assertThat(value.applyRemembered())
                .isTrue();
            assertThat(told)
                .containsExactly(true);
        }

        @Test
        void keepsAColoursAlpha() {

            saved.putInt("fill", 0x40112233);

            var value = new RememberedValue<>("fill", Color.BLACK, SavedValueFormat.COLOUR, told::add);

            assertThat(value.applyRemembered())
                .isEqualTo(new Color(0x11, 0x22, 0x33, 0x40));
        }

        @Test
        void readsANamedValueByItsName() {

            saved.put("size", "LARGE");

            var value = new RememberedValue<>(
                "size",
                Size.SMALL,
                SavedValueFormat.createNamedFormat(List.of(Size.SMALL, Size.LARGE)),
                told::add);

            assertThat(value.applyRemembered())
                .isEqualTo(Size.LARGE);
        }

        @Test
        void readsANameNoLongerOfferedAsTheFallback() {

            saved.put("size", "ENORMOUS");

            var value = new RememberedValue<>(
                "size",
                Size.SMALL,
                SavedValueFormat.createNamedFormat(List.of(Size.SMALL, Size.LARGE)),
                told::add);

            assertThat(value.applyRemembered())
                .isEqualTo(Size.SMALL);
        }
    }

    @Nested
    class RecordValue {

        @Test
        void tellsTheOwnerAndWritesTheValueDown() {

            var value = new RememberedValue<>("reach", 12.5, SavedValueFormat.MEASUREMENT, told::add);

            value.recordValue(30.0);

            assertThat(told)
                .containsExactly(30.0);
            assertThat(saved.get("reach", "nothing"))
                .isEqualTo("30.0");
        }

        @Test
        void writesANamedValueUnderItsName() {

            var value = new RememberedValue<>(
                "size",
                Size.SMALL,
                SavedValueFormat.createNamedFormat(List.of(Size.SMALL, Size.LARGE)),
                told::add);

            value.recordValue(Size.LARGE);

            assertThat(saved.get("size", "nothing"))
                .isEqualTo("LARGE");
        }

        @Test
        void writesAColourAsItsPackedArgb() {

            var value = new RememberedValue<>("fill", Color.BLACK, SavedValueFormat.COLOUR, told::add);

            value.recordValue(new Color(0x11, 0x22, 0x33, 0x40));

            assertThat(saved.getInt("fill", 0))
                .isEqualTo(0x40112233);
        }
    }
}
