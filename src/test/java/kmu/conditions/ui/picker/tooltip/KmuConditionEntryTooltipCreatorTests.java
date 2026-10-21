package kmu.conditions.ui.picker.tooltip;

import com.fs.starfarer.api.ui.Alignment;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmu.conditions.ui.picker.model.KmuConditionPickerEntry;
import kmu.starsector.StarsectorUiColoursMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the hover box of an entry carrying its condition's live tooltip: the live tooltip takes the place of the
 * picker's own title and text, the picker's status and metadata sections follow it, and a live tooltip that fails
 * gives way to the picker's own.
 */
class KmuConditionEntryTooltipCreatorTests {

    private static final String CONDITION_NAME = "Hot";
    private static final String TOOLTIP_TEXT = "Too hot to settle.";

    // The section frames, as the muted and warning styles draw them.
    private static final Color MUTED_TITLE = new Color(170, 222, 255);
    private static final Color MUTED_BACKGROUND = new Color(31, 94, 112, 175);
    private static final Color WARNING_TITLE = new Color(255, 100, 0, 255);
    private static final Color WARNING_BACKGROUND = new Color(70, 20, 20);

    private static final float HEADING_PAD = 10f;
    private static final float LINE_PAD = 4f;

    private StarsectorUiColoursMock coloursMock;

    @BeforeEach
    void installUiColours() {

        coloursMock = StarsectorUiColoursMock.install();
    }

    @AfterEach
    void closeUiColours() {

        coloursMock.close();
    }

    @Nested
    class IsTooltipExpandable {

        @Test
        void answersExpandableWhenLiveTooltipIs() {

            var rendererMock = mock(KmuConditionTooltipRenderer.class);

            when(rendererMock.isTooltipExpandable())
                .thenReturn(true);

            assertThat(buildCreatorOver(stubEntryRenderedBy(rendererMock)).isTooltipExpandable(null))
                .isTrue();
        }

        @Test
        void answersNotExpandableWhenLiveTooltipDeclines() {

            var rendererMock = mock(KmuConditionTooltipRenderer.class);

            when(rendererMock.isTooltipExpandable())
                .thenReturn(false);

            assertThat(buildCreatorOver(stubEntryRenderedBy(rendererMock)).isTooltipExpandable(null))
                .isFalse();
        }
    }

    @Nested
    class GetTooltipWidth {

        @Test
        void takesWidthFromLiveTooltip() {

            var rendererMock = mock(KmuConditionTooltipRenderer.class);

            when(rendererMock.getTooltipWidth())
                .thenReturn(512f);

            assertThat(buildCreatorOver(stubEntryRenderedBy(rendererMock)).getTooltipWidth(null))
                .isEqualTo(512f);
        }
    }

    @Nested
    class CreateTooltip {

        @Test
        void drawsLiveTooltipInPlaceOfPickerTitleAndText() {

            var rendererMock = mock(KmuConditionTooltipRenderer.class);
            var tooltipMock = mock(TooltipMakerAPI.class);

            buildCreatorOver(stubEntryRenderedBy(rendererMock)).createTooltip(tooltipMock, true, null);

            verify(rendererMock)
                .createTooltip(tooltipMock, true);
            verify(tooltipMock, never())
                .addTitle(anyString());
            verify(tooltipMock, never())
                .addPara(eq(TOOLTIP_TEXT), any(Color.class), anyFloat());
        }

        @Test
        void appendsMetadataSectionAfterLiveTooltip() {

            var rendererMock = mock(KmuConditionTooltipRenderer.class);
            var tooltipMock = mock(TooltipMakerAPI.class);

            buildCreatorOver(stubEntryRenderedBy(rendererMock)).createTooltip(tooltipMock, false, null);

            var callOrder = inOrder(rendererMock, tooltipMock);

            callOrder
                .verify(rendererMock)
                .createTooltip(tooltipMock, false);

            callOrder
                .verify(tooltipMock)
                .addSectionHeading(
                    anyString(),
                    eq(MUTED_TITLE),
                    eq(MUTED_BACKGROUND),
                    eq(Alignment.MID),
                    eq(HEADING_PAD));
        }

