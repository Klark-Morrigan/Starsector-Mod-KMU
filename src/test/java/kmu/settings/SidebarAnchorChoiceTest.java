package kmu.settings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins {@link SidebarAnchorChoice}: each of the eight positions round-trips through its
 * LunaLib Radio label, and an unknown or missing label falls back to the caller's default
 * (a stale config or a read before LunaLib loads must not throw or misplace the box).
 */
final class SidebarAnchorChoiceTest {

    @Nested
    class FromLabel {

        @Test
        void fromLabelReturnsTheChoiceForEachKnownLabel() {
            for (var choice : SidebarAnchorChoice.values()) {
                assertThat(SidebarAnchorChoice.fromLabel(choice.getLabel(),
                        SidebarAnchorChoice.TOP_RIGHT)).isEqualTo(choice);
            }
        }

        @Test
        void fromLabelReturnsTheFallbackForAnUnknownLabel() {
            // A label from an old config (or a typo) matches nothing, so the caller's
            // default stands rather than throwing.
            assertThat(SidebarAnchorChoice.fromLabel("Middle middle",
                    SidebarAnchorChoice.BOTTOM_LEFT)).isEqualTo(SidebarAnchorChoice.BOTTOM_LEFT);
        }

        @Test
        void fromLabelReturnsTheFallbackForNull() {
            // getString returns null before LunaLib has loaded the field; the fallback
            // covers that read.
            assertThat(SidebarAnchorChoice.fromLabel(null, SidebarAnchorChoice.TOP_LEFT))
                    .isEqualTo(SidebarAnchorChoice.TOP_LEFT);
        }
    }

    @Nested
    class GetLabel {

        @Test
        void getLabelReturnsTheLunaLibOptionLabel() {
            assertThat(SidebarAnchorChoice.TOP_LEFT.getLabel()).isEqualTo("Top left");
            assertThat(SidebarAnchorChoice.RIGHT_CENTER.getLabel()).isEqualTo("Right center");
            assertThat(SidebarAnchorChoice.BOTTOM_CENTER.getLabel()).isEqualTo("Bottom center");
        }
    }
}
