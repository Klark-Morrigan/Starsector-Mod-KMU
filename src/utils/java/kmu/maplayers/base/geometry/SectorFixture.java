package kmu.maplayers.base.geometry;

import kmlib.math.geometry.VoronoiCellBuilder;
import kmlib.starsector.systems.SystemKey;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * A real sector's sites and owners, so the map geometry can be exercised over a live layout
 * instead of hand-built squares.
 *
 * <p>Hand-built cells answer "does this rule fire". They cannot produce what a real sector
 * does: near-collinear triples, a pair of systems 608 units apart, voids fourteen thousand
 * units wide, and seventy owners whose cells meet at every angle. Those are the shapes an
 * offset, a chainer, or a corner join actually breaks on, so they are worth running
 * against.
 *
 * <p>The layout is a flat resource extracted once from a developed save. The extractor
 * reads the save's obfuscated serialisation internals and is brittle across game versions
 * and mod sets, so it is deliberately not part of the build: this fixture is the only
 * source, and no test reads a save.
 *
 * <p>Each system's score is a realistic spread of owner strengths, not the real
 * {@code DominanceRules} weight - the fixture's own header says so. It is here to shape
 * geometry, never to assert what dominance resolves to.
 */
public final class SectorFixture {

    // No leading slash: the classloader resolves against the classpath roots, which is what
    // lets every root be scanned rather than only the first.
    private static final String DIRECTORY = "kmu/maplayers/base/geometry";
    private static final String EXTENSION = ".csv";
    private static final String COMMENT_PREFIX = "#";
    private static final String SEPARATOR = ",";

    private static final int COLUMN_ID = 0;
    private static final int COLUMN_X = 1;
    private static final int COLUMN_Y = 2;
    private static final int COLUMN_GROUP_KEY = 3;
    private static final int COLUMN_SCORE = 4;
    private static final int COLUMN_COUNT = 5;

    private final List<String> systemIds = new ArrayList<>();
    private final List<double[]> sites = new ArrayList<>();

    private final Map<String, String> ownerBySystemId = new LinkedHashMap<>();
    private final Map<String, Integer> scoreBySystemId = new LinkedHashMap<>();

    private SectorFixture() {
    }

    /**
     * Every sector fixture on the classpath, so a caller runs over all of them rather than
     * over one favourite.
     *
     * <p>One sector only ever proves things about its own shape. A second with a different
     * density, empty-space ratio, or owner count produces triples, spacings, and cluster
     * shapes the first never does - and those are what the geometry breaks on. Enumerating
     * the folder rather than listing names means dropping a new extract in is all it takes
     * to widen coverage.
     *
     * @return each fixture's file name, sorted so a failure names the same one run to run
     */
    public static List<String> listSectorNames() {

        // Every classpath root is scanned, not just the first. This resource path mirrors a
        // package, so it exists twice at test time - once under the compiled-classes output,
        // which holds the sub-packages and no fixture, and once under the resources output.
        // getResource answers with whichever comes first and would report an empty folder.
        var names = new TreeSet<String>();

        try {

            var roots = SectorFixture.class.getClassLoader().getResources(DIRECTORY);

            while (roots.hasMoreElements()) {

                var root = Path.of(roots.nextElement().toURI());

                if (!Files.isDirectory(root)) {
                    continue;
                }

                try (var entries = Files.list(root)) {

                    entries.map(path -> path.getFileName().toString())
                            .filter(name -> name.endsWith(EXTENSION))
                            .forEach(names::add);
                }
            }
        } catch (IOException | URISyntaxException e) {
            throw new IllegalStateException("cannot list sector fixtures in " + DIRECTORY, e);
        }
        return List.copyOf(names);
    }

