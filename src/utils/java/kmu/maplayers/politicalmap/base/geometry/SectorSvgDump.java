package kmu.maplayers.politicalmap.base.geometry;

import java.nio.file.Path;

/**
 * Writes every sector fixture's geometry to SVG without opening a window.
 *
 * <p>{@link SectorGeometryViewer} already draws these shapes and can save one on request, but it
 * needs someone sitting at it. A shape often has to be looked at when nobody is: to attach to a
 * plan, to diff two runs of a geometry change against each other, or to hand to someone else.
 * Run it with {@code gradlew writeSectorSvg}.
 */
final class SectorSvgDump {
    private static final Path SVG_DIRECTORY = Path.of("build", "reports", "political-map");
    private static final String CSV_EXTENSION = ".csv";
    private static final String SVG_EXTENSION = ".svg";

    private SectorSvgDump() {
    }

    public static void main(String[] args) {
        for (var sectorName : SectorFixture.listSectorNames()) {
            var fixture = SectorFixture.loadSector(sectorName);
            var target = SVG_DIRECTORY.resolve(
                    sectorName.replace(CSV_EXTENSION, SVG_EXTENSION));
            SectorSvgWriter.writeSectorSvg(
                    target,
                    fixture,
                    SectorGeometry.buildSectorGeometry(
                            fixture, SectorGeometryParameters.createDefaults()));
            System.out.println("wrote " + target.toAbsolutePath());
        }
    }
}
