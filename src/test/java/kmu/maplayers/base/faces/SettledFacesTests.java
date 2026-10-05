package kmu.maplayers.base.faces;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionNames;
import kmlib.starsector.markets.SectorMarkets;
import kmlib.starsector.strings.StarsectorStrings;
import kmlib.starsector.systems.SectorStarSystems;
import kmlib.starsector.ui.font.SettledFaceMemo;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.starsector.ui.font.installed.InstalledFaces;

import kmu.maplayers.base.machinery.SectorMapMachinery;
import kmu.maplayers.base.machinery.SectorMapMachineryIndex;
import kmu.util.KmuStringKeys;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins how KMU holds its faces: one memo per sector, released with the sector's machinery, reading each
 * kind of text off the sector or KMU's strings. Which face a text settles on is KMLib's memo's, pinned by
 * its own suite.
 */
final class SettledFacesTests {

    // "Hegemony" (U+9738 U+4E3B), a faction name as a localised install draws it.
    private static final String LOCALISED_FACTION_NAME = "霸主";

    private final SectorAPI sectorMock = mock(SectorAPI.class);

    @Nested
    final class ResolveFacesIn {

        @Test
        void holdsOneSetOfFacesPerSectorMachinery() {
            // Settling reads every name the sector holds, so a second ask on the same sector reuses the first.
            var machinery = new SectorMapMachinery(sectorMock);

            assertThat(SettledFaces.resolveFacesIn(machinery))
                .isSameAs(SettledFaces.resolveFacesIn(machinery));
        }

        @Test
        void holdsSeparateFacesForSeparateSectors() {
            // Two sectors can name different factions, so neither may read faces settled on the other.
            assertThat(SettledFaces.resolveFacesIn(new SectorMapMachinery(sectorMock)))
                .isNotSameAs(SettledFaces.resolveFacesIn(new SectorMapMachinery(mock(SectorAPI.class))));
        }

        @Test
        void readsTheTextsOffTheSectorTheMachineryIsInstalledOn() {
            // The machinery's own sector rather than the running one, which a second sector is not.
            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<ProbedText, List<String>>> readerCaptor = ArgumentCaptor.forClass(Function.class);

            try (var installedFacesMock = mockStatic(InstalledFaces.class);
                    var factionNamesMock = mockStatic(FactionNames.class)) {

                factionNamesMock.when(() -> FactionNames.listEveryName(sectorMock))
                    .thenReturn(List.of(LOCALISED_FACTION_NAME));

                SettledFaces.resolveFacesIn(new SectorMapMachinery(sectorMock));

                installedFacesMock.verify(() -> InstalledFaces.createFaceMemo(readerCaptor.capture()));

                assertThat(readerCaptor.getValue().apply(ProbedText.FACTION_NAMES))
                    .containsExactly(LOCALISED_FACTION_NAME);
            }
        }
    }

    @Nested
    final class ResolveFacesForLiveSector {

        @Test
        void answersTheFacesOfTheRunningSectorsMachinery() {
            // A surface vanilla drives names no sector, so the running one's machinery stands in for it.
            var liveMachinery = new SectorMapMachinery(sectorMock);

            try (var machineryIndexMock = mockStatic(SectorMapMachineryIndex.class)) {

                machineryIndexMock.when(SectorMapMachineryIndex::resolveMachineryForLiveSector)
                    .thenReturn(liveMachinery);

                assertThat(SettledFaces.resolveFacesForLiveSector())
                    .isSameAs(SettledFaces.resolveFacesIn(liveMachinery));
            }
        }
    }

    @Nested
    final class SettleFace {

        @Test
        void answersTheFaceTheMemoSettled() {

            @SuppressWarnings("unchecked")
            SettledFaceMemo<ProbedText> faceMemoMock = mock(SettledFaceMemo.class);

            when(faceMemoMock.settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES)))
                .thenReturn(StarsectorFont.VANILLA_INSIGNIA_25);

            assertThat(new SettledFaces(faceMemoMock)
                    .settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES)))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_25);
        }
    }

    @Nested
    final class DisposeMachinery {

        @Test
        void discardsTheSettledFaces() {
            // The machinery is released with its sector, and the faces with it.
            @SuppressWarnings("unchecked")
            SettledFaceMemo<ProbedText> faceMemoMock = mock(SettledFaceMemo.class);

            new SettledFaces(faceMemoMock).disposeMachinery();

            verify(faceMemoMock).discardFaces();
        }
    }

    @Nested
    final class CreateForSector {

        @Test
        void keepsTheRequestedFaceWhereThereIsNoSector() {
            // The detached machinery's faces: no game is loaded, so there is no name to hold a face to.
            assertThat(SettledFaces.createForSector(null)
                    .settleFace(StarsectorFont.VANILLA_INSIGNIA_42, ProbedText.EVERY_KIND))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_42);
        }

        @Test
        void readsTheTextsOffTheSectorItSettlesOn() {

            @SuppressWarnings("unchecked")
            ArgumentCaptor<Function<ProbedText, List<String>>> readerCaptor = ArgumentCaptor.forClass(Function.class);

            try (var installedFacesMock = mockStatic(InstalledFaces.class);
                    var factionNamesMock = mockStatic(FactionNames.class)) {

                factionNamesMock.when(() -> FactionNames.listEveryName(sectorMock))
                    .thenReturn(List.of(LOCALISED_FACTION_NAME));

                SettledFaces.createForSector(sectorMock);

                installedFacesMock.verify(() -> InstalledFaces.createFaceMemo(readerCaptor.capture()));

                assertThat(readerCaptor.getValue().apply(ProbedText.FACTION_NAMES))
                    .containsExactly(LOCALISED_FACTION_NAME);
            }
        }
    }

    @Nested
    final class ReadTexts {

        @Test
        void readsFactionNamesOffTheSectorsFactions() {

            try (var factionNamesMock = mockStatic(FactionNames.class)) {

                factionNamesMock.when(() -> FactionNames.listEveryName(sectorMock))
                    .thenReturn(List.of("Tri-Tachyon", LOCALISED_FACTION_NAME));

                assertThat(SettledFaces.readTexts(sectorMock, ProbedText.FACTION_NAMES))
                    .containsExactly("Tri-Tachyon", LOCALISED_FACTION_NAME);
            }
        }

        @Test
        void readsPlaceNamesOffTheSectorsSystemsAndColoniesBoth() {
            // A place is named by its system or its colony, so a face held to places holds either.
            try (var systemsMock = mockStatic(SectorStarSystems.class);
                    var marketsMock = mockStatic(SectorMarkets.class)) {

                systemsMock.when(() -> SectorStarSystems.listSystemNames(sectorMock))
                    .thenReturn(List.of("Corvus"));
                marketsMock.when(() -> SectorMarkets.listMarketNames(sectorMock))
                    .thenReturn(List.of("Jangala"));

                assertThat(SettledFaces.readTexts(sectorMock, ProbedText.PLACE_NAMES))
                    .containsExactly("Corvus", "Jangala");
            }
        }

        @Test
        void readsModStringsOffKmusOwnCategory() {

            try (var stringsMock = mockStatic(StarsectorStrings.class)) {

                stringsMock.when(() -> StarsectorStrings.listCategoryStrings(any()))
                    .thenReturn(List.of());
                stringsMock.when(() -> StarsectorStrings.listCategoryStrings(KmuStringKeys.CATEGORY))
                    .thenReturn(List.of("Political map"));

                assertThat(SettledFaces.readTexts(sectorMock, ProbedText.MOD_STRINGS))
                    .containsExactly("Political map");
            }
        }
    }
}
