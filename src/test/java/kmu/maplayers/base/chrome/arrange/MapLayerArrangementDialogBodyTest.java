package kmu.maplayers.base.chrome.arrange;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmu.starsector.StarsectorUiColoursMock;
import kmu.starsector.ui.ParagraphLabelMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

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
 *
 * <p>A row is held for the opposite reason: it says nothing, and shows its state instead. So what is
 * pinned there is the shade its name takes and the absence of a word in its box - two things a reader
 * of the class cannot tell from a widget call that would still compile either way.
 */
final class MapLayerArrangementDialogBodyTest {

    // The engine's palette, which also answers the wording lookups the head and foot make. Held for
    // the shade a highlighted run takes: the assertions below tell it from the untinted body text,
    // and which shade stands for which role is settled once there rather than per case.
    private StarsectorUiColoursMock uiColoursMock;

    @BeforeEach
    void installStarsectorUiColours() {

        uiColoursMock = StarsectorUiColoursMock.install();
    }

    @AfterEach
    void clearStarsectorUiColours() {

        uiColoursMock.close();
    }

    @Nested
    class FillHeader {

        @Test
        void fillHeaderNamesTheBoxInTheFaceTheGameHeadsItsOwnWith() {

            var headerMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(headerMock);

            MapLayerArrangementDialogBody.fillHeader(headerMock);

            // The larger face is the whole of what makes this a heading: at the element's default
            // title size it is the same size as the line under it.
            verify(headerMock).setTitleOrbitronLarge();
            verify(headerMock).addTitle("Arrange Layer Bar");
        }

        @Test
        void fillHeaderPartsTheHintFromTheHeadingByAStatedGap() {

            var headerMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(headerMock);

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
            var labelMock = ParagraphLabelMock.mockLabelOn(headerMock);

            MapLayerArrangementDialogBody.fillHeader(headerMock);

            // The two buttons a row carries and the box beside them, and nothing else in the
            // sentence: a hint is skimmed after its first reading, and a skim should land on the
            // words that name something on screen.
            verify(labelMock)
                .setHighlight("Up", "Down", "uncheck");

            verify(labelMock)
                .setHighlightColors(
                    StarsectorUiColoursMock.HIGHLIGHT_GOLD,
                    StarsectorUiColoursMock.HIGHLIGHT_GOLD,
                    StarsectorUiColoursMock.HIGHLIGHT_GOLD);
        }
    }

    @Nested
    class FillFooter {

        @Test
        void fillFooterNamesWhatThePlayerIsLeavingWith() {

            var footerMock = mock(TooltipMakerAPI.class);

            MapLayerArrangementDialogBody.fillFooter(footerMock);

            verify(footerMock)
                .addButton(eq("Apply"), any(), anyFloat(), anyFloat(), anyFloat());
        }
    }

    @Nested
    class AddRowLabel {

        @Test
        void addRowLabelMutesARowWhoseTabIsOffTheBar() {

            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            MapLayerArrangementDialogBody.addRowLabel(
                labelCellMock,
                new MapLayerArrangementRow("kmu_political", "Political", true));

            verify(labelCellMock)
                .addPara(eq("Political"), eq(StarsectorUiColoursMock.UI_GRAY), anyFloat());
        }

        @Test
        void addRowLabelDrawsARowWhoseTabIsOnTheBarInTheOrdinaryShade() {

            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            MapLayerArrangementDialogBody.addRowLabel(
                labelCellMock,
                new MapLayerArrangementRow("kmu_political", "Political", false));

            verify(labelCellMock)
                .addPara(eq("Political"), eq(StarsectorUiColoursMock.UI_TEXT), anyFloat());
        }

        @Test
        void addRowLabelDrawsTheLastRowLeftOnTheBarUnmuted() {
            // The row whose box the editor refuses, that box being the one that would empty the bar.
            // Refused and off the bar are different things, and the shade answers the second: a row
            // that cannot be taken off is still on.
            var labelCellMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(labelCellMock);

            MapLayerArrangementDialogBody.addRowLabel(
                labelCellMock,
                new MapLayerArrangementRow("kmu_political", "Political", false));

            verify(labelCellMock)
                .addPara(eq("Political"), eq(StarsectorUiColoursMock.UI_TEXT), anyFloat());
        }
    }

    @Nested
    class AddShownBox {

        @Test
        void addShownBoxCarriesNoWord() {
            // The hint over the column already says what the box does, so a word in the box would be
            // that sentence repeated once per row.
            var controlsCellMock = mock(TooltipMakerAPI.class);

            MapLayerArrangementDialogBody.addShownBox(controlsCellMock);

            verify(controlsCellMock)
                .addAreaCheckbox(
                    eq(""),
                    any(),
                    any(Color.class),
                    any(Color.class),
                    any(Color.class),
                    anyFloat(),
                    anyFloat(),
                    anyFloat());
        }

        @Test
        void addShownBoxStandsSquareAtTheRowsControlHeight() {
            // With the word gone the box has nothing to be wide for, and a box wider than it is tall
            // would read as a button whose label failed to load.
            var controlsCellMock = mock(TooltipMakerAPI.class);

            MapLayerArrangementDialogBody.addShownBox(controlsCellMock);

            verify(controlsCellMock)
                .addAreaCheckbox(
                    any(),
                    any(),
                    any(Color.class),
                    any(Color.class),
                    any(Color.class),
                    eq(20f),
                    eq(20f),
                    anyFloat());
        }
    }
}
