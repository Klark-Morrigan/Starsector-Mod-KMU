package kmu.maplayers.base.geometry;

import kmlib.math.geometry.Points;
import kmlib.math.geometry.PolygonRegions;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static kmu.maplayers.base.geometry.SectorPipeline.layEveryWall;
import static kmu.maplayers.base.geometry.SectorPipeline.loadFixture;
import static kmu.maplayers.base.geometry.SectorPipeline.traceContinentCoast;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration coverage for the naming of the void, over real sectors.
 *
 * <p>Two things can go wrong once the pieces are read off the whole laying rather than off one
 * coast. A name can stop being unique - which is the one property a key has to have, and the
 * one thing the report was written to count - and a piece the trace already decided about can
 * be read as something else: a puddle the floor refused a shore is a puddle, and a lake the
 * floor gave one is lake water, whatever spans were laid across either afterwards.
 *
 * <p>Checked against the trace's own lakes and puddles rather than against a floor of this
 * suite's, because the point is that there is one floor: a piece is what the trace said it
 * was, and a second measurement here would be a second answer that happens to agree today.
 */
class VoidSectionsIntegrationTest {

    private static final String SECTORS = SectorPipeline.SECTORS;

    // What a key of each kind opens with, spelled out rather than read off the code that
    // builds them, so a prefix that drifted would be caught here rather than followed.
    private static final Map<VoidSection.SectionKind, String> PREFIX_BY_KIND = Map.of(
        VoidSection.SectionKind.PUDDLE, "void_puddle--",
        VoidSection.SectionKind.LAKE, "void_lake--",
        VoidSection.SectionKind.LAKE_POCKET, "void_lakepocket--",
        VoidSection.SectionKind.COASTAL, "void_coast--",
        VoidSection.SectionKind.INLET, "void_inlet--",
        VoidSection.SectionKind.INTERCONTINENTAL, "void_sea--");

    private static final Map<String, List<VoidSections.NamedSection>> SECTIONS =
        new ConcurrentHashMap<>();

    @Nested
    class CollectNamedSections {

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyPieceOfVoidHasANameOfItsOwn(String sector) {
            // Names are keys into the same map as the cells, so two pieces sharing one would be
            // one region to everything downstream - and the side-of-the-closest-pair part of a
            // name exists to settle exactly the collisions the cells alone leave.
            var names = new ArrayList<String>();

            for (var named : nameSectionsOf(sector)) {
                names.add(named.region().name());
            }

            assertThat(names)
                .as("%s: two pieces of void share a name", sector)
                .doesNotHaveDuplicates();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void everyNameOpensWithItsKind(String sector) {
            // The prefix is what keeps a section's key out of a star's namespace and says what
            // water a reader should expect - so a section whose key opens with another kind's
            // prefix is misfiled twice over.
            var misfiled = new ArrayList<String>();

            for (var named : nameSectionsOf(sector)) {

                if (!named.region().name().startsWith(
                        PREFIX_BY_KIND.get(named.section().kind()))) {

                    misfiled.add(named.section().kind() + " " + named.region().name());
                }
            }

            assertThat(misfiled)
                .as("%s: a section keyed under another kind's prefix", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aPuddleTheTraceFoundIsNamedAsAPuddle(String sector) {
            // The floor decided this once, at the trace. A puddle span may have cut the puddle
            // into pieces, so what is asked is that every piece ringed by a subset of the
            // puddle's own cells came out as a puddle - not that the piece is the whole puddle.
            var puddles = traceContinentCoast(sector).puddles();

            assertThat(puddles)
                .as("%s: no puddles to check the naming against", sector)
                .isNotEmpty();

            var misread = new ArrayList<String>();
            var unnamed = 0;

            for (var puddle : puddles) {

                var within = findSectionsInside(sector, puddle.waterEdge());

                if (within.isEmpty()) {
                    unnamed++;
                }

                for (var named : within) {

                    if (named.section().kind() != VoidSection.SectionKind.PUDDLE) {
                        misread.add(named.section().kind() + " " + named.region().name());
                    }
                }
            }

            assertThat(unnamed)
                .as("%s: puddles with no section inside them", sector)
                .isZero();

            assertThat(misread)
                .as("%s: a puddle read as something else", sector)
                .isEmpty();
        }

        @ParameterizedTest(name = "{0}")
        @MethodSource(SECTORS)
        void aLakeTheTraceFoundIsNamedAsLakeWater(String sector) {
            // Whole where nothing crosses it, and a lake pocket per piece where a lake span
            // does; either way the water is the lake's, and neither the outer shore's nor a
            // bay's.
            var lakes = traceContinentCoast(sector).lakes();

            assertThat(lakes)
                .as("%s: no lakes to check the naming against", sector)
                .isNotEmpty();

            var misread = new ArrayList<String>();
            var unnamed = 0;

            for (var lake : lakes) {

                var within = findSectionsInside(sector, lake.waterEdge());

                if (within.isEmpty()) {
                    unnamed++;
                }

                for (var named : within) {

                    if (named.section().kind() != VoidSection.SectionKind.LAKE
                            && named.section().kind() != VoidSection.SectionKind.LAKE_POCKET) {

                        misread.add(named.section().kind() + " " + named.region().name());
                    }
                }
            }

            assertThat(unnamed)
                .as("%s: lakes with no section inside them", sector)
                .isZero();

            assertThat(misread)
                .as("%s: a lake read as something else", sector)
                .isEmpty();
        }
    }

    private static List<VoidSections.NamedSection> nameSectionsOf(String sector) {

        return SECTIONS.computeIfAbsent(sector, named -> VoidSections.collectNamedSections(
            layEveryWall(named), loadFixture(named).getSystemIds()));
    }

    // The sections lying inside the given water, judged by where each one's middle falls. By
    // position rather than by cells, because two pieces of void can run on the same cells from
    // opposite sides of the line between them - a sliver behind a coast reach outside a puddle
    // runs on two of the puddle's own ring cells and is not the puddle's water.
    private static List<VoidSections.NamedSection> findSectionsInside(
            String sector,
            List<double[]> water) {

        var within = new ArrayList<VoidSections.NamedSection>();

        for (var named : nameSectionsOf(sector)) {

            var middle = Points.computeMean(named.section().outline());

            if (PolygonRegions.isPointInsideRing(water, middle[0], middle[1])) {
                within.add(named);
            }
        }
        return within;
    }
}
