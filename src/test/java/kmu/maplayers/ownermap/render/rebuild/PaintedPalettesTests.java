package kmu.maplayers.ownermap.render.rebuild;

import kmlib.colour.ColourPair;

import kmu.maplayers.ownermap.owners.OwnerReading;
import kmu.maplayers.ownermap.owners.SpotlitOwners;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.render.rebuild.PaintedPalettes.OwnerPalette;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what a build is sampled as having painted in - each distinct owner once, the spotlit footprint
 * under the picked owner - and how a frame finds that a shade has moved since: a recolour is a moved
 * answer from the reading the build was painted by.
 */
final class PaintedPalettesTests {

    private static final String HEGEMONY_ID = "hegemony";
    private static final String TRITACHYON_ID = "tritachyon";

    private static final ColourPair HEGEMONY_PALETTE = new ColourPair(
        new Color(10, 20, 30),
        new Color(40, 50, 60));

    private static final ColourPair TRITACHYON_PALETTE = new ColourPair(
        new Color(1, 2, 3),
        new Color(4, 5, 6));

    private static final ColourPair RECOLOURED_PALETTE = new ColourPair(
        new Color(70, 80, 90),
        new Color(100, 110, 120));

    private static final Color UNOWNED_COLOUR = new Color(128, 128, 128);

    private static final ColourPair RECEDE_PALETTE = new ColourPair(
        new Color(200, 200, 200),
        new Color(50, 50, 50));

    private OwnerReading readingMock;

    @BeforeEach
    void stubTheShadesTheBuildWasPaintedIn() {

        readingMock = mock(OwnerReading.class);

        when(readingMock.resolvePalette(HEGEMONY_ID))
            .thenReturn(HEGEMONY_PALETTE);
        when(readingMock.resolvePalette(TRITACHYON_ID))
            .thenReturn(TRITACHYON_PALETTE);
        when(readingMock.resolveUnownedColour())
            .thenReturn(UNOWNED_COLOUR);
        when(readingMock.resolveRecedePalette())
            .thenReturn(RECEDE_PALETTE);
    }

    @Nested
    class SampleFrom {

        // One owner holds many systems, and the per-frame walk asks about it once.
        @Test
        void holdsEachDistinctOwnerOnceWithTheShadesItsReadingAnswered() {

            // ARRANGE
            var expectedOwnerPalettes = List.of(
                new OwnerPalette(HEGEMONY_ID, HEGEMONY_PALETTE),
                new OwnerPalette(TRITACHYON_ID, TRITACHYON_PALETTE));

            var owners = List.of(
                new SystemOwner(HEGEMONY_ID, HEGEMONY_PALETTE),
                new SystemOwner(TRITACHYON_ID, TRITACHYON_PALETTE),
                new SystemOwner(HEGEMONY_ID, HEGEMONY_PALETTE));

            // ACT
            var actualPaintedPalettes = PaintedPalettes.sampleFrom(readingMock, owners, null);

            // ASSERT
            assertThat(actualPaintedPalettes.ownerPalettes())
                .isEqualTo(expectedOwnerPalettes);
        }

        // The spotlit key names no owner the reading knows, so the spotlit footprint is asked about
        // under the owner the sidebar picked.
        @Test
        void holdsTheSpotlitFootprintUnderThePickedOwner() {

            // ARRANGE
            var expectedOwnerPalettes = List.of(new OwnerPalette(HEGEMONY_ID, HEGEMONY_PALETTE));
            var owners = List.of(SpotlitOwners.createSpotlitOwner(HEGEMONY_PALETTE));

            // ACT
            var actualPaintedPalettes = PaintedPalettes.sampleFrom(readingMock, owners, HEGEMONY_ID);

            // ASSERT
            assertThat(actualPaintedPalettes.ownerPalettes())
                .isEqualTo(expectedOwnerPalettes);
        }

        // The two neutrals are painted in too, and a recolour of either is as visible as an owner's.
        @Test
        void holdsTheUnownedAndRecedeShadesItsReadingAnswered() {

            // ACT
            var actualPaintedPalettes = PaintedPalettes.sampleFrom(readingMock, List.of(), null);

            // ASSERT
            assertThat(actualPaintedPalettes.unownedColour())
                .isEqualTo(UNOWNED_COLOUR);
            assertThat(actualPaintedPalettes.recedePalette())
                .isEqualTo(RECEDE_PALETTE);
        }
    }

