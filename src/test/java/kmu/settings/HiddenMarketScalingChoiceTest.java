package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link HiddenMarketScalingChoice}: each mode round-trips through its LunaLib
 * Radio label, and an unknown or missing label falls back to the caller's default (a
 * stale config or a read before LunaLib loads must not throw or silently flip the
 * hidden-market scaling mode).
 */
final class HiddenMarketScalingChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsTheChoiceForEachKnownLabel() {
            for (var choice : HiddenMarketScalingChoice.values()) {
                assertThat(HiddenMarketScalingChoice.fromLabel(choice.getLabel(),
                        HiddenMarketScalingChoice.NORMAL)).isEqualTo(choice);
            }
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A label from an old config (or a typo) matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(HiddenMarketScalingChoice.fromLabel("Scaled",
                    HiddenMarketScalingChoice.FIXED)).isEqualTo(HiddenMarketScalingChoice.FIXED);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            // getString returns null before LunaLib has loaded the field; the fallback
            // covers that read.
            assertThat(HiddenMarketScalingChoice.fromLabel(null,
                    HiddenMarketScalingChoice.FIXED)).isEqualTo(HiddenMarketScalingChoice.FIXED);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(HiddenMarketScalingChoice.NORMAL.getLabel()).isEqualTo("Normal");
            assertThat(HiddenMarketScalingChoice.FIXED.getLabel()).isEqualTo("Fixed");
        }
    }
}
