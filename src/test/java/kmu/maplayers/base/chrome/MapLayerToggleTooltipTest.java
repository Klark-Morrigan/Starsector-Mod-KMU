package kmu.maplayers.base.chrome;

import com.fs.starfarer.api.ui.TooltipMakerAPI;

import kmu.starsector.StarsectorSettingsFake;
import kmu.starsector.ui.ParagraphLabelMock;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyFloat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Pins what the box on the game's filter row says, and which of its words stand out.
 *
 * <p>The runs and their colours are held in order, because that order is the whole of the binding:
 * the substrate matches a colour to a highlighted run by position, so a run inserted or dropped
 * without its colour repaints the rest of the sentence rather than failing.
 *
 * <p>The alliances clause is held on both sides of its condition. It names a view the political map
 * only offers where the mod that keeps alliances is installed, so an install without it must be
 * told about the two views it has rather than the three it does not. Held on both sides of a
 * <em>change</em> too: the engine rebuilds a body while the tooltip is up, so what the sentence
 * lists has to be a read rather than something settled when the box went up.
 *
 * <p>The second paragraph is held for its two colours rather than its wording alone. What it tells a
 * player is an order of operations for getting the feature back out of a save, and the steps of that
 * order are what the positive shade marks - so a step tinted like the mods it is carried out in
 * would leave the instruction reading as a list of names.
 */
final class MapLayerToggleTooltipTest {

    // Shades a failing assertion can tell apart, standing in for the engine's own. Named here
    // because what the paragraphs turn on is which runs are named things and which are steps, and
    // every unnamed key answers with one default shade that would make the two indistinguishable.
    private static final Color HIGHLIGHT = new Color(255, 220, 80);
    private static final Color POSITIVE = new Color(120, 220, 120);
    private static final Color NEGATIVE = new Color(220, 80, 80);

    // The engine's own keys for the three roles: the shade a highlighted run takes, the shade it
    // writes something favourable in, and the shade it keeps for bad news.
    private static final String HIGHLIGHT_COLOUR_KEY = "buttonShortcut";
    private static final String POSITIVE_COLOUR_KEY = "textFriendColor";
    private static final String NEGATIVE_COLOUR_KEY = "textEnemyColor";

    @BeforeEach
    void installStarsectorSettings() {

        StarsectorSettingsFake.installSettings(key -> switch (key) {
            case HIGHLIGHT_COLOUR_KEY -> HIGHLIGHT;
            case POSITIVE_COLOUR_KEY -> POSITIVE;
            case NEGATIVE_COLOUR_KEY -> NEGATIVE;
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
        void describeToggleNamesTheLayersTheMapAndEveryViewItOffers() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = ParagraphLabelMock.mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock);

            verify(tooltipMock)
                .addPara(
                    eq("Shows Sector Map Layers that draw a Political Map (Factions, Alliances, "
                        + "system Claims) over the sector map."),
                    any(Color.class),
                    anyFloat());

            // "system" is not part of what the view is called, so the qualifier stays untinted while
            // the name inside it does not.
            verify(labelMock)
                .setHighlight(
                    "Sector Map Layers",
                    "Political Map",
                    "Factions",
                    "Alliances",
                    "Claims");

            verify(labelMock)
                .setHighlightColors(HIGHLIGHT, HIGHLIGHT, HIGHLIGHT, HIGHLIGHT, HIGHLIGHT);
        }

        @Test
        void describeToggleNamesTheModAnswerableForTheFeatureAndNothingElse() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = ParagraphLabelMock.mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock);

            // Attribution alone. What to do about it belongs where the reason for doing it is,
            // which is the paragraph below rather than this one.
            verify(tooltipMock)
                .addPara(
                    eq("This feature is provided by the KMU mod."),
                    any(Color.class),
                    anyFloat());

            verify(labelMock)
                .setHighlight("KMU");

            verify(labelMock)
                .setHighlightColors(HIGHLIGHT);
        }

        @Test
        void describeToggleFlagsTheWarningThenGivesTheStepsThatAvoidIt() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = ParagraphLabelMock.mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> true).describeToggle(tooltipMock);

            verify(tooltipMock)
                .addPara(
                    eq("Warning: save game loading will produce an error if KMU is disabled without "
                        + "uninstalling Sector Map Layers from a save beforehand. To do so, disable "
                        + "this feature in LunaLib mod settings and save your game."),
                    any(Color.class),
                    anyFloat());

            verify(labelMock)
                .setHighlight(
                    "Warning:",
                    "KMU",
                    "uninstalling",
                    "Sector Map Layers",
                    "disable this feature",
                    "LunaLib",
                    "save your game");

            // The flag in the shade for bad news, then names and steps alternating through the
            // warning and on into the procedure - one vocabulary across the whole paragraph, so a
            // player skimming the positive runs reads the procedure in order.
            verify(labelMock)
                .setHighlightColors(
                    NEGATIVE, HIGHLIGHT, POSITIVE, HIGHLIGHT, POSITIVE, HIGHLIGHT, POSITIVE);
        }

        @Test
        void describeToggleLeavesTheAlliancesViewOutWhereItIsNotOffered() {

            var tooltipMock = mock(TooltipMakerAPI.class);
            var labelMock = ParagraphLabelMock.mockLabelOn(tooltipMock);

            new MapLayerToggleTooltip(() -> false).describeToggle(tooltipMock);

            // One clause and one run fewer, and the colours shorten with them - a colour list still
            // sized for five would tint the closing bracket of a four-run sentence.
            verify(tooltipMock)
                .addPara(
                    eq("Shows Sector Map Layers that draw a Political Map (Factions, system Claims) "
                        + "over the sector map."),
                    any(Color.class),
                    anyFloat());

            verify(labelMock)
                .setHighlight("Sector Map Layers", "Political Map", "Factions", "Claims");

            verify(labelMock)
                .setHighlightColors(HIGHLIGHT, HIGHLIGHT, HIGHLIGHT, HIGHLIGHT);
        }

        @Test
        void describeToggleAsksWhichViewsAreOfferedAfreshOnEveryHover() {

            var isAllianceViewOffered = new AtomicBoolean(false);
            var tooltip = new MapLayerToggleTooltip(isAllianceViewOffered::get);

            var firstTooltipMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(firstTooltipMock);
            tooltip.describeToggle(firstTooltipMock);

            isAllianceViewOffered.set(true);

            var secondTooltipMock = mock(TooltipMakerAPI.class);
            ParagraphLabelMock.mockLabelOn(secondTooltipMock);
            tooltip.describeToggle(secondTooltipMock);

            // The engine rebuilds a body while the tooltip is up, so anything read once at
            // construction would go on describing the roster as it stood when the box was hung -
            // and a mod loaded into a running game is exactly when that would be wrong.
            verify(secondTooltipMock)
                .addPara(
                    eq("Shows Sector Map Layers that draw a Political Map (Factions, Alliances, "
                        + "system Claims) over the sector map."),
                    any(Color.class),
                    anyFloat());
        }
    }
}
