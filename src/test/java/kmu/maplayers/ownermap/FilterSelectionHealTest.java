package kmu.maplayers.ownermap;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.maplayers.ownermap.picker.BlocMetricsFake;
import kmu.maplayers.ownermap.picker.BlocPickerRead;
import kmu.maplayers.ownermap.picker.BlocPresenceIndex;
import kmu.maplayers.ownermap.picker.BlocSortModesFake;
import kmu.maplayers.ownermap.picker.RankedBloc;
import kmu.maplayers.ownermap.picker.SelectableBloc;
import kmu.settings.KmuLunaSettings;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;

import java.util.List;
import java.util.function.Predicate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the glue that heals a loaded save's spotlight selections against each screen's selected view:
 * a screen with no view selected is skipped (its persisted filter is left for a later view to judge),
 * and a screen with one is healed with a predicate that reports a bloc selectable exactly when that
 * screen's view still lists it. The views, the sector, and the filter selection are stubbed so this
 * pins the wiring alone, not how the selection actually clears.
 *
 * <p>The settings registration is pinned here too, that being the moment a live game can lose a bloc
 * from the picker without a load or a view switch to heal against.
 */
final class FilterSelectionHealTest {

    // How many screens a heal is owed, pinned as a literal: a screen added without its heal would
    // leave one panel spotlighting a bloc the player can no longer unpick from the panel they are on.
    private static final int SCREEN_COUNT = 2;

    // The layer whose spotlights are healed, stubbed per case to each screen's selected view.
    private final MapLayerViewRegistry viewRegistryMock = mock(MapLayerViewRegistry.class);

