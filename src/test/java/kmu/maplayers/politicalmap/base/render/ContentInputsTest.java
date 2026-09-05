package kmu.maplayers.politicalmap.base.render;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.politicalmap.base.FactionNameFormatChoice;
import kmu.maplayers.politicalmap.base.NameFormatPreference;
import kmu.maplayers.politicalmap.base.PoliticalMapView;
import kmu.maplayers.politicalmap.base.RecedePreferences;
import kmu.maplayers.politicalmap.base.UninhabitedOutlinePreference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the two things the bake's reading of the sidebar preferences has to get right.
 *
 * <p>The first is what it reads. Each pick is stored per save under a key of its own, and one of
 * them - the spotlight - is stored per view, so a reading that crossed two of those keys would
 * paint the map by a pick the player made somewhere else. The picks are written here through the
 * very holders the sidebar writes them through, over a memory that really stores, so a value
 * written under one key and read under another fails rather than passing on two stubs that agree.
 *
 * <p>The second is its equality, and everything the rebuild decision rests on is that: a reading
 * equal to the one the map was baked under is a reading the map is entitled to skip the rebuild
 * for. Each pick is separated on its own, because each of them changes what is baked and none of
 * them moves the settings revision.
 */
final class ContentInputsTest {

    // The view whose picker the spotlight is read under. Its id is what scopes the pick, so a
    // second view would read a slot of its own - which is FilterSelection's own suite to pin.
    private static final String VIEW_ID = "factions";
    private static final String SPOTLIT_BLOC_ID = "hegemony";

