package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link ConcealedBaseScalingChoice}: each mode round-trips through its LunaLib
 * Radio label, and an unknown or missing label falls back to the caller's default (a
 * stale config or a read before LunaLib loads must not throw or silently flip the
 * concealed-base scaling mode).
 */
final class ConcealedBaseScalingChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsTheChoiceForEachKnownLabel() {
            for (var choice : ConcealedBaseScalingChoice.values()) {
                assertThat(ConcealedBaseScalingChoice.fromLabel(choice.getLabel(),
                        ConcealedBaseScalingChoice.NORMAL)).isEqualTo(choice);
            }
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A label from an old config (or a typo) matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(ConcealedBaseScalingChoice.fromLabel("Scaled",
                    ConcealedBaseScalingChoice.FIXED)).isEqualTo(ConcealedBaseScalingChoice.FIXED);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            // getString returns null before LunaLib has loaded the field; the fallback
            // covers that read.
            assertThat(ConcealedBaseScalingChoice.fromLabel(null,
                    ConcealedBaseScalingChoice.FIXED)).isEqualTo(ConcealedBaseScalingChoice.FIXED);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(ConcealedBaseScalingChoice.NORMAL.getLabel()).isEqualTo("Normal");
            assertThat(ConcealedBaseScalingChoice.FIXED.getLabel()).isEqualTo("Fixed");
        }
    }
}
