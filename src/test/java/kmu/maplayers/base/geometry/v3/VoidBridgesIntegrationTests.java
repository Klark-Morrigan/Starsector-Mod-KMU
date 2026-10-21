package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.CellGap;
import kmu.maplayers.base.geometry.LoadedSectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import static kmu.maplayers.base.geometry.LoadedSectors.loadFixture;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.PARAMETERS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the bridges two cells hold between them, found off grids of the sites
 * and of the bridges kept, held to what a sweep over every pair finds.
 *
 * <p>The grids only narrow which pairs and which kept bridges are looked at, so the bridges have
 * to come out exactly as the sweep laid them: the same pairs, in the same order.
 */
class VoidBridgesIntegrationTests {

    private static final String SECTORS = LoadedSectors.SECTORS;

    // Four reaches centre to centre, the limit the search is documented at.
    private static final double SEPARATION_REACHES = 4;

    // The sweep's slack on the clearance test.
    private static final double COVER_TOLERANCE = 1e-6;

    @Nested
    class FindVoidBridges {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void laysTheBridgesTheSweepLaid(String sector) {

            var sites = loadFixture(sector).getSites();
            var reach = PARAMETERS.cellRadius();
            var maxSeparation = SEPARATION_REACHES * reach;

            var bridges = VoidBridges.findVoidBridges(sites, reach, maxSeparation);
            var swept = layBySweep(sites, reach, maxSeparation);

            assertThat(swept)
                .as("the sweep laid nothing, so nothing was compared")
                .isNotEmpty();

            assertThat(bridges)
                .extracting(bridge -> bridge.fromSite() + "-" + bridge.toSite())
                .containsExactlyElementsOf(
                    swept.stream().map(bridge -> bridge.fromSite() + "-" + bridge.toSite()).toList());
        }
    }

    // The search as it ran before the grids: every pair, every site, every kept bridge.
    private static List<CellGap> layBySweep(
            List<double[]> sites,
            double reach,
            double maxSeparation) {

        var cells = CellGaps.createOverSites(sites, reach);
        var widestGap = maxSeparation - 2 * reach;
        var candidates = new ArrayList<CellGap>();

        for (var first = 0; first < sites.size(); first++) {
            for (var second = first + 1; second < sites.size(); second++) {

                var gap = cells.findGapBetween(first, second);
                if (gap != null
                        && gap.width() <= widestGap
                        && isClearBySweep(gap.start(), gap.end(), sites, reach)) {

                    candidates.add(gap);
                }
            }
        }

        candidates.sort(Comparator
            .comparingDouble(CellGap::width)
            .thenComparingInt(CellGap::fromSite)
            .thenComparingInt(CellGap::toSite));

        var kept = new ArrayList<CellGap>();

        for (var candidate : candidates) {

            if (!isCrossingAnyBySweep(candidate, kept)) {
                kept.add(candidate);
            }
        }

        return kept;
    }

    private static boolean isClearBySweep(
            double[] start,
            double[] end,
            List<double[]> sites,
            double reach) {

        for (var site : sites) {

            if (Segments.computeDistanceToPoint(start, end, site) < reach - COVER_TOLERANCE) {
                return false;
            }
        }

        return true;
    }

    private static boolean isCrossingAnyBySweep(CellGap bridge, List<CellGap> kept) {

        for (var held : kept) {

            if (Segments.intersectSegments(bridge.start(), bridge.end(), held.start(), held.end())
                    != null) {

                return true;
            }
        }

        return false;
    }
}