    /**
     * Reads one sector layout off the classpath.
     *
     * @param name the fixture's file name, as {@link #listSectorNames} reports it
     * @return the fixture, its systems in the resource's own order
     */
    public static SectorFixture loadSector(String name) {

        var resource = "/" + DIRECTORY + "/" + name;
        var fixture = new SectorFixture();

        try (var reader = new BufferedReader(new InputStreamReader(
                SectorFixture.class.getResourceAsStream(resource),
                StandardCharsets.US_ASCII))) {

            String line;

            while ((line = reader.readLine()) != null) {

                if (line.isBlank() || line.startsWith(COMMENT_PREFIX)) {
                    continue;
                }
                fixture.addSystem(line);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("cannot read " + resource, e);
        }
        return fixture;
    }

    /**
     * The ID of the system each site was loaded from, in the sites' own order.
     *
     * @return the IDs
     */
    public List<String> getSystemIds() {
        return systemIds;
    }

    /**
     * Where each system sits, which is what every construction here is built from.
     *
     * @return the sites
     */
    public List<double[]> getSites() {
        return sites;
    }

    /**
     * Each site's owner, index-aligned with {@link #getSites()}.
     *
     * @return the owner per site, null where the site is unowned
     */
    public List<String> getOwnerBySite() {

        var owners = new ArrayList<String>(systemIds.size());

        for (var systemId : systemIds) {
            owners.add(ownerBySystemId.get(systemId));
        }
        return owners;
    }

    /**
     * The owner per system, in the shape every geometry consumer takes: an owned
     * system maps to its owner ID, and an unowned one is absent from the map entirely -
     * which is what makes it a frontier star to {@link EdgeClassifier}.
     *
     * <p>Re-addressed by {@link SystemKey} on the way out, since that is the one address the
     * geometry holds both its cells and its owners under; the rows carry an ID and nothing else,
     * so each key states that arm alone.
     *
     * @return the key map, keyed by system key
     */
    Map<SystemKey, String> getOwnerBySystemKey() {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, String>();

        for (var index = 0; index < systemIds.size(); index++) {

            var owner = ownerBySystemId.get(systemIds.get(index));
            if (owner != null) {
                ownerBySystemKey.put(readSystemKeyAt(index), owner);
            }
        }
        return ownerBySystemKey;
    }

    Map<String, Integer> getScoreBySystemId() {
        return scoreBySystemId;
    }

    /**
     * Builds the cell-adjacency graph for the whole sector, the input every shaping and
     * tracing consumer reads.
     *
     * <p>This repeats the translation {@link CellGeometryCache} performs from a
     * labelled cell to tagged {@link CellEdge}s, because that translation is private to the
     * cache and the cache itself needs a live sector. So the graph here is equivalent to
     * the cache's, not produced by it: this exercises the geometry below the cache, and
     * pins nothing about the cache's own diffing.
     *
     * <p>Keyed by {@link SystemKey} as the live cut keys its own cells. The fixture's rows carry
     * an ID and nothing else, so each key states that arm alone - which is enough to key a cell,
     * a fixture being free of the colliding IDs a live sector holds.
     *
     * @param cellRadius    how far a cell may reach from its site
     * @param boundSegments sides of the polygon approximating each cell's radius bound
     * @return each system's cell edges, tagged with the neighbour across them
     */
    Map<SystemKey, List<CellEdge>> buildCellEdgesBySystemKey(double cellRadius, int boundSegments) {

        var edgesBySystemKey = new LinkedHashMap<SystemKey, List<CellEdge>>();

        for (var index = 0; index < systemIds.size(); index++) {

            var cell = VoronoiCellBuilder.buildLabelledCell(
                index,
                sites,
                cellRadius,
                boundSegments);

            edgesBySystemKey.put(readSystemKeyAt(index), buildCellEdges(cell));
        }
        return edgesBySystemKey;
    }

    // Walks a labelled cell into edges, resolving each edge's neighbour site index back to
    // the system across it - and BOUND_EDGE to the reach bound that means "no star across it".
    private List<CellEdge> buildCellEdges(VoronoiCellBuilder.LabelledCell cell) {

        var vertices = cell.vertices();
        var edges = new ArrayList<CellEdge>(vertices.size());

        for (var i = 0; i < vertices.size(); i++) {

            var from = vertices.get(i);
            var to = vertices.get((i + 1) % vertices.size());
            var neighbourIndex = cell.edgeNeighbourSiteIndices()[i];
            var target = neighbourIndex == VoronoiCellBuilder.BOUND_EDGE
                ? EdgeTarget.REACH_BOUND
                : new EdgeTarget.AcrossSystem(readSystemKeyAt(neighbourIndex));

            edges.add(new CellEdge(from[0], from[1], to[0], to[1], target));
        }
        return edges;
    }

    // One row's system as the cells address it: the ID the fixture states, with neither entity
    // arm, since the rows carry no entities to state.
    private SystemKey readSystemKeyAt(int index) {
        return new SystemKey(systemIds.get(index), null, null);
    }

    private void addSystem(String line) {

        var columns = line.split(SEPARATOR, -1);

        if (columns.length != COLUMN_COUNT) {
            throw new IllegalStateException("malformed fixture row: " + line);
        }

        var systemId = columns[COLUMN_ID];

        systemIds.add(systemId);
        sites.add(new double[] {
            Double.parseDouble(columns[COLUMN_X]),
            Double.parseDouble(columns[COLUMN_Y])});

        // An unowned system carries no key at all rather than an empty one, so it reads as
        // unowned to every consumer exactly as a real uninhabited system does.
        if (!columns[COLUMN_GROUP_KEY].isEmpty()) {
            ownerBySystemId.put(systemId, columns[COLUMN_GROUP_KEY]);
        }
        scoreBySystemId.put(systemId, Integer.parseInt(columns[COLUMN_SCORE]));
    }
}