    // What a recede set resolves to on an untouched save: recoloured at full opacity, since
    // Desaturate defaults on and Mute defaults off. A literal, so a flip of either default breaks
    // this suite rather than silently changing what the bake is keyed on.
    private static final ElementStyleAdjustment DESATURATING = new ElementStyleAdjustment(1.0, true);

    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);

    // The save the picks are written into and read back out of. Holds the stand-in for the game's
    // static entry point open, so it is closed per case.
    private SectorMemoryFake sectorMemoryFake;

    // The board the sidebar's writers raise on. Nothing here reads it - what a flip raises is the
    // holders' own suites to pin - but every writer takes one.
    private MapLayerRefreshBoard board;

    @BeforeEach
    void openTheSave() {
        // The board first, and not for tidiness: its logger is resolved once for the life of the
        // JVM, on first use of the class, and a resolution taken while the game's static entry
        // point is stood in for hands it a null that faults every later suite that logs a line.
        board = new MapLayerRefreshBoard();
        sectorMemoryFake = new SectorMemoryFake();

        when(viewMock.getId())
            .thenReturn(VIEW_ID);
    }

    @AfterEach
    void closeTheSave() {
        sectorMemoryFake.close();
    }

    @Nested
    class SampleForView {

        @Test
        void sampleForViewReadsTheSpotlightPickMadeOnTheViewsOwnPicker() {
            // The spotlight is the one pick stored per view, so it is read under the view being
            // painted rather than under whatever the map happens to hold elsewhere.
            FilterSelection.selectId(VIEW_ID, SPOTLIT_BLOC_ID, board);

            assertThat(ContentInputs.sampleForView(viewMock).selectedBlocId())
                .isEqualTo(SPOTLIT_BLOC_ID);
        }

        @Test
        void sampleForViewRecedesNothingWhileNothingIsSpotlighted() {
            // Off filter there is no spotlight for a backdrop to sit behind, so the recede is the
            // identity however the toggles are set - which is what keeps a Mute or Desaturate flip
            // made with nothing spotlighted from rebuilding a map it would not change. The
            // desaturate default is on, so this is the recede being gated and not merely absent.
            var inputs = ContentInputs.sampleForView(viewMock);

            assertThat(inputs.selectedBlocId())
                .isNull();
            assertThat(inputs.filterRecedeAdjustment())
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void sampleForViewTakesTheFilterRecedeWhileABlocIsSpotlighted() {
            // With a bloc spotlit the rest of the sector recedes by the filter set's own toggles,
            // resolved here rather than by whoever paints, so the pick and the recede it implies
            // are one reading.
            FilterSelection.selectId(VIEW_ID, SPOTLIT_BLOC_ID, board);

            assertThat(ContentInputs.sampleForView(viewMock).filterRecedeAdjustment())
                .isEqualTo(DESATURATING);
        }

        @Test
        void sampleForViewReadsTheAllianceRecedeApartFromTheFilterOne() {
            // The two backdrops are tuned independently, so they must come off separate keys: with
            // the alliance set cleared and a bloc spotlit, one reading has to answer the identity
            // and the other the recolour. Read from one key they would answer alike.
            RecedePreferences.ALLIANCE_NON_ALLIED.setDesaturated(false, board);
            FilterSelection.selectId(VIEW_ID, SPOTLIT_BLOC_ID, board);

            var inputs = ContentInputs.sampleForView(viewMock);

            assertThat(inputs.allianceRecedeAdjustment())
                .isEqualTo(ElementStyleAdjustment.NONE);
            assertThat(inputs.filterRecedeAdjustment())
                .isEqualTo(DESATURATING);
        }

        @Test
        void sampleForViewReadsTheNameFormatAndTheUninhabitedOutline() {
            // The two picks that are neither spotlight nor recede, both moved off their defaults so
            // a reading that failed to consult either would answer FULL and off.
            NameFormatPreference.selectNameFormat(FactionNameFormatChoice.SHORT, board);
            UninhabitedOutlinePreference.setOutlineDrawn(true, board);

            var inputs = ContentInputs.sampleForView(viewMock);

            assertThat(inputs.nameFormat())
                .isEqualTo(FactionNameFormatChoice.SHORT);
            assertThat(inputs.isUninhabitedOutlineDrawn())
                .isTrue();
        }

        @Test
        void sampleForViewReadsAnUntouchedSaveAtTheShippedDefaults() {
            // What a fresh campaign bakes under, pinned as literals: nothing spotlighted, full
            // names, no uninhabited outline, and the non-allied backdrop already recolouring so the
            // alliances view opens reading as the figure.
            var inputs = ContentInputs.sampleForView(viewMock);

            assertThat(inputs)
                .isEqualTo(new ContentInputs(
                    null,
                    ElementStyleAdjustment.NONE,
                    DESATURATING,
                    FactionNameFormatChoice.FULL,
                    false));
        }
    }

    @Nested
    class Equality {

        @Test
        void equalityHoldsForTwoReadingsOfUnmovedPicks() {
            // The frame this cache spends nearly all of its life on: nothing the player can click
            // has moved, so the map already on screen is the map these picks describe.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isEqualTo(buildReadingSpotlighting(SPOTLIT_BLOC_ID));
        }

        @Test
        void equalitySeparatesReadingsAcrossASpotlightChange() {
            // The pick decides who holds a cell rather than only how it is coloured, so a different
            // bloc is a different map and not a restyle of the same one.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isNotEqualTo(buildReadingSpotlighting("tritachyon"));
        }

        @Test
        void equalitySeparatesReadingsAcrossAFilterRecedeChange() {
            // How far the backdrop gives way is baked into every non-spotlit cell, so a Mute or
            // Desaturate flip under a spotlight has to read as a different bake.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isNotEqualTo(new ContentInputs(
                    SPOTLIT_BLOC_ID,
                    new ElementStyleAdjustment(0.4, true),
                    DESATURATING,
                    FactionNameFormatChoice.FULL,
                    false));
        }

        @Test
        void equalitySeparatesReadingsAcrossAnAllianceRecedeChange() {
            // The other backdrop, separated on its own: the alliances view bakes every non-allied
            // bloc under it, and it moves without the filter recede moving.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isNotEqualTo(new ContentInputs(
                    SPOTLIT_BLOC_ID,
                    DESATURATING,
                    ElementStyleAdjustment.NONE,
                    FactionNameFormatChoice.FULL,
                    false));
        }

        @Test
        void equalitySeparatesReadingsAcrossANameFormatChange() {
            // The format is the text every cluster label is fitted around, so it changes the search
            // as well as the words - a placement sized for a full name does not describe a short one.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isNotEqualTo(new ContentInputs(
                    SPOTLIT_BLOC_ID,
                    DESATURATING,
                    DESATURATING,
                    FactionNameFormatChoice.SHORT,
                    false));
        }

        @Test
        void equalitySeparatesReadingsAcrossAnUninhabitedOutlineFlip() {
            // Whether never-settled space is outlined at all is baked into the theme the cells are
            // styled from, so the flip cannot show without a rebuild.
            assertThat(buildReadingSpotlighting(SPOTLIT_BLOC_ID))
                .isNotEqualTo(new ContentInputs(
                    SPOTLIT_BLOC_ID,
                    DESATURATING,
                    DESATURATING,
                    FactionNameFormatChoice.FULL,
                    true));
        }
    }

    @Nested
    class ClearFilterPick {

        @Test
        void clearFilterPickDropsTheSpotlightAndTheRecedeBehindIt() {
            // The debug border-tracing view paints real dominant holders across the whole sector,
            // so it must not recede anything - and a spotlight left standing would also have it
            // naming synthetic keys no view can spell.
            var cleared = buildReadingSpotlighting(SPOTLIT_BLOC_ID).clearFilterPick();

            assertThat(cleared.selectedBlocId())
                .isNull();
            assertThat(cleared.isFiltering())
                .isFalse();
            assertThat(cleared.filterRecedeAdjustment())
                .isEqualTo(ElementStyleAdjustment.NONE);
        }

        @Test
        void clearFilterPickKeepsEveryOtherPick() {
            // Only the spotlight is dropped: such a pass still draws the names the player asked for,
            // in the format they asked for, over the categories they asked to see.
            var reading = new ContentInputs(
                SPOTLIT_BLOC_ID,
                DESATURATING,
                DESATURATING,
                FactionNameFormatChoice.SHORT,
                true);

            var cleared = reading.clearFilterPick();

            assertThat(cleared.allianceRecedeAdjustment())
                .isEqualTo(DESATURATING);
            assertThat(cleared.nameFormat())
                .isEqualTo(FactionNameFormatChoice.SHORT);
            assertThat(cleared.isUninhabitedOutlineDrawn())
                .isTrue();
        }
    }

    // The reading the equality cases vary one pick of: a bloc spotlit with both backdrops
    // recolouring, full names and no uninhabited outline.
    private static ContentInputs buildReadingSpotlighting(String selectedBlocId) {
        return new ContentInputs(
            selectedBlocId,
            DESATURATING,
            DESATURATING,
            FactionNameFormatChoice.FULL,
            false);
    }
}
