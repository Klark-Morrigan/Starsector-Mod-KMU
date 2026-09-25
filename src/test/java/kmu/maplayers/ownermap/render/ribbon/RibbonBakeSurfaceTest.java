package kmu.maplayers.ownermap.render.ribbon;

import kmu.maplayers.base.geometry.CellGeometryCache;
import kmu.maplayers.base.labels.LabelLineBoxes;
import kmu.maplayers.base.labels.anchor.ClusterAnchor;
import kmu.maplayers.base.labels.anchor.ClusterNameBoxes;
import kmu.maplayers.ownermap.preferences.FactionNameFormatChoice;
import kmu.maplayers.ownermap.render.StaticSeams;
import kmu.maplayers.ownermap.render.clusters.OwnerMapClusterFixtures;
import kmu.settings.KmuOwnerMapRibbonSettings;
import kmu.settings.RibbonNameClearanceChoice;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.base.geometry.CellKeyFixture.buildKeyedValues;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins the map one bake is laid against: the settled systems, the sites and the ring store handed
 * over as the build's own, and the room the names take resolved from the build's own name format
 * and the two band knobs - none while the names are off or the bands ignore them, and otherwise
 * whichever of the two readings the player picked.
 *
 * <p>The two readings are seams here. What a name measures to is each reading's own suite's, and
 * measuring the drawn words needs the label face, which no test JVM loads; what belongs here is
 * which of the two the choice reaches for, and that neither is asked where no room is kept.
 */
final class RibbonBakeSurfaceTest {

    // A system something stands in, the one the settled set is read back for.
    private static final String SETTLED_SYSTEM = "settled";

    // The boxes each reading hands back, distinct instances so a case reads which one arrived.
    private static final List<List<double[]>> FITTED_BOXES =
        List.of(List.of(new double[] {0.0, 0.0}, new double[] {10.0, 0.0}, new double[] {10.0, 10.0}));
    private static final List<List<double[]>> WORD_BOXES =
        List.of(List.of(new double[] {50.0, 50.0}, new double[] {60.0, 50.0}, new double[] {60.0, 60.0}));

    // The placements the bake is handed, read by identity so a reading can be seen to be asked
    // about this list and no other.
    private final List<ClusterAnchor> clusterAnchors = new ArrayList<>();

    private final CellGeometryCache geometryCacheMock = mock(CellGeometryCache.class);

    // The classes this arrangement stands in for, closed on the way out.
    private final StaticSeams seams = new StaticSeams();

    private MockedStatic<KmuOwnerMapRibbonSettings> settingsMock;
    private MockedStatic<ClusterNameBoxes> fittedBoxesMock;
    private MockedStatic<LabelLineBoxes> wordBoxesMock;

    @BeforeEach
    void openSeams() {

        // The bands on and kept clear of the names, under the fitted-box reading: what every case
        // starts from, each re-stubbing the one knob it is about on top.
        settingsMock = seams.openSeam(KmuOwnerMapRibbonSettings.class);
        RibbonSettingsFixtures.stubBandsOnAtSizesThatDraw(settingsMock);

        fittedBoxesMock = seams.openSeam(ClusterNameBoxes.class);
        fittedBoxesMock
            .when(() -> ClusterNameBoxes.listNameBoxes(same(clusterAnchors)))
            .thenReturn(FITTED_BOXES);

        wordBoxesMock = seams.openSeam(LabelLineBoxes.class);
        wordBoxesMock
            .when(() -> LabelLineBoxes.listLineBoxes(same(clusterAnchors)))
            .thenReturn(WORD_BOXES);
    }

    @AfterEach
    void closeSeams() {

        seams.closeEverySeam();
    }

    @Nested
    class CreateForPass {

        @Test
        void createForPassHandsOverTheBuildsOwnSettledSystemsSitesAndRingStore() {
            // The ring store is the cells' own rather than a copy, so a ring traced by this bake is
            // there for the next one and dropped with the shape it was traced inside.
            var clusters = OwnerMapClusterFixtures.createClustersSettledIn(Map.of(), Set.of(SETTLED_SYSTEM));
            var siteBySystemKey = buildKeyedValues(Map.of(SETTLED_SYSTEM, new double[] {2000.0, 2000.0}));

            when(geometryCacheMock.getSiteBySystemKey())
                .thenReturn(siteBySystemKey);

            var surface = RibbonBakeSurface.createForPass(clusters, geometryCacheMock, clusterAnchors);

            assertThat(surface.inhabitedSystemKeys())
                .containsExactly(buildCellKey(SETTLED_SYSTEM));
            assertThat(surface.siteBySystemKey())
                .isSameAs(siteBySystemKey);
            assertThat(surface.ringPathCache())
                .isSameAs(clusters.getPaintedCells().getRingPathCache());
        }

        @Test
        void createForPassKeepsClearOfTheFittedBoxesUnderTheFittedBoxReading() {

            var surface = createSurfaceSpelling(FactionNameFormatChoice.SHORT);

            assertThat(surface.nameBoxes())
                .isSameAs(FITTED_BOXES);

            wordBoxesMock.verifyNoInteractions();
        }

        @Test
        void createForPassKeepsClearOfTheDrawnWordsUnderTheWordsReading() {

            settingsMock
                .when(KmuOwnerMapRibbonSettings::getOwnerMapRibbonNameClearance)
                .thenReturn(RibbonNameClearanceChoice.WORDS);

            var surface = createSurfaceSpelling(FactionNameFormatChoice.SHORT);

            assertThat(surface.nameBoxes())
                .isSameAs(WORD_BOXES);

            fittedBoxesMock.verifyNoInteractions();
        }

        @Test
        void createForPassKeepsClearOfNothingWhileTheNamesAreSwitchedOff() {
            // Read off the build's name format rather than off the placements being empty: the
            // placements are built for the anchor overlay too, so they can stand while nothing is
            // drawn for a band to give way to.
            var surface = createSurfaceSpelling(FactionNameFormatChoice.NONE);

            assertThat(surface.nameBoxes())
                .isEmpty();

            fittedBoxesMock.verifyNoInteractions();
            wordBoxesMock.verifyNoInteractions();
        }

        @Test
        void createForPassKeepsClearOfNothingWhileTheBandsIgnoreTheNames() {
            // The other reason a band has nothing to keep clear of: the names are drawn, and the
            // player would rather the band ran whole beneath them.
            settingsMock
                .when(KmuOwnerMapRibbonSettings::shouldKeepOwnerMapRibbonsClearOfNames)
                .thenReturn(false);

            var surface = createSurfaceSpelling(FactionNameFormatChoice.SHORT);

            assertThat(surface.nameBoxes())
                .isEmpty();

            fittedBoxesMock.verifyNoInteractions();
            wordBoxesMock.verifyNoInteractions();
        }
    }

    // The surface over a build holding nobody, whose names are spelled the given way - the one
    // input the room kept for them is read off.
    private RibbonBakeSurface createSurfaceSpelling(FactionNameFormatChoice nameFormat) {

        var clusters = OwnerMapClusterFixtures.createClustersSpellingNames(Map.of(), nameFormat);

        return RibbonBakeSurface.createForPass(clusters, geometryCacheMock, clusterAnchors);
    }
}
