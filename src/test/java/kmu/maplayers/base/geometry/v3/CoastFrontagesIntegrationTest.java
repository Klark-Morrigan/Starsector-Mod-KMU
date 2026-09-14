package kmu.maplayers.base.geometry.v3;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;

import static kmu.maplayers.base.geometry.v3.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for what a coast offers a wall to anchor on, over real sectors.
 *
 * <p>Under the knobs the coast declares as its own, which are the ones every drawing of it
 * opens on; a rule record typed here by hand would describe a second map.
 */
class CoastFrontagesIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    @Nested
    class CollectPinchedCells {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aSectorHasCellsThatOfferASinglePoint(String sector) {
            // Asked first and alone, since every claim below is true of an empty set. A third of
            // a coast's stretches come out as one point, so a sector with none would be one
            // where the frontage was not read at all.
            assertThat(CoastFrontages.collectPinchedCells(traceContinentCoast(sector)))
                .as("%s: no cell offers a single point", sector)
                .isNotEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyPinchedCellOffersNothingButSinglePoints(String sector) {
            // What pinched means. A cell with any stretch to its name is not pinched, however
            // many single points it offers besides: a wall on such a cell has somewhere with
            // width to land, and keeps its channel.
            var traced = traceContinentCoast(sector);
            var frontages = CoastFrontages.collectBridgeFrontages(traced);
            var withAStretch = new ArrayList<String>();

            for (var cell : CoastFrontages.collectPinchedCells(traced)) {
                for (var run : frontages.get(cell)) {
                    if (run.size() > 1) {
                        withAStretch.add(String.format("cell %d, %d points", cell, run.size()));
                    }
                }
            }

            assertThat(withAStretch)
                .as("%s: a pinched cell with a stretch of frontage", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void noPinchedCellIsAbsentFromTheFrontage(String sector) {
            // The other direction: pinched is read OFF the frontage, so a cell reported pinched
            // has frontage to be read off. One that did not would be a cell offering a wall
            // nowhere at all, reported as offering it a point.
            var traced = traceContinentCoast(sector);
            var frontages = CoastFrontages.collectBridgeFrontages(traced);
            var unfronted = new ArrayList<Integer>();

            for (var cell : CoastFrontages.collectPinchedCells(traced)) {
                if (!frontages.containsKey(cell) || frontages.get(cell).isEmpty()) {
                    unfronted.add(cell);
                }
            }

            assertThat(unfronted)
                .as("%s: a pinched cell with no frontage to have been read off", sector)
                .isEmpty();
        }
    }
}
