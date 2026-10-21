package kmu.maplayers.base.geometry.v3;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.Segments;

import kmu.maplayers.base.geometry.LoadedSectors;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;

import static kmu.maplayers.base.geometry.LoadedSectors.loadFixture;
import static kmu.maplayers.base.geometry.v3.SectorPipeline.PARAMETERS;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the gaps between cells read off a grid of the sites, held to what a
 * sweep over every site answers.
 *
 * <p>The grid is a filter and never the answer, so the only way it can go wrong is by leaving
 * out a site that mattered. Every question here is therefore asked twice - once of the grid, once
 * by walking every site the way the sweep it replaced did - and the two have to agree on every
 * line over both sectors.
 */
class CellGapsIntegrationTests {

    private static final String SECTORS = LoadedSectors.SECTORS;

    // How far apart two cells may stand and still be asked about, centre to centre: the span
    // search's own limit, and past it a third cell sits between almost every pair.
    private static final double PAIR_REACHES = 4;

    // The sweep's slack, as the grid applies it.
    private static final double COVER_TOLERANCE = 1e-6;

    @Nested
    class IsLineClearOfCells {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void answersEveryCorridorAsTheSweepDid(String sector) {
            // The corridors between near pairs, which are mostly clear: a site the grid missed
            // would turn a blocked corridor clear here.
            var sites = loadFixture(sector).getSites();
            var reach = PARAMETERS.cellRadius();
            var cells = CellGaps.createOverSites(sites, reach);
            var disagreements = new ArrayList<String>();
            var asked = 0;

            for (var from = 0; from < sites.size(); from++) {
                for (var to : cells.listLaterSitesWithin(from, PAIR_REACHES * reach)) {

                    var gap = cells.findGapBetween(from, to);
                    if (gap == null) {
                        continue;
                    }

                    asked++;

                    if (cells.isLineClearOfCells(gap.start(), gap.end())
                            != isClearBySweep(gap.start(), gap.end(), sites, reach)) {

                        disagreements.add(from + "-" + to);
                    }
                }
            }

            assertThat(asked)
                .as("no corridor was asked about, so nothing was compared")
                .isPositive();
            assertThat(disagreements)
                .as("corridors the grid answered differently from the sweep")
                .isEmpty();
        }

        @ParameterizedTest
        @MethodSource(SECTORS)
        void answersEveryCentreToCentreLineAsTheSweepDid(String sector) {
            // Lines from site to site, which run through cells and are mostly blocked - the
            // other answer the grid has to reach.
            var sites = loadFixture(sector).getSites();
            var reach = PARAMETERS.cellRadius();
            var cells = CellGaps.createOverSites(sites, reach);
            var disagreements = new ArrayList<String>();

            for (var from = 0; from < sites.size(); from++) {
                for (var to : cells.listLaterSitesWithin(from, PAIR_REACHES * reach)) {

                    var start = sites.get(from);
                    var end = sites.get(to);

                    if (cells.isLineClearOfCells(start, end)
                            != isClearBySweep(start, end, sites, reach)) {

                        disagreements.add(from + "-" + to);
                    }
                }
            }

            assertThat(disagreements)
                .as("lines the grid answered differently from the sweep")
                .isEmpty();
        }
    }

    @Nested
    class ListLaterSitesWithin {

        @ParameterizedTest
        @MethodSource(SECTORS)
        void listsEveryLaterSiteTheSweepFinds(String sector) {

            var sites = loadFixture(sector).getSites();
            var reach = PARAMETERS.cellRadius();
            var cells = CellGaps.createOverSites(sites, reach);
            var distance = PAIR_REACHES * reach;

            for (var site = 0; site < sites.size(); site++) {

                assertThat(cells.listLaterSitesWithin(site, distance))
                    .as("the cells near site %d", site)
                    .containsExactlyElementsOf(listLaterSitesBySweep(sites, site, distance));
            }
        }
    }

    // The sweep the grid replaced: every site, weighed against the whole line.
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

    private static List<Integer> listLaterSitesBySweep(
            List<double[]> sites,
            int site,
            double distance) {

        var later = new ArrayList<Integer>();

        for (var other = site + 1; other < sites.size(); other++) {

            if (Points.computeDistance(sites.get(site), sites.get(other)) <= distance) {
                later.add(other);
            }
        }

        return later;
    }
}
