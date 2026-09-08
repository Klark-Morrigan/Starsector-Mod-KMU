package kmu.maplayers.politicalmap.base;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.widgets.lists.ListPicker;

import kmu.maplayers.base.layer.MapLayerScreens;
import kmu.maplayers.base.layer.ScreenLayerPicks;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.sidebar.SelectionSlot;
import kmu.maplayers.politicalmap.base.politics.BlocPresenceIndex;
import kmu.maplayers.politicalmap.base.politics.DominanceStats;
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
 * Pins the glue that heals a loaded save's spotlight selections against its active view: with no view
 * selected the heal is skipped (a persisted filter is left for a later view to judge), and with a view
 * selected the heal runs on every screen with a predicate that reports a bloc selectable exactly when
 * the active view still lists it. The view, the sector, and the filter selection are stubbed so this
 * pins the wiring alone, not how the selection actually clears.
 *
 * <p>The settings registration is pinned here too, that being the moment a live game can lose a bloc
 * from the picker without a load or a view switch to heal against.
 */
final class FilterSelectionHealTest {

    // How many screens a heal is owed, pinned as a literal: a screen added without its heal would
    // leave one panel spotlighting a bloc the player can no longer unpick from the panel they are on.
    private static final int SCREEN_COUNT = 2;

    private final PoliticalMapView viewMock = mock(PoliticalMapView.class);
    private final SectorAPI sectorMock = mock(SectorAPI.class);

    @Nested
    class HealStaleSelectionAgainstActiveView {

        @Test
        void healStaleSelectionAgainstActiveViewDoesNothingWhenNoViewIsSelected() {
            try (var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(null);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

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
                    var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                // The heal matches on the id alone, so the stats half of the option, the vocabulary
                // bundled beside the list, and the presence beside the rows all stand in. Stubbed
                // through doReturn because the seam answers a wildcarded read, whose captured item
                // type a when() stub would have to name.
                doReturn(new BlocPickerRead<>(
                        new ListPicker<>(
                            List.of(new RankedBloc<>(
                                new SelectableBloc("hegemony", "Hegemony", "crest_heg"),
                                DominanceStats.EMPTY)),
                            DominanceSortModes.MODES),
                        BlocPresenceIndex.EMPTY))
                    .when(viewMock)
                    .resolveBlocPickerRead(sectorMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

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
            try (var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

                var healedSlots = captureHealedSlots(selectionMock);

                // Every screen the roster names, and no other. Compared against the roster rather than
                // against two spelled-out screens, because which screens the mod has is that class's
                // answer: a third one added there is a third panel this glue then owes a heal.
                assertThat(healedSlots)
                    .extracting(SelectionSlot::memoryScope)
                    .containsExactlyInAnyOrderElementsOf(
                        MapLayerScreens.getAllScreenPicks().stream()
                            .map(ScreenLayerPicks::memoryScope)
                            .toList());

                // Each under the active view's id, since a spotlight is per view as well as per screen.
                assertThat(healedSlots)
                    .extracting(SelectionSlot::scopeId)
                    .containsOnly("factions");
            }
        }

        @Test
        void healStaleSelectionAgainstActiveViewReadsNoBlocsUntilOneIsThereToJudge() {
            // The cost contract, which matters because every settings change now arrives here and
            // most saves hold no spotlight: resolving the view's blocs is a whole grouped dominance
            // pass over the sector, so it must not run before the heal has found a stored id worth
            // judging. Pinned on the seam that pass goes through, the pass itself being the view's.
            try (var globalMock = mockStatic(Global.class);
                    var registryMock = mockStatic(PoliticalMapViewRegistry.class);
                    var selectionMock = mockStatic(FilterSelection.class)) {

                globalMock
                    .when(Global::getSector)
                    .thenReturn(sectorMock);

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(viewMock);

                when(viewMock.getId())
                    .thenReturn("factions");

                doReturn(new BlocPickerRead<>(
                        new ListPicker<>(List.of(), DominanceSortModes.MODES),
                        BlocPresenceIndex.EMPTY))
                    .when(viewMock)
                    .resolveBlocPickerRead(sectorMock);

                FilterSelectionHeal.healStaleSelectionAgainstActiveView();

                verify(viewMock, never()).resolveBlocPickerRead(sectorMock);

                // And it is that same predicate that pays the cost once there is an id to judge, so
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
            try (var settingsMock = mockStatic(KmuLunaSettings.class);
                    var registryMock = mockStatic(PoliticalMapViewRegistry.class)) {

                registryMock
                    .when(PoliticalMapViewRegistry::getSelectedView)
                    .thenReturn(null);

                FilterSelectionHeal.installHealOnSettingsChange();

                var captor = ArgumentCaptor.forClass(Runnable.class);
                settingsMock.verify(
                    () -> KmuLunaSettings.runOnSettingsChange(captor.capture()));

                // Run what was registered: only the heal reads the selected view, so reaching that
                // read is what identifies the registered reaction.
                captor.getValue().run();

                registryMock.verify(PoliticalMapViewRegistry::getSelectedView);
            }
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
    // selectability rule the glue built from the active view's blocs. Every screen is handed the same
    // rule - which blocs a view offers is the view's answer, not a panel's - so the last one captured
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
