package kmu.desktop.ui.swing;

import kmu.desktop.ui.SavedValues;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.awt.Container;
import java.nio.file.Path;

import javax.swing.JButton;
import javax.swing.JTextField;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SliderRows#buildSliderPair} against a real {@link SavedValues} file.
 *
 * <p>Two sliders sharing a line have to behave exactly as two sliders that do not. The fault
 * worth guarding is the half that quietly becomes the other's shadow: a right-hand knob saved
 * under the left one's key, opened on the left one's value, or put back by the left one's reset
 * would look entirely correct on screen while being one control wearing two names.
 *
 * <p>Nothing here asks a component for its size. Doing so needs font metrics, which a runner
 * with no usable fonts cannot supply - the failure that took every one of these down the first
 * time they ran on CI.
 */
final class SliderRowsIntegrationTest {

    private static final String LEFT_KEY = "leftKnob";
    private static final String RIGHT_KEY = "rightKnob";

    private static final double LEFT_DEFAULT = 25;
    private static final double RIGHT_DEFAULT = 75;

    private static final double LOWEST = 0;
    private static final double HIGHEST = 100;

    @TempDir
    private Path store;

    private double leftValue;
    private double rightValue;

    @BeforeEach
    void rememberInTempDirectory() {
        SavedValues.rememberIn(store.resolve("slider-rows.json"));
    }

    @Nested
    class BuildSliderPair {

        @Test
        void buildSliderPairTellsEachOwnerItsOwnDefault() {

            buildPair();

            assertThat(leftValue)
                .isEqualTo(LEFT_DEFAULT);
            assertThat(rightValue)
                .isEqualTo(RIGHT_DEFAULT);
        }

        @Test
        void buildSliderPairOpensEachHalfOnItsOwnRememberedValue() {

            SavedValues.findSavedValues().putDouble(LEFT_KEY, 10);
            SavedValues.findSavedValues().putDouble(RIGHT_KEY, 90);

            buildPair();

            assertThat(leftValue)
                .isEqualTo(10);
            assertThat(rightValue)
                .isEqualTo(90);
        }

        @Test
        void buildSliderPairGivesEachHalfItsOwnValueBox() {

            var boxes = ComponentTreeFixture.findAll(buildPair(), JTextField.class);

            assertThat(boxes)
                .hasSize(2);
            assertThat(boxes.get(0).getText())
                .isNotEqualTo(boxes.get(1).getText());
        }

        @Test
        void buildSliderPairGivesEachHalfItsOwnReset() {

            var pair = buildPair();

            assertThat(ComponentTreeFixture.findAll(pair, JButton.class))
                .hasSize(2);
        }
    }

    private Container buildPair() {

        return SliderRows.buildSliderPair(
            new SliderRows.SliderSpec(
                LEFT_KEY,
                "Left knob",
                new SliderRows.SliderRange(LOWEST, HIGHEST, LEFT_DEFAULT),
                new SliderRows.SliderWork(value -> leftValue = value, () -> { }, () -> { })),
            new SliderRows.SliderSpec(
                RIGHT_KEY,
                "Right knob",
                new SliderRows.SliderRange(LOWEST, HIGHEST, RIGHT_DEFAULT),
                new SliderRows.SliderWork(value -> rightValue = value, () -> { }, () -> { })));
    }

}
