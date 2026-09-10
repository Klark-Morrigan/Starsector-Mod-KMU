package kmu.maplayers.base.geometry.output;

import kmu.maplayers.base.geometry.BridgedContinents;
import kmu.maplayers.base.geometry.DrawnSector;
import kmu.maplayers.base.geometry.SectorFixture;
import kmu.maplayers.base.geometry.SectorGeometry;
import kmu.maplayers.base.geometry.ShippedMap;
import kmu.maplayers.base.geometry.VoidBridgeCache;
import kmu.maplayers.base.geometry.VoidPockets;
import kmu.svg.SvgRasteriser;

import java.nio.file.Path;

/**
 * Writes every sector fixture's geometry to SVG without opening a window.
 *
 * <p>the geometry viewer already draws these shapes and can save one on request, but it
 * needs someone sitting at it. A shape often has to be looked at when nobody is: to attach to a
 * plan, to diff two runs of a geometry change against each other, or to hand to someone else.
 * Run it with {@code gradlew writeSectorSvg}.
 */
public final class SectorSvgDump {

    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");

    // What the true-extent map's own files are called, so the two never overwrite each
    // other and a reader can tell which map they opened from its name alone.
    private static final String TRUE_EXTENT_SUFFIX = "-true-extent";

    private static final String CSV_EXTENSION = ".csv";
    private static final String SVG_EXTENSION = ".svg";
    private static final String PNG_EXTENSION = ".png";

    private SectorSvgDump() {
    }

    /**
     * Writes every fixture's map, at both shapings of the void.
     *
     * @param args ignored; every fixture on the classpath is written
     */
    public static void main(String[] args) {

        for (var sectorName : SectorFixture.listSectorNames()) {

            var fixture = SectorFixture.loadSector(sectorName);

            // Built once for both maps: the cells, their fills, their cluster rings and the
            // laying over them are the same geometry either way, and only what is drawn of the
            // VOID answers to the shaping. Building them twice would also invite the two maps
            // to be built under knobs that had drifted apart.
            //
            // At the shipped knobs, which is what a batch dump is for: a picture of the map as
            // it comes rather than as anyone has tuned it.
            var geometry = SectorGeometry.buildSectorGeometry(fixture, ShippedMap.KNOBS);

            var laid = BridgedContinents.layContinents(
                fixture.getSites(),
                ShippedMap.KNOBS,
                ShippedMap.COAST_RULES,
                ShippedMap.SPAN_RULES,
                new VoidBridgeCache());

            writeMap(fixture, geometry, laid, sectorName, VoidPockets.PocketShaping.WITH_CHANNEL);
            writeMap(
                fixture, geometry, laid, sectorName, VoidPockets.PocketShaping.AT_TRUE_EXTENT);
        }
    }

    // One map, at one shaping of the void. Both are written every run because a change to the
    // void answers differently in the two, and the one nobody looks at is where a fault sits
    // unseen: the channel hides an outline that has strayed by cutting it back, and the true
    // extent hides nothing at all.
    private static void writeMap(
            SectorFixture fixture,
            SectorGeometry geometry,
            BridgedContinents laid,
            String sectorName,
            VoidPockets.PocketShaping shaping) {

        // Named after the shaping rather than told what to call itself, so the two maps cannot
        // land on one name through a caller passing the wrong suffix.
        var suffix = shaping.isAtTrueExtent() ? TRUE_EXTENT_SUFFIX : "";
        var target = SVG_DIRECTORY.resolve(
            sectorName.replace(CSV_EXTENSION, suffix + SVG_EXTENSION));

        SectorSvgWriter.writeSectorSvg(
            target,
            fixture,
            DrawnSector.buildDrawnSector(
                geometry, DrawnSector.DEFAULT_SMOOTHING, laid, shaping));

        System.out.println("wrote " + target.toAbsolutePath());

        // Rendered from the SVG rather than drawn a second time, so the picture cannot
        // disagree with the drawing it is a picture of. The SVG stays the artefact worth
        // keeping - diffable and deterministic - and this is only what makes it viewable by
        // anything that reads raster images.
        var raster = SVG_DIRECTORY.resolve(
            sectorName.replace(CSV_EXTENSION, suffix + PNG_EXTENSION));

        SvgRasteriser.rasteriseToPng(target, raster);
        System.out.println("wrote " + raster.toAbsolutePath());
    }
}
