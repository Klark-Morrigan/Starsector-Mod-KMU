package kmu.desktop.ui.swing;

import kmu.desktop.ui.RememberedValue;
import kmu.desktop.ui.SavedValueFormat;
import kmu.desktop.ui.SavedValues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Container;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JSlider;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins {@link SliderRows} against a real {@link SavedValues} file.
 *
 * <p>Two sliders sharing a line have to behave exactly as two sliders that do not. The fault
 * worth guarding is the half that quietly becomes the other's shadow: a right-hand knob saved
 * under the left one's key, opened on the left one's value, or put back by the left one's reset
 * would look entirely correct on screen while being one control wearing two names.
 *
 * <p>And a slider does its expensive work and writes its value once, when the handle settles,
 * not on every pixel of a drag.
 *
 * <p>These need a runner that can measure text, and avoiding size queries is not what makes them
 * safe. No case here asks a component for its size, but putting a value in the box measures the
 * line it holds, so the font manager is built whatever the suite declines to ask for. On a runner
 * whose fontconfig cannot be read that surfaces as an InternalError naming neither fonts nor the
 * machine - which is why KMU declares awtFontFamiliesRequired, so the build stops ahead of these
 * with the environment written out.
 */
final class SliderRowsIntegrationTests {

    private static final String LEFT_KEY = "leftKnob";
    private static final String RIGHT_KEY = "rightKnob";

    private static final double LEFT_DEFAULT = 25;
    private static final double RIGHT_DEFAULT = 75;

    private static final SliderRows.SliderRange RANGE = new SliderRows.SliderRange(0, 100);

    @TempDir
    private Path store;

    private final List<String> work = new ArrayList<>();

    private double leftValue;
    private double rightValue;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(store.resolve("slider-rows.json"));
    }

    @Nested
    class SliderSpec {

        @Test
        void refusesAFallbackOutsideItsRange() {

            assertThatThrownBy(() -> new SliderRows.SliderSpec(
                    "Knob",
                    RANGE,
                    rememberLeft(150),
                    new SliderRows.SliderWork(() -> { }, () -> { })))
                .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    class BuildSlider {

        @Test
        void doesNoWorkWhileBuilt() {

            buildLeft();

            assertThat(work)
                .isEmpty();
        }

        @Test
        void doesTheCheapWorkButNotTheSettlingWhileDragged() {

            var slider = ComponentTreeFixture.findOnly(buildLeft(), JSlider.class);

            slider.setValueIsAdjusting(true);
            work.clear();
            slider.setValue(600);

            assertThat(work)
                .containsExactly("changed");
            assertThat(leftValue)
                .isEqualTo(60);
            assertThat(SavedValues.findSavedValues().getDouble(LEFT_KEY, -1))
                .isEqualTo(-1);
        }

        @Test
        void settlesAndWritesTheValueOnceTheHandleIsLetGo() {

            var slider = ComponentTreeFixture.findOnly(buildLeft(), JSlider.class);

            slider.setValueIsAdjusting(true);
            slider.setValue(600);
            work.clear();
            slider.setValueIsAdjusting(false);

            assertThat(work)
                .containsExactly("changed", "settled");
            assertThat(SavedValues.findSavedValues().getDouble(LEFT_KEY, -1))
                .isEqualTo(60);
        }

        // The typed path moves the slider too, and the slider's own listener must not settle a
        // second time on the step the value quantises to.
        @Test
        void settlesATypedValueOnce() {

            var box = ComponentTreeFixture.findOnly(buildLeft(), JTextField.class);

            box.setText("42");
            box.postActionEvent();

            assertThat(work)
                .containsExactly("changed", "settled");
            assertThat(leftValue)
                .isEqualTo(42);
            assertThat(SavedValues.findSavedValues().getDouble(LEFT_KEY, -1))
                .isEqualTo(42);
        }
    }

    @Nested
    class BuildSliderPair {

        @Test
        void tellsEachOwnerItsOwnDefault() {

            buildPair();

            assertThat(leftValue)
                .isEqualTo(LEFT_DEFAULT);
            assertThat(rightValue)
                .isEqualTo(RIGHT_DEFAULT);
        }

        @Test
        void opensEachHalfOnItsOwnRememberedValue() {

            SavedValues.findSavedValues().putDouble(LEFT_KEY, 10);
            SavedValues.findSavedValues().putDouble(RIGHT_KEY, 90);

            buildPair();

            assertThat(leftValue)
                .isEqualTo(10);
            assertThat(rightValue)
                .isEqualTo(90);
        }

        @Test
        void givesEachHalfItsOwnValueBox() {

            var boxes = ComponentTreeFixture.findAll(buildPair(), JTextField.class);

            assertThat(boxes)
                .hasSize(2);
            assertThat(boxes.get(0).getText())
                .isNotEqualTo(boxes.get(1).getText());
        }

        @Test
        void givesEachHalfItsOwnReset() {

            var pair = buildPair();

            assertThat(ComponentTreeFixture.findAll(pair, JButton.class))
                .hasSize(2);
        }
    }

    private RememberedValue<Double> rememberLeft(double fallback) {

        return new RememberedValue<>(
            LEFT_KEY,
            fallback,
            SavedValueFormat.MEASUREMENT,
            value -> leftValue = value);
    }

    private SliderRows.SliderWork recordWork() {
        return new SliderRows.SliderWork(() -> work.add("changed"), () -> work.add("settled"));
    }

    private Container buildLeft() {

        return SliderRows.buildSlider(
            new SliderRows.SliderSpec("Left knob", RANGE, rememberLeft(LEFT_DEFAULT), recordWork()));
    }

    private Container buildPair() {

        return SliderRows.buildSliderPair(
            new SliderRows.SliderSpec(
                "Left knob",
                RANGE,
                rememberLeft(LEFT_DEFAULT),
                new SliderRows.SliderWork(() -> { }, () -> { })),
            new SliderRows.SliderSpec(
                "Right knob",
                RANGE,
                new RememberedValue<>(
                    RIGHT_KEY,
                    RIGHT_DEFAULT,
                    SavedValueFormat.MEASUREMENT,
                    value -> rightValue = value),
                new SliderRows.SliderWork(() -> { }, () -> { })));
    }
}
