package kmu.maplayers.politicalmap.base.render.territories;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.StarsectorFactionColours;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.sidebar.FilterSelection;
import kmu.maplayers.base.theme.CategoryStyle;
import kmu.maplayers.base.theme.ElementStyle;
import kmu.maplayers.base.visibility.MapVisibilityOverrides;
import kmu.maplayers.politicalmap.base.PoliticalMapInhabitation;
import kmu.maplayers.politicalmap.base.PoliticalMapViewFake;
import kmu.maplayers.politicalmap.base.dominance.HolderPass;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.holders.HolderResolution;
import kmu.maplayers.politicalmap.base.render.style.FactionPaletteSlot;
import kmu.maplayers.politicalmap.base.render.style.MapPalettes;
import kmu.maplayers.politicalmap.base.render.style.RenderStyleReader;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.politicalmap.base.render.territories.PoliticalMapTerritoryFixtures.createRenderStyleForEveryCategory;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins what a rebuild opens and what it hands down: one reading of the sector, shared by every
 * reader beneath it.
 *
 * <p>The arrangement the whole pass exists for, and the one thing no reader below can state for
 * itself. Each of them takes a pass and shares its walk of a system with whatever else reads that
 * system through it - but only this build decides how many passes there are, and two opened here
 * would have the sector walked twice over while every reader below went on looking correct.
 *
 * <p>Everything the build reads apart from that is stood in for: the theme, the palettes, the
 * inhabitation scan and the filter selection all read live sources no test JVM answers, and none
 * of them is what a case here is about. The geometry is empty, so the shaping and tracing stages
 * run over nothing and the build reduces to the reads this suite names.
 */
final class TerritoryBuilderTest {

    private static final Color NEUTRAL = new Color(150, 150, 150);

    private MockedStatic<MapVisibilityOverrides> visibilityOverridesMock;
    private MockedStatic<FilterSelection> filterSelectionMock;
    private MockedStatic<PoliticalMapInhabitation> inhabitationMock;
    private MockedStatic<FilteredPolitics> filteredPoliticsMock;
    private MockedStatic<RenderStyleReader> styleReaderMock;
    private MockedStatic<StarsectorFactionColours> factionColoursMock;
    private MockedStatic<MapPalettes> palettesMock;

    // The passes the presence scan was asked for, in the order the build asked.
    private final List<HolderPass> presenceScanPasses = new ArrayList<>();

    @BeforeEach
    void openTheLiveSeams() {

        visibilityOverridesMock = mockStatic(MapVisibilityOverrides.class);
        filterSelectionMock = mockStatic(FilterSelection.class);
        inhabitationMock = mockStatic(PoliticalMapInhabitation.class);
        filteredPoliticsMock = mockStatic(FilteredPolitics.class);
        styleReaderMock = mockStatic(RenderStyleReader.class);
        factionColoursMock = mockStatic(StarsectorFactionColours.class);
        palettesMock = mockStatic(MapPalettes.class);

        visibilityOverridesMock
            .when(MapVisibilityOverrides::readFromLunaSettings)
            .thenReturn(MapVisibilityOverrides.NONE);
        filterSelectionMock
            .when(() -> FilterSelection.getSelectedIdOf(any()))
            .thenReturn(null);
        inhabitationMock
            .when(() -> PoliticalMapInhabitation.readInhabitedSystemIds(any()))
            .thenReturn(Set.of("inhabited-system"));
        styleReaderMock
            .when(RenderStyleReader::readRenderStyle)
            .thenReturn(createRenderStyleForEveryCategory(buildInertCategoryStyle()));
        factionColoursMock
            .when(() -> StarsectorFactionColours.resolveNeutralColour(any()))
            .thenReturn(NEUTRAL);

        // Each pass the presence scan is handed, kept rather than answered about: the case is
        // about which pass reached it, not what it reported.
        filteredPoliticsMock
            .when(() -> FilteredPolitics.findPresentSystemIds(any(HolderPass.class), any(), any()))
            .thenAnswer(invocation -> {
                presenceScanPasses.add(invocation.getArgument(0));
                return Set.of();
            });
    }

    @AfterEach
    void closeTheLiveSeams() {
        palettesMock.close();
        factionColoursMock.close();
        styleReaderMock.close();
        filteredPoliticsMock.close();
        inhabitationMock.close();
        filterSelectionMock.close();
        visibilityOverridesMock.close();
    }

    // One style bundle for every category: nothing here turns on how a cell paints, only on what
    // the build read before it painted anything.
    private static CategoryStyle buildInertCategoryStyle() {

        var element = new ElementStyle(FactionPaletteSlot.PRIMARY, 1.0);
        return new CategoryStyle(element, element, 1.0, element, 1.0);
    }

    @Nested
    class BuildTerritories {

        @Test
        void buildTerritoriesReadsTheSectorThroughOnePassForEveryReaderBeneathIt() {
            // A rebuild resolves holding and then asks where the spotlit bloc lives outside it.
            // Both walk every system, so a pass apiece is a second traversal of the sector for
            // colonies the first one has already read - and, less visibly, a second reading of a
            // sector that is free to have moved between them.
            var holderPasses = new ArrayList<HolderPass>();
            var viewFake = new PoliticalMapViewFake(
                Map.of(),
                (pass, selectedBlocId) -> {
                    holderPasses.add(pass);
                    return new HolderResolution(Map.of(), Set.of(), Set.of());
                });

            TerritoryBuilder.buildTerritories(
                new CellGeometryCache(),
                mock(SectorAPI.class),
                viewFake);

            // One pass opened, and the same one at both readers - stated as identity rather than
            // as equality, since two passes over one sector carry two separate walks of it while
            // agreeing about everything they were built from.
            assertThat(holderPasses)
                .hasSize(1);
            assertThat(presenceScanPasses)
                .hasSize(1);
            assertThat(presenceScanPasses.get(0))
                .isSameAs(holderPasses.get(0));
        }
    }
}