    @Nested
    class HasAnyPaletteMoved {

        // The frame the map spends nearly all of its life on.
        @Test
        void isFalseWhileEveryShadeHolds() {

            // ARRANGE
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            // ACT
            var actualHasMoved = paintedPalettes.hasAnyPaletteMoved();

            // ASSERT
            assertThat(actualHasMoved)
                .isFalse();
        }

        @Test
        void isTrueOnceAPaintedOwnerIsRecoloured() {

            // ARRANGE
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolvePalette(TRITACHYON_ID))
                .thenReturn(RECOLOURED_PALETTE);

            // ACT
            var actualHasMoved = paintedPalettes.hasAnyPaletteMoved();

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
        }

        @Test
        void isTrueOnceTheUnownedShadeMoves() {

            // ARRANGE
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolveUnownedColour())
                .thenReturn(new Color(90, 90, 90));

            // ACT
            var actualHasMoved = paintedPalettes.hasAnyPaletteMoved();

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
        }

        @Test
        void isTrueOnceTheRecedeShadesMove() {

            // ARRANGE
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolveRecedePalette())
                .thenReturn(RECOLOURED_PALETTE);

            // ACT
            var actualHasMoved = paintedPalettes.hasAnyPaletteMoved();

            // ASSERT
            assertThat(actualHasMoved)
                .isTrue();
        }
    }

    @Nested
    class DescribeMovedPalette {

        // The rebuild's line is the one place a recolour lands, so it names the owner and both pairs.
        @Test
        void namesTheRecolouredOwnerAndBothOfItsPairs() {

            // ARRANGE
            var expectedDescription = "owner tritachyon from (1,2,3,255 / 4,5,6,255)"
                + " to (70,80,90,255 / 100,110,120,255)";

            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolvePalette(TRITACHYON_ID))
                .thenReturn(RECOLOURED_PALETTE);

            // ACT
            var actualDescription = paintedPalettes.describeMovedPalette();

            // ASSERT
            assertThat(actualDescription)
                .isEqualTo(expectedDescription);
        }

        // An owner gone from the sector answers no shades, which the line writes as nothing.
        @Test
        void namesAnOwnerThatAnswersNoShadesAnyMore() {

            // ARRANGE
            var expectedDescription = "owner hegemony from (10,20,30,255 / 40,50,60,255) to nothing";
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolvePalette(HEGEMONY_ID))
                .thenReturn(null);

            // ACT
            var actualDescription = paintedPalettes.describeMovedPalette();

            // ASSERT
            assertThat(actualDescription)
                .isEqualTo(expectedDescription);
        }

        @Test
        void namesTheUnownedShadeWhenItMoves() {

            // ARRANGE
            var expectedDescription = "unowned from 128,128,128,255 to 90,90,90,255";
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolveUnownedColour())
                .thenReturn(new Color(90, 90, 90));

            // ACT
            var actualDescription = paintedPalettes.describeMovedPalette();

            // ASSERT
            assertThat(actualDescription)
                .isEqualTo(expectedDescription);
        }

        @Test
        void namesTheRecedeShadesWhenTheyMove() {

            // ARRANGE
            var expectedDescription = "recede from (200,200,200,255 / 50,50,50,255)"
                + " to (70,80,90,255 / 100,110,120,255)";

            var paintedPalettes = sampleHegemonyAndTriTachyon();

            when(readingMock.resolveRecedePalette())
                .thenReturn(RECOLOURED_PALETTE);

            // ACT
            var actualDescription = paintedPalettes.describeMovedPalette();

            // ASSERT
            assertThat(actualDescription)
                .isEqualTo(expectedDescription);
        }

        @Test
        void namesNothingWhileEveryShadeHolds() {

            // ARRANGE
            var expectedDescription = "none";
            var paintedPalettes = sampleHegemonyAndTriTachyon();

            // ACT
            var actualDescription = paintedPalettes.describeMovedPalette();

            // ASSERT
            assertThat(actualDescription)
                .isEqualTo(expectedDescription);
        }
    }

    // A build that painted one Hegemony system and one Tri-Tachyon system, nothing spotlit.
    private PaintedPalettes sampleHegemonyAndTriTachyon() {

        return PaintedPalettes.sampleFrom(
            readingMock,
            List.of(
                new SystemOwner(HEGEMONY_ID, HEGEMONY_PALETTE),
                new SystemOwner(TRITACHYON_ID, TRITACHYON_PALETTE)),
            null);
    }
}