        @Test
        void appendsStatusWarningsBetweenLiveTooltipAndMetadata() {

            // The live tooltip is the condition's own and says nothing of the market's suppression or
            // hiding, so the picker's warnings still have to follow it.
            var rendererMock = mock(KmuConditionTooltipRenderer.class);
            var entryMock = stubEntryRenderedBy(rendererMock);

            when(entryMock.isPresent())
                .thenReturn(true);
            when(entryMock.isSuppressed())
                .thenReturn(true);
            when(entryMock.isHidden())
                .thenReturn(true);

            var tooltipMock = mock(TooltipMakerAPI.class);

            buildCreatorOver(entryMock).createTooltip(tooltipMock, false, null);

            var callOrder = inOrder(rendererMock, tooltipMock);

            callOrder
                .verify(rendererMock)
                .createTooltip(tooltipMock, false);

            callOrder
                .verify(tooltipMock)
                .addSectionHeading(
                    "Suppressed",
                    WARNING_TITLE,
                    WARNING_BACKGROUND,
                    Alignment.MID,
                    HEADING_PAD);

            callOrder
                .verify(tooltipMock)
                .addSectionHeading(
                    "Hidden",
                    WARNING_TITLE,
                    WARNING_BACKGROUND,
                    Alignment.MID,
                    HEADING_PAD);

            callOrder
                .verify(tooltipMock)
                .addSectionHeading(
                    anyString(),
                    eq(MUTED_TITLE),
                    eq(MUTED_BACKGROUND),
                    eq(Alignment.MID),
                    eq(HEADING_PAD));
        }

        @Test
        void fallsBackToPickerTitleAndTextWhenLiveTooltipFails() {

            // A broken modded condition plugin must still leave the player a readable box.
            var rendererMock = mock(KmuConditionTooltipRenderer.class);

            doThrow(new IllegalStateException("plugin failed"))
                .when(rendererMock)
                .createTooltip(any(TooltipMakerAPI.class), anyBoolean());

            var tooltipMock = mock(TooltipMakerAPI.class);

            buildCreatorOver(stubEntryRenderedBy(rendererMock)).createTooltip(tooltipMock, false, null);

            var callOrder = inOrder(tooltipMock);

            callOrder
                .verify(tooltipMock)
                .addTitle(CONDITION_NAME);

            callOrder
                .verify(tooltipMock)
                .addPara(TOOLTIP_TEXT, StarsectorUiColoursMock.UI_TEXT, LINE_PAD);

            callOrder
                .verify(tooltipMock)
                .addSectionHeading(
                    anyString(),
                    eq(MUTED_TITLE),
                    eq(MUTED_BACKGROUND),
                    eq(Alignment.MID),
                    eq(HEADING_PAD));

        }
    }

    private static KmuConditionEntryTooltipCreator buildCreatorOver(KmuConditionPickerEntry entry) {

        return new KmuConditionEntryTooltipCreator(() -> entry);
    }

    // Stood in rather than built: the constructor taking a live renderer belongs to the model package, which
    // hands one only to an entry whose condition is on the market.
    private static KmuConditionPickerEntry stubEntryRenderedBy(KmuConditionTooltipRenderer renderer) {

        var entryMock = mock(KmuConditionPickerEntry.class);

        when(entryMock.getConditionId())
            .thenReturn("hot");
        when(entryMock.getName())
            .thenReturn(CONDITION_NAME);
        when(entryMock.getTooltipText())
            .thenReturn(TOOLTIP_TEXT);
        when(entryMock.getTooltipRenderer())
            .thenReturn(Optional.of(renderer));

        return entryMock;
    }
}
