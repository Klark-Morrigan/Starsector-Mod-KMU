package kmu.maplayers.base.tooltip;

import kmlib.starsector.entities.EntityMapIcon;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Pins the two readings a mark can take and the absence they share. Which one a mark is cannot be read
 * off its texture, so it is stated where the path is named - and these are the cases that hold the two
 * apart: a picture in its own right keeps its pixels, while a shorthand for the name beside it follows
 * that name's colour.
 *
 * <p>The absence is the other half. Every factory answers no mark for a subject the game marked with
 * nothing, so a caller resolving one it may not have never branches first, and a mark that does exist
 * always has something to load.
 */
final class CellTooltipMarkTest {

    private static final String CREST = "graphics/hegemony_crest.png";
    private static final String COLONY_ICON = "graphics/warroom/icon_planet.png";

    // A path the game never supplied, which is what a faction with no crest and an entity the map marks
    // with no glyph both hand over.
    private static final String NO_PATH = null;

    @Nested
    class ResolveMarkAsAuthored {

        @Test
        void resolveMarkAsAuthoredKeepsTheAssetsOwnColours() {
            // A crest is a picture of a thing rather than a shorthand for it, and its colours are in
            // its own pixels - drawn in the line's shade it would come out a tinted smudge.
            var mark = CellTooltipMark.resolveMarkAsAuthored(CREST);

            assertThat(mark)
                .isEqualTo(new CellTooltipMark(CREST, false));
        }

        @Test
        void resolveMarkAsAuthoredAnswersNoMarkForAPathTheGameDidNotSupply() {
            assertThat(CellTooltipMark.resolveMarkAsAuthored(NO_PATH))
                .isNull();
        }
    }

    @Nested
    class ResolveMarkInLineColour {

        @Test
        void resolveMarkInLineColourFollowsTheNameBesideIt() {
            // It names no colour of its own: what a line speaks in is the block's to settle and differs
            // by the tier the line lands at, so the mark states only that it follows its name.
            var mark = CellTooltipMark.resolveMarkInLineColour(COLONY_ICON);

            assertThat(mark)
                .isEqualTo(new CellTooltipMark(COLONY_ICON, true));
        }

        @Test
        void resolveMarkInLineColourAnswersNoMarkForAPathTheGameDidNotSupply() {
            // The same absence the other reading answers, so a line showing nothing at its head is one
            // state however the mark was resolved.
            assertThat(CellTooltipMark.resolveMarkInLineColour(NO_PATH))
                .isNull();
        }

        @Test
        void resolveMarkInLineColourIsTheOnlyThingSeparatingItFromAnAuthoredMark() {
            // The same texture can be a subject on one line and a shorthand on the next, which is why
            // the colouring is stated here rather than read off the sprite.
            assertThat(CellTooltipMark.resolveMarkInLineColour(COLONY_ICON))
                .isNotEqualTo(CellTooltipMark.resolveMarkAsAuthored(COLONY_ICON));
        }
    }

    @Nested
    class ResolveMarkForMapIcon {

        @Test
        void resolveMarkForMapIconFollowsTheNameBesideIt() {
            // A map glyph stands in for the name it prefixes rather than picturing anything, so it
            // reads in the line's colour - vanilla's own shade is authored to carry against black and
            // arrives brighter than the numbers a box is about.
            var mark = CellTooltipMark.resolveMarkForMapIcon(
                Optional.of(new EntityMapIcon(COLONY_ICON, new Color(120, 200, 90))));

            assertThat(mark)
                .isEqualTo(new CellTooltipMark(COLONY_ICON, true));
        }

        @Test
        void resolveMarkForMapIconSpendsNoneOfTheAuthoredColour() {
            // The asset's colour is KMLib's answer about the asset and a surface drawing the map still
            // wants it; a mark carries no colour of its own, so nothing of it can reach the line.
            var authored = CellTooltipMark.resolveMarkForMapIcon(
                Optional.of(new EntityMapIcon(COLONY_ICON, new Color(120, 200, 90))));

            var uncoloured = CellTooltipMark.resolveMarkForMapIcon(
                Optional.of(new EntityMapIcon(COLONY_ICON, null)));

            assertThat(authored)
                .isEqualTo(uncoloured);
        }

        @Test
        void resolveMarkForMapIconAnswersNoMarkForAnEntityTheMapMarksWithNothing() {
            // The same absence a path the game never supplied answers, so a caller lists a marked and
            // an unmarked subject through one expression rather than branching first.
            assertThat(CellTooltipMark.resolveMarkForMapIcon(Optional.empty()))
                .isNull();
        }
    }

    @Nested
    class Constructor {

        @Test
        void constructorRefusesAMarkWithNothingToLoad() {
            // A line showing no mark carries no mark at all, so a null reaching here would otherwise
            // surface at the texture lookup inside a draw, past the point that could name the line.
            assertThatThrownBy(() -> new CellTooltipMark(NO_PATH, true))
                .isInstanceOf(NullPointerException.class);
        }
    }
}
