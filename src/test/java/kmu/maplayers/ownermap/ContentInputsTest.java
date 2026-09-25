package kmu.maplayers.ownermap;

import kmlib.testfixtures.starsector.memory.SectorMemoryFake;

import kmu.KmuMod;
import kmu.maplayers.base.layer.ScreenMemoryScope;
import kmu.maplayers.base.layer.ScreenMemoryScopes;
import kmu.maplayers.base.refresh.MapLayerRefreshBoard;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.ScreenSelectionSlot;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.maplayers.base.theme.ElementStyleAdjustment;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferences;
import kmu.maplayers.ownermap.preferences.OwnerMapBodyPreferencesFixtures;

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
 * <p>The first is what it reads. Each pick is stored per save under a key of its own, all five are
 * stored per screen, and one of them - the spotlight - is stored per view as well, so a reading that
 * crossed two of those keys would paint the map by a pick the player made somewhere else. The picks
 * are written here through the very holders the sidebar writes them through, over a memory that really
 * stores, so a value written under one key and read under another fails rather than passing on two
 * stubs that agree.
 *
 * <p>The second is its equality, and everything the rebuild decision rests on is that: a reading
 * equal to the one the map was baked under is a reading the map is entitled to skip the rebuild
 * for. Each pick is separated on its own, because each of them changes what is baked and none of
 * them moves the settings revision.
 */
public final class ContentInputsTest {

    // The screen the frame is painting for, which is the other half of what scopes the spotlight: a
    // second screen reads a slot of its own, which is FilterSelection's own suite to pin. A stand-in,
    // since a sampling is the same reading whichever panel the frame is being painted for.
    private static final ScreenMemoryScope SCREEN_SCOPE = ScreenMemoryScopes.createStandInScreen();

    // The view whose picker the spotlight is read under. Its ID is what scopes the pick, so a
    // second view would read a slot of its own - which is FilterSelection's own suite to pin.
    private static final String VIEW_ID = "factions";
    private static final String SPOTLIT_BLOC_ID = "hegemony";

    // What a recede set resolves to on an untouched save: recoloured at full opacity, since
    // Desaturate defaults on and Mute defaults off. A literal, so a flip of either default breaks
    // this suite rather than silently changing what the bake is keyed on.
    private static final ElementStyleAdjustment DESATURATING = new ElementStyleAdjustment(1.0, true);

    // What the painting view answers for its own backdrop, distinct from every recede-set default so
    // a reading that took it from anywhere but the view is seen.
    private static final ElementStyleAdjustment VIEW_RECEDE = new ElementStyleAdjustment(0.6, false);

    private final OwnerPaintedView viewMock = mock(OwnerPaintedView.class);

    // The painting layer's body preferences, real and over test keys, so a pick written through them
    // lands in the memory below and is read back out of it.
    private final OwnerMapBodyPreferences preferences = OwnerMapBodyPreferencesFixtures.createUnderTestKeys();

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
        when(viewMock.resolveViewRecedeAdjustment(SCREEN_SCOPE))
            .thenReturn(VIEW_RECEDE);
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
            FilterSelection.selectId(createSpotlightSlot(SCREEN_SCOPE), SPOTLIT_BLOC_ID, board);

