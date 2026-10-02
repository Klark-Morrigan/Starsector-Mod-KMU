package kmu.maplayers.base.tooltip.layout;

import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.testfixtures.starsector.ui.font.FaceLineHeightReaderFake;
import kmlib.testfixtures.starsector.ui.font.installed.LazyFontLineHeightReaderMock;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;
import kmu.maplayers.base.tooltip.CellTooltipPaletteFake;
import kmu.settings.KmuMapTooltipSettings;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Pins which kinds of text each of the box's faces is settled against: the heading titles a system, the
 * body lists factions and places among KMU's own words, and the foot is KMU's words alone. A face held to
 * the wrong kinds would move for text it never draws, or stay put over text it cannot.
 */
final class CellTooltipLookTests {

    // Faces the settled answers stand as, each distinct so a face settled against the wrong kinds shows
    // as the wrong face rather than as the right one by coincidence.
    private static final StarsectorFont SETTLED_HEADER_FACE = StarsectorFont.VANILLA_INSIGNIA_21;
    private static final StarsectorFont SETTLED_BODY_FACE = StarsectorFont.VANILLA_INSIGNIA_25;
    private static final StarsectorFont SETTLED_FOOTNOTE_FACE = StarsectorFont.VANILLA_VICTOR_10;

    // The density knobs the look reads, at any values it accepts.
    private static final float LINE_GAP = 6f;
    private static final float LEADER_THICKNESS = 2f;
    private static final float LEADER_OPACITY = 0.4f;

    private MockedStatic<KmuMapTooltipSettings> settingsMock;
    private LazyFontLineHeightReaderMock lineHeightsMock;

    @BeforeEach
    void installLiveReads() {

        CellTooltipPaletteFake.installPalette();
        lineHeightsMock = LazyFontLineHeightReaderMock.install(FaceLineHeightReaderFake.createVanillaLineHeights());
        settingsMock = mockStatic(KmuMapTooltipSettings.class);

        settingsMock.when(KmuMapTooltipSettings::getMapTooltipLineGap)
            .thenReturn(LINE_GAP);
        settingsMock.when(KmuMapTooltipSettings::getMapTooltipLeaderThickness)
            .thenReturn(LEADER_THICKNESS);
        settingsMock.when(KmuMapTooltipSettings::getMapTooltipLeaderOpacity)
            .thenReturn(LEADER_OPACITY);
    }

    @AfterEach
    void clearLiveReads() {

        settingsMock.close();
        lineHeightsMock.close();
        CellTooltipPaletteFake.clearPalette();
    }

    // Faces settled as the localised install settles them, each distinct from the face asked for.
    private static SettledFaces createSettlingFacesMock() {

        var settledFacesMock = mock(SettledFaces.class);

        when(settledFacesMock.settleFace(
                StarsectorFont.VANILLA_ORBITRON_20AA,
                Set.of(ProbedText.PLACE_NAMES, ProbedText.MOD_STRINGS)))
            .thenReturn(SETTLED_HEADER_FACE);
        when(settledFacesMock.settleFace(StarsectorFont.VANILLA_INSIGNIA_15, ProbedText.EVERY_KIND))
            .thenReturn(SETTLED_BODY_FACE);
        when(settledFacesMock.settleFace(
                StarsectorFont.VANILLA_ORBITRON_12_CONDENSED,
                Set.of(ProbedText.MOD_STRINGS)))
            .thenReturn(SETTLED_FOOTNOTE_FACE);

        return settledFacesMock;
    }

    @Nested
    final class BuildStyle {

        @Test
        void settlesEachFaceAgainstTheKindsItsLinesAreMadeOf() {
            // Each settled face answers only for the kinds its lines are made of, so one asked against other
            // kinds answers as the face asked for and fails the case.
            var typography = CellTooltipLook.buildStyle(createSettlingFacesMock()).typography();

            assertThat(typography.headerStyle().face().atlas())
                .isEqualTo(SETTLED_HEADER_FACE);
            assertThat(typography.paragraphStyle().face().atlas())
                .isEqualTo(SETTLED_BODY_FACE);
            assertThat(typography.footnoteStyle().face().atlas())
                .isEqualTo(SETTLED_FOOTNOTE_FACE);
        }

        @Test
        void drawsTheHeadingAndTheBodyAtTheSizeTheirSettledAtlasesState() {
            // A bitmap face is crisp at its own atlas's size alone, so a face settled elsewhere is drawn at
            // that atlas's size - 21 and 24 here - rather than at the 20 and 15 the faces asked for state.
            var typography = CellTooltipLook.buildStyle(createSettlingFacesMock()).typography();

            assertThat(typography.headerStyle().face().size())
                .isEqualTo(21d);
            assertThat(typography.paragraphStyle().face().size())
                .isEqualTo(24d);
        }

        @Test
        void drawsTheFootAtTheBodysSettledSize() {
            // The foot's own atlas reads as fine print beside the body, so it takes the body's size - the
            // settled body's 24, not the 15 the body face asked for states, nor the foot atlas's own 9.
            var typography = CellTooltipLook.buildStyle(createSettlingFacesMock()).typography();

            assertThat(typography.footnoteStyle().face().size())
                .isEqualTo(24d);
        }
    }
}
