package kmu.maplayers.base.geometry;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real sectors every geometry integration suite runs over, each loaded once for the whole run.
 *
 * <p>At the geometry root rather than beside any one generation of the void prototype, so a suite
 * about the shipped cells reads the same sectors as a suite about a prototype without reaching into
 * it.
 */
public final class LoadedSectors {

    /**
     * Where the parameterised tests take their sectors from.
     *
     * <p>A constant rather than the string at each use, because a {@code @MethodSource} names
     * its provider by text: spelled out per suite, a provider that moves leaves every copy
     * pointing at nothing, and the failure is a test that silently stops running.
     */
    public static final String SECTORS =
        "kmu.maplayers.base.geometry.LoadedSectors#provideSectorNames";

    private static final Map<String, SectorFixture> FIXTURES = new ConcurrentHashMap<>();

    private LoadedSectors() {
    }

    /**
     * @param sector which sector
     * @return its sites and their names, loaded once
     */
    public static SectorFixture loadFixture(String sector) {
        return FIXTURES.computeIfAbsent(sector, SectorFixture::loadSector);
    }

    /**
     * The sectors every parameterised test runs over.
     *
     * <p>Asserts they exist, because a missing fixture set makes every suite here pass without
     * running anything - which reads as green.
     *
     * @return the sector names on the classpath
     */
    public static List<String> provideSectorNames() {

        var names = SectorFixture.listSectorNames();

        assertThat(names)
            .as("no sector fixtures on the classpath")
            .isNotEmpty();

        return names;
    }
}