            assertThat(ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE).selectedBlocId())
                .isEqualTo(SPOTLIT_BLOC_ID);
        }

        @Test
        void sampleForViewRecedesNothingWhileNothingIsSpotlighted() {
            // Off filter there is no spotlight for a backdrop to sit behind, so the recede is the
            // identity however the toggles are set - which is what keeps a Mute or Desaturate flip
            // made with nothing spotlighted from rebuilding a map it would not change. The
            // desaturate default is on, so this is the recede being gated and not merely absent.
            var inputs = ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE);

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
            FilterSelection.selectId(createSpotlightSlot(SCREEN_SCOPE), SPOTLIT_BLOC_ID, board);

            assertThat(ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE).filterRecedeAdjustment())
                .isEqualTo(DESATURATING);
        }

        @Test
        void sampleForViewAsksThePaintingViewForItsOwnRecedeApartFromTheFilterOne() {
            // The view's own backdrop is the view's to answer, and the filter's is the layer's: with a
            // bloc spotlit both are read, each from its own owner, so neither can stand in for the
            // other.
            FilterSelection.selectId(createSpotlightSlot(SCREEN_SCOPE), SPOTLIT_BLOC_ID, board);

            var inputs = ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE);

            assertThat(inputs.viewRecedeAdjustment())
                .isEqualTo(VIEW_RECEDE);
            assertThat(inputs.filterRecedeAdjustment())
                .isEqualTo(DESATURATING);
        }

        @Test
        void sampleForViewReadsTheNameFormatAndTheUninhabitedOutline() {
            // The two picks that are neither spotlight nor recede, both moved off their defaults so
            // a reading that failed to consult either would answer FULL and off.
            preferences.nameFormat().selectNameFormat(SCREEN_SCOPE, FactionNameFormatChoice.SHORT, board);
            preferences.uninhabitedOutline().setOutlineDrawn(SCREEN_SCOPE, true, board);

            var inputs = ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE);

            assertThat(inputs.nameFormat())
                .isEqualTo(FactionNameFormatChoice.SHORT);
            assertThat(inputs.isUninhabitedOutlineDrawn())
                .isTrue();
        }

        @Test
        void sampleForViewReadsEveryPickUnderTheScreenBeingPaintedFor() {
            // Every one of the five is a pick made on one panel, so a frame painting for one screen
            // must not pick up what the player set on the other. Posed with the whole set moved off
            // its default on a second screen: a reading that resolved a screen of its own, or left any
            // one pick screen-blind, would answer with one of these rather than the defaults.
            var otherScreen = ScreenMemoryScopes.createOtherStandInScreen();

            FilterSelection.selectId(createSpotlightSlot(otherScreen), SPOTLIT_BLOC_ID, board);
            preferences.filterRecede().setMuted(otherScreen, true, board);
            preferences.nameFormat().selectNameFormat(otherScreen, FactionNameFormatChoice.NONE, board);
            preferences.uninhabitedOutline().setOutlineDrawn(otherScreen, true, board);

            // The view is asked for the screen being painted too, and answers for that one alone.
            assertThat(ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE))
                .isEqualTo(new ContentInputs(
                    null,
                    ElementStyleAdjustment.NONE,
                    VIEW_RECEDE,
                    FactionNameFormatChoice.FULL,
                    false));
        }

        @Test
        void sampleForViewReadsAnUntouchedSaveAtTheShippedDefaults() {
            // What a fresh campaign bakes under, pinned as literals: nothing spotlighted, full
            // names, no uninhabited outline, and whatever the view answers for its own backdrop.
            var inputs = ContentInputs.sampleForView(viewMock, preferences, SCREEN_SCOPE);

            assertThat(inputs)
                .isEqualTo(new ContentInputs(
                    null,
                    ElementStyleAdjustment.NONE,
                    VIEW_RECEDE,
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
        void equalitySeparatesReadingsAcrossAViewRecedeChange() {
            // The view's own backdrop, separated on its own: a view bakes every bloc it recedes under
            // it, and it moves without the filter recede moving.
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
            // The debug border-tracing view paints real holders across the whole sector,
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

            assertThat(cleared.viewRecedeAdjustment())
                .isEqualTo(DESATURATING);
            assertThat(cleared.nameFormat())
                .isEqualTo(FactionNameFormatChoice.SHORT);
            assertThat(cleared.isUninhabitedOutlineDrawn())
                .isTrue();
        }
    }

    // The slot a spotlight is seeded into, addressed as the sidebar addresses it: this mod's store
    // namespace, the screen the pick was made on, and the view whose picker made it. Written through
    // the real holder rather than stubbed, so a sampling that composed any of the three differently
    // reads back nothing.
    private static SelectionSlot createSpotlightSlot(ScreenMemoryScope memoryScope) {
        return new SelectionSlot(
            new ScreenSelectionSlot(KmuMod.MAP_STORE_NAMESPACE, memoryScope),
            VIEW_ID);
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
