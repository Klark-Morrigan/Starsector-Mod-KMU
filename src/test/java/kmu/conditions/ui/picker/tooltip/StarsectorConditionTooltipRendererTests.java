package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.campaign.econ.MarketConditionAPI;
import com.fs.starfarer.api.campaign.econ.MarketConditionPlugin;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how a present condition's own tooltip is read from its live plugin, and what stands in when the plugin
 * is missing or fails: no expansion, a fixed width, and the condition's name as the title.
 */
class StarsectorConditionTooltipRendererTests {

    private static final String CONDITION_NAME = "Hot";

    private static final float FALLBACK_WIDTH = 420f;

    @Nested
    class IsTooltipExpandable {

        @Test
        void answersExpandableWhenPluginIs() {

            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.isTooltipExpandable())
                .thenReturn(true);

            assertThat(buildRendererOver(pluginMock).isTooltipExpandable())
                .isTrue();
        }

        @Test
        void answersNotExpandableWhenPluginDeclines() {

            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.isTooltipExpandable())
                .thenReturn(false);

            assertThat(buildRendererOver(pluginMock).isTooltipExpandable())
                .isFalse();
        }

        @Test
        void answersNotExpandableWithoutPlugin() {

            assertThat(buildRendererOver(null).isTooltipExpandable())
                .isFalse();
        }

        @Test
        void answersNotExpandableWhenPluginLookupFails() {

            assertThat(buildRendererWithFailingLookup().isTooltipExpandable())
                .isFalse();
        }

        @Test
        void answersNotExpandableWhenPluginFails() {

            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.isTooltipExpandable())
                .thenThrow(new IllegalStateException("plugin failed"));

            assertThat(buildRendererOver(pluginMock).isTooltipExpandable())
                .isFalse();
        }
    }

    @Nested
    class GetTooltipWidth {

        @Test
        void takesWidthFromPlugin() {

            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.getTooltipWidth())
                .thenReturn(512f);

            assertThat(buildRendererOver(pluginMock).getTooltipWidth())
                .isEqualTo(512f);
        }

        @Test
        void fallsBackToFixedWidthWhenPluginWidthIsNotPositive() {

            // A plugin that never set a width answers zero, which would draw a box with no room for text.
            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.getTooltipWidth())
                .thenReturn(0f);

            assertThat(buildRendererOver(pluginMock).getTooltipWidth())
                .isEqualTo(FALLBACK_WIDTH);
        }

        @Test
        void fallsBackToFixedWidthWithoutPlugin() {

            assertThat(buildRendererOver(null).getTooltipWidth())
                .isEqualTo(FALLBACK_WIDTH);
        }

        @Test
        void fallsBackToFixedWidthWhenPluginLookupFails() {

            assertThat(buildRendererWithFailingLookup().getTooltipWidth())
                .isEqualTo(FALLBACK_WIDTH);
        }

        @Test
        void fallsBackToFixedWidthWhenPluginFails() {

            var pluginMock = mock(MarketConditionPlugin.class);

            when(pluginMock.getTooltipWidth())
                .thenThrow(new IllegalStateException("plugin failed"));

            assertThat(buildRendererOver(pluginMock).getTooltipWidth())
                .isEqualTo(FALLBACK_WIDTH);
        }
    }

    @Nested
    class CreateTooltip {

        @Test
        void drawsPluginTooltipAtTheAskedExpansion() {

            var pluginMock = mock(MarketConditionPlugin.class);
            var tooltipMock = mock(TooltipMakerAPI.class);

            buildRendererOver(pluginMock).createTooltip(tooltipMock, true);

            verify(pluginMock)
                .createTooltip(tooltipMock, true);
            verify(tooltipMock, never())
                .addTitle(anyString());
        }

        @Test
        void titlesTooltipWithConditionNameWithoutPlugin() {

            var tooltipMock = mock(TooltipMakerAPI.class);

            buildRendererOver(null).createTooltip(tooltipMock, false);

            verify(tooltipMock)
                .addTitle(CONDITION_NAME);
        }

        @Test
        void titlesTooltipWithConditionNameWhenPluginLookupFails() {

            var tooltipMock = mock(TooltipMakerAPI.class);

            buildRendererWithFailingLookup().createTooltip(tooltipMock, false);

            verify(tooltipMock)
                .addTitle(CONDITION_NAME);
        }
    }

    private static StarsectorConditionTooltipRenderer buildRendererOver(MarketConditionPlugin plugin) {

        var conditionMock = mock(MarketConditionAPI.class);

        when(conditionMock.getName())
            .thenReturn(CONDITION_NAME);
        when(conditionMock.getPlugin())
            .thenReturn(plugin);

        return new StarsectorConditionTooltipRenderer(conditionMock);
    }

    private static StarsectorConditionTooltipRenderer buildRendererWithFailingLookup() {

        var conditionMock = mock(MarketConditionAPI.class);

        when(conditionMock.getName())
            .thenReturn(CONDITION_NAME);
        when(conditionMock.getPlugin())
            .thenThrow(new IllegalStateException("plugin unavailable"));

        return new StarsectorConditionTooltipRenderer(conditionMock);
    }
}
