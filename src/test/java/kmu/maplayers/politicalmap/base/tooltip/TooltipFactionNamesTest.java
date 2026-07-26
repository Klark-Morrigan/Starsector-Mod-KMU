package kmu.maplayers.politicalmap.base.tooltip;

import com.fs.starfarer.api.campaign.FactionAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link TooltipFactionNames}: a resolvable faction reads as its long title, and one the sector
 * no longer knows falls back to the bare id rather than leaving the row nameless.
 */
final class TooltipFactionNamesTest {

    @Nested
    class ResolveLongName {

        @Test
        void resolveLongNameReadsTheFactionsLongTitle() {
            var factionMock = mock(FactionAPI.class);
            when(factionMock.getDisplayNameLong()).thenReturn("The Hegemony");

            assertThat(TooltipFactionNames.resolveLongName(factionMock, "hegemony"))
                    .isEqualTo("The Hegemony");
        }

        @Test
        void resolveLongNameFallsBackToTheIdForAnUnresolvableFaction() {
            assertThat(TooltipFactionNames.resolveLongName(null, "hegemony")).isEqualTo("hegemony");
        }
    }
}
