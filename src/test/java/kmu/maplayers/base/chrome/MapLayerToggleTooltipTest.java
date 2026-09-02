package kmu.maplayers.base.chrome;

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
 * Pins what the box on the game's filter row says, and which of its words stand out.
 *
 * <p>The runs and their colours are held in order, because that order is the whole of the binding:
 * the substrate matches a colour to a highlighted run by position, so a run inserted or dropped
 * without its colour repaints the rest of the sentence rather than failing.
 *
 * <p>The alliances clause is held on both sides of its condition. It names a view the political map
 * only offers where the mod that keeps alliances is installed, so an install without it must be
 * told about the two views it has rather than the three it does not.
 */
final class MapLayerToggleTooltipTest {

    // Two shades a failing assertion can tell apart, standing in for the engine's own. Named here
    // because what the sentence turns on is which run recedes, and every unnamed key answers with
    // one default shade that would make the aside and its neighbours indistinguishable.
    private static final Color HIGHLIGHT = new Color(255, 220, 80);
    private static final Color GRAY = new Color(155, 155, 155);

    // The engine's own keys for the two roles: the shade a highlighted run takes, and the shade
    // vanilla writes an aside in.
    private static final String HIGHLIGHT_COLOUR_KEY = "buttonShortcut";
    private static final String GRAY_COLOUR_KEY = "textGrayColor";

    @BeforeEach
    void installStarsectorSettings() {

        StarsectorSettingsFake.installSettings(key -> switch (key) {
            case HIGHLIGHT_COLOUR_KEY -> HIGHLIGHT;
            case GRAY_COLOUR_KEY -> GRAY;
            default -> null;
        });
    }

    @AfterEach
    void clearStarsectorSettings() {

        StarsectorSettingsFake.clearSettings();
    }

    @Nested
    class DescribeToggle {

        @Test
        void describeToggleHighlightsEveryRunButTheAsideNamingTheMod() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock);

            verify(tooltipMock)
                .addPara(
                    eq("Shows Sector Map Layers (supplied by KMU) that draw a political map of the "
                        + "sector (factions, alliances, system claims)."),
                    any(Color.class),
                    anyFloat());

            verify(labelMock)
                .setHighlight(
                    "Sector Map Layers",
                    "(supplied by KMU)",
                    "political map",
                    "factions",
                    "alliances",
                    "system claims");

            // The aside is the one run in gray: the box is dressed as the game's own furniture, so
            // the only place it can say who put it there is here, and saying it in the colour the
            // feature's own words take would read as part of the feature's name.
            verify(labelMock)
                .setHighlightColors(
                    HIGHLIGHT,
                    GRAY,
                    HIGHLIGHT,
                    HIGHLIGHT,
                    HIGHLIGHT,
                    HIGHLIGHT);
        }

        @Test
        void describeToggleLeavesTheAlliancesViewOutWhereItIsNotOffered() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> false).describeToggle(tooltipMock);

            // One clause and one run fewer, and the colours shorten with them - a colour list still
            // sized for six would tint the closing bracket of a five-run sentence.
            verify(tooltipMock)
                .addPara(
                    eq("Shows Sector Map Layers (supplied by KMU) that draw a political map of the "
                        + "sector (factions, system claims)."),
                    any(Color.class),
                    anyFloat());

            verify(labelMock)
                .setHighlight(
                    "Sector Map Layers",
                    "(supplied by KMU)",
                    "political map",
                    "factions",
                    "system claims");

            verify(labelMock)
                .setHighlightColors(
                    HIGHLIGHT,
                    GRAY,
                    HIGHLIGHT,
                    HIGHLIGHT,
                    HIGHLIGHT);
        }
    }

    // The label the paragraph is tinted through. A tooltip that answered none would leave the runs
    // nowhere to land, which is not a state the engine ever hands a body.
    private static LabelAPI mockLabelOn(TooltipMakerAPI tooltipMock) {

        var labelMock = mock(LabelAPI.class);

        when(tooltipMock.addPara(anyString(), any(Color.class), anyFloat()))
            .thenReturn(labelMock);

        return labelMock;
    }
}
