package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmu.starsector.StarsectorSettingsFake;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins what the box says at its head and its foot, the two places its wording has to answer for
 * something the player cannot see anywhere else.
 *
 * <p>The head is held for its runs rather than its sentence alone. A highlight the substrate cannot
 * find in the text is simply not drawn, and nothing reports it - so the words picked out are pinned
 * against the same strings the controls are labelled from, which is what makes a reworded button
 * carry its highlight with it.
 *
 * <p>The foot is held because its word is a claim about the box: nothing in it is held back to be
 * committed, so the way out names what the player leaves with. A button that said Close would say
 * the arrangement had merely been abandoned.
 */
final class MapLayerArrangementDialogBodyTest {

    // The shade a failing assertion can tell from the default, standing in for the engine's own
    // highlight. Every unnamed key answers with one default, which would make a highlighted run
    // indistinguishable from an untinted one.
    private static final Color HIGHLIGHT = new Color(255, 220, 80);

    // The engine's own key for the shade it highlights a run in.
    private static final String HIGHLIGHT_COLOUR_KEY = "buttonShortcut";

    @BeforeEach
    void installStarsectorSettings() {

        StarsectorSettingsFake.installSettings(key -> HIGHLIGHT_COLOUR_KEY.equals(key)
            ? HIGHLIGHT
            : null);
    }

    @AfterEach
    void clearStarsectorSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class FillHeader {

        @Test
        void fillHeaderNamesTheBoxInTheFaceTheGameHeadsItsOwnWith() {

            var headerMock = mock(TooltipMakerAPI.class);
            mockLabelOn(headerMock);

            MapLayerArrangementDialogBody.fillHeader(headerMock);

            // The larger face is the whole of what makes this a heading: at the element's default
            // title size it is the same size as the line under it.
            verify(headerMock).setTitleOrbitronLarge();
            verify(headerMock).addTitle("Arrange Layer Bar");
        }

        @Test
        void fillHeaderPartsTheHintFromTheHeadingByAStatedGap() {

            var headerMock = mock(TooltipMakerAPI.class);
            mockLabelOn(headerMock);

            MapLayerArrangementDialogBody.fillHeader(headerMock);

            verify(headerMock)
                .addPara(
                    eq("Move a layer with Up and Down, or uncheck its box to take its tab off the "
                        + "bar."),
                    any(Color.class),
                    eq(10f));
        }

        @Test
        void fillHeaderPicksOutOnlyTheWordsThatNameAControl() {

            var headerMock = mock(TooltipMakerAPI.class);
            var labelMock = mockLabelOn(headerMock);

            MapLayerArrangementDialogBody.fillHeader(headerMock);

            // The two buttons a row carries and the box beside them, and nothing else in the
            // sentence: a hint is skimmed after its first reading, and a skim should land on the
            // words that name something on screen.
            verify(labelMock)
                .setHighlight("Up", "Down", "uncheck");

            verify(labelMock)
                .setHighlightColors(HIGHLIGHT, HIGHLIGHT, HIGHLIGHT);
        }
    }

    @Nested
    class FillApplyFooter {

        @Test
        void fillApplyFooterNamesWhatThePlayerIsLeavingWith() {

            var footerMock = mock(TooltipMakerAPI.class);

            MapLayerArrangementDialogBody.fillApplyFooter(footerMock);

            verify(footerMock)
                .addButton(eq("Apply"), any(), anyFloat(), anyFloat(), anyFloat());
        }
    }

    // The label a paragraph's highlights are set on. The engine hands one back from addPara and the
    // runs are routed through it, so a surface that answers with nothing has nothing to tint.
    private static LabelAPI mockLabelOn(TooltipMakerAPI tooltipMock) {

        var labelMock = mock(LabelAPI.class);

        when(tooltipMock.addPara(anyString(), any(Color.class), anyFloat()))
            .thenReturn(labelMock);

        return labelMock;
    }
}
