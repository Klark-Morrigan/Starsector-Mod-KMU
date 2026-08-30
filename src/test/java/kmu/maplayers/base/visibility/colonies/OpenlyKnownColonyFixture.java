package kmu.maplayers.base.visibility.colonies;

import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The landmark every case about an excused concealment is posed against, and the taking back that
 * has to follow it.
 *
 * <p>Shared because the registry is process state: a suite that seeds it and walks away answers for
 * every suite that runs after it, so the pairing of a seeding and a clearing is the thing worth
 * having in one place rather than restated wherever a landmark is posed.
 *
 * <p>The id is vanilla's own and is written here a second time on purpose. A case asserting the
 * composition root seeded the Academy has to name the Academy independently, or it would be holding
 * the root against itself.
 */
public final class OpenlyKnownColonyFixture {

    /** The entity behind the one concealed colony vanilla openly points at. */
    public static final String ACADEMY_ENTITY_ID = "station_galatia_academy";

    private OpenlyKnownColonyFixture() {
        // fixture of static builders, no instances.
    }

    /** Vouches for the Academy's entity, as a composition root does at start-up. */
    public static void registerTheAcademy() {
        OpenlyKnownColonyRegistry.registerEntityIds(List.of(ACADEMY_ENTITY_ID));
    }

    /**
     * Takes every registration back. Belongs in a suite's teardown wherever one was made: the
     * registry is seeded once per launch and read for the rest of it, so a set left behind excuses
     * concealments in suites that never posed a landmark.
     */
    public static void clearRegistrations() {
        OpenlyKnownColonyRegistry.registerEntityIds(List.of());
    }

    /**
     * The entity a concealed colony stands on, named as the game names one. The tag answers false
     * unstubbed, which is what an entity nobody marked carries.
     *
     * @param entityId the entity's id; null poses the entity the game never named
     * @return the entity mock
     */
    public static SectorEntityToken buildEntity(String entityId) {

        var entityMock = mock(SectorEntityToken.class);

        when(entityMock.getId())
            .thenReturn(entityId);

        return entityMock;
    }

    /**
     * Names the entity an already-built colony stands on, which is the only thing that parts a
     * landmark from an identical concealed market.
     *
     * @param colony   the colony's market, built by whichever fixture poses its shape
     * @param entityId the id to give the entity it already stands on
     * @return the same market, for a caller composing this into one expression
     */
    public static MarketAPI standOnEntity(MarketAPI colony, String entityId) {

        when(colony.getPrimaryEntity().getId())
            .thenReturn(entityId);

        return colony;
    }
}