    private final OwnerPaintedView viewMock = mock(OwnerPaintedView.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    @Nested
    class HealStaleSelectionAgainstActiveView {

        @Test
        void healStaleSelectionAgainstActiveViewDoesNothingWhenNoViewIsSelected() {
            try (var selectionMock = mockStatic(FilterSelection.class)) {

                when(viewRegistryMock.getSelectedView(any()))
                    .thenReturn(null);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                // No active view means no grouping to judge selectability under, so a persisted filter
                // is left untouched rather than cleared against nothing.
                selectionMock.verify(
                    () -> FilterSelection.healStaleSelection(any(), any()),
                    never());
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewHealsWithTheActiveViewsSelectableBlocs() {
            try (var globalMock = mockStatic(Global.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                when(viewRegistryMock.getSelectedView(any()))
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                // The heal matches on the ID alone, so the stats half of the option, the vocabulary
                // bundled beside the list, and the presence beside the rows all stand in. Stubbed
                // through doReturn because the seam answers a wildcarded read, whose captured item
                // type a when() stub would have to name.
                doReturn(new BlocPickerRead<>(
                        new ListPicker<>(
                            List.of(new RankedBloc<>(
                                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                                BlocMetricsFake.EMPTY_FAKE)),
                            BlocSortModesFake.MODES),
                        BlocPresenceIndex.EMPTY))
                    .when(viewMock)
                    .resolveBlocPickerRead(sectorMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                // The predicate handed to the heal reports a bloc selectable exactly when the active
                // view still lists it, so a still-listed bloc survives and a vanished one is stale.
                var predicate = capturePredicate(selectionMock);

                assertThat(predicate.test("hegemony")).isTrue();
                assertThat(predicate.test("vanished")).isFalse();
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewHealsEveryScreensSlot() {
            // A bloc that lapsed lapsed for both panels, so both are judged in the one pass. Healing
            // only the screen being looked at would leave the other spotlighting a footprint that is
            // no longer on the map, and no way to unpick it from the panel the player is on.
            try (var selectionMock = mockStatic(FilterSelection.class)) {

                when(viewRegistryMock.getSelectedView(any()))
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                var healedSlots = captureHealedSlots(selectionMock);

                // Every screen the roster names, and no other. Compared against the roster rather than
                // against two spelled-out screens, because which screens the mod has is that class's
                // answer: a third one added there is a third panel this glue then owes a heal.
                assertThat(healedSlots)
                    .extracting(slot -> slot.screenSlot().memoryScope())
                    .containsExactlyInAnyOrderElementsOf(
                        MapLayerScreens.getAllScreenPicks().stream()
                            .map(ScreenLayerPicks::memoryScope)
                            .toList());

                // Each under the active view's ID, since a spotlight is per view as well as per screen.
                assertThat(healedSlots)
                    .extracting(SelectionSlot::scopeId)
                    .containsOnly("factions");
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewJudgesEachScreenUnderItsOwnView() {
            // The view is that panel's pick as much as the spotlight is, so each slot is judged under
            // the view its own panel is set to. Judged under the other panel's view instead, a
            // perfectly live faction spotlight would be cleared for not appearing in a group list.
            try (var selectionMock = mockStatic(FilterSelection.class)) {

                var otherViewMock = mock(OwnerPaintedView.class);

                when(viewMock.getId())
                    .thenReturn("factions");
                when(otherViewMock.getId())
                    .thenReturn("groups");

                stubOneViewPerScreen(viewRegistryMock, viewMock, otherViewMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                // Each screen's slot under its own screen's view ID, which is the pairing a shared view
                // read would collapse onto one id.
                assertThat(captureHealedSlots(selectionMock))
                    .extracting(SelectionSlot::scopeId)
                    .containsExactly("factions", "groups");
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewSkipsAScreenWithNoViewSelectedAndHealsTheRest() {
            // A panel with its map off has no grouping to judge its slot under, so its stored spotlight
            // waits for a view - while the panel beside it, which has one, is healed in the same pass.
            try (var selectionMock = mockStatic(FilterSelection.class)) {

                when(viewMock.getId())
                    .thenReturn("factions");

                stubOneViewPerScreen(viewRegistryMock, null, viewMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                selectionMock.verify(
                    () -> FilterSelection.healStaleSelection(any(), any()),
                    times(1));
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewReadsNoBlocsUntilOneIsThereToJudge() {
            // The cost contract, which matters because every settings change arrives here and
            // most saves hold no spotlight: resolving the view's blocs is a whole grouped holder
            // pass over the sector, so it must not run before the heal has found a stored ID worth
            // judging. Pinned on the seam that pass goes through, the pass itself being the view's.
            try (var globalMock = mockStatic(Global.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                when(viewRegistryMock.getSelectedView(any()))
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                doReturn(new BlocPickerRead<>(
                        new ListPicker<>(List.of(), BlocSortModesFake.MODES),
                        BlocPresenceIndex.EMPTY))
                    .when(viewMock)
                    .resolveBlocPickerRead(sectorMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView(viewRegistryMock);

                verify(viewMock, never()).resolveBlocPickerRead(sectorMock);

                // And it is that same predicate that pays the cost once there is an ID to judge, so
                // the work is deferred rather than dropped.
                capturePredicate(selectionMock).test("hegemony");

                verify(viewMock).resolveBlocPickerRead(sectorMock);
            }
        }
    }

    @Nested
    class InstallHealOnSettingsChange {

        @Test
        void installHealOnSettingsChangeRegistersTheHealAsTheSettingsChangeReaction() {
            // A settings change is what takes a bloc off the picker mid-game (a visibility override
            // switched off can leave a faction with no visible market), so what is pinned is that the
            // runnable handed to the settings seam is the heal itself and not some other reaction.
            try (var settingsMock = mockStatic(KmuLunaSettings.class)) {

                when(viewRegistryMock.getSelectedView(any()))
                    .thenReturn(null);

                FilterSelectionHeal.installHealOnSettingsChange(viewRegistryMock);

                var captor = ArgumentCaptor.forClass(Runnable.class);
                settingsMock.verify(
                    () -> KmuLunaSettings.runOnSettingsChange(captor.capture()));

                // Run what was registered: only the heal reads the selected view, so reaching that
                // read - on the registry the install was handed - is what identifies the reaction.
                captor.getValue().run();

                verify(viewRegistryMock, times(SCREEN_COUNT)).getSelectedView(any());
            }
        }
    }

    // Sets each screen's own selected view, in the order the screens are walked, so a case can pose two
    // panels on different views - or one with its map off. Which screens those are stays
    // MapLayerScreens' answer: the views are handed out against the scopes it names rather than against
    // two spelled-out screens.
    private static void stubOneViewPerScreen(
            MapLayerViewRegistry viewRegistryMock,
            OwnerPaintedView... viewPerScreen) {

        var screenPicks = MapLayerScreens.getAllScreenPicks();

        for (var screenIndex = 0; screenIndex < screenPicks.size(); screenIndex++) {

            var memoryScope = screenPicks.get(screenIndex).memoryScope();
            var view = viewPerScreen[screenIndex];

            when(viewRegistryMock.getSelectedView(memoryScope))
                .thenReturn(view);
        }
    }

    // Captures the screens the heal was run for, in order, so a test can pin that every panel's slot
    // was judged rather than only the one being looked at.
    private static List<SelectionSlot> captureHealedSlots(
            MockedStatic<FilterSelection> selectionMock) {

        ArgumentCaptor<SelectionSlot> captor = ArgumentCaptor.forClass(SelectionSlot.class);
        selectionMock.verify(
            () -> FilterSelection.healStaleSelection(captor.capture(), any()),
            times(SCREEN_COUNT));
        return captor.getAllValues();
    }

    // Captures the predicate passed to FilterSelection.healStaleSelection, so a test can exercise the
    // selectability rule the glue built from a screen's view's blocs. Used by the cases that pose one
    // view across both screens, where every rule captured is built from that one view, so the last one
    // stands for all of them.
    private static Predicate<String> capturePredicate(MockedStatic<FilterSelection> selectionMock) {
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Predicate<String>> captor = ArgumentCaptor.forClass(Predicate.class);
        selectionMock.verify(
            () -> FilterSelection.healStaleSelection(any(), captor.capture()),
            times(SCREEN_COUNT));
        return captor.getValue();
    }
}
