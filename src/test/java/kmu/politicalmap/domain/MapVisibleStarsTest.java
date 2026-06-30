package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.lwjgl.util.vector.Vector2f;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MapVisibleStars}: a system is map-visible when a star anchor sits
 * at its hyperspace location and is not tagged hidden; a hidden anchor, a
 * non-anchor jump point, no co-located anchor, or a missing hyperspace all read
 * as not visible. Location is matched by coordinates, not entity identity.
 */
final class MapVisibleStarsTest {

    @Nested
    class IsStarVisibleForSystem {

        @Test
        void isStarVisibleForSystemIsTrueWhenAVisibleStarAnchorSitsAtItsLocation() {
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorAt(new Vector2f(100f, 200f), false)));

            // A distinct Vector2f instance with the same coordinates, to prove the
            // match is by location and not object identity.
            assertThat(visibleStars.isStarVisibleForSystem(systemAt(new Vector2f(100f, 200f))))
                    .isTrue();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenItsStarAnchorIsHiddenOnMap() {
            var location = new Vector2f(100f, 200f);
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorAt(location, true)));

            assertThat(visibleStars.isStarVisibleForSystem(systemAt(location))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenTheCoLocatedJumpPointIsNotAStarAnchor() {
            var location = new Vector2f(100f, 200f);
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(nonAnchorAt(location)));

            assertThat(visibleStars.isStarVisibleForSystem(systemAt(location))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenNoAnchorSitsAtItsLocation() {
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorAt(new Vector2f(100f, 200f), false)));

            assertThat(visibleStars.isStarVisibleForSystem(systemAt(new Vector2f(500f, 600f))))
                    .isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenSectorHasNoHyperspace() {
            // getHyperspace() defaults to null on the mock - the empty-index path.
            var visibleStars = MapVisibleStars.scan(mock(SectorAPI.class));

            assertThat(visibleStars.isStarVisibleForSystem(systemAt(new Vector2f(0f, 0f))))
                    .isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseForNullSector() {
            var visibleStars = MapVisibleStars.scan(null);

            assertThat(visibleStars.isStarVisibleForSystem(systemAt(new Vector2f(0f, 0f))))
                    .isFalse();
        }
    }

    private static SectorAPI sectorWithHyperEntities(JumpPointAPI... entities) {
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of(entities));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    private static JumpPointAPI starAnchorAt(Vector2f location, boolean isHiddenOnMap) {
        var jumpPointMock = mock(JumpPointAPI.class);
        when(jumpPointMock.isStarAnchor()).thenReturn(true);
        when(jumpPointMock.hasTag(Tags.STAR_HIDDEN_ON_MAP)).thenReturn(isHiddenOnMap);
        when(jumpPointMock.getLocation()).thenReturn(location);
        return jumpPointMock;
    }

    private static JumpPointAPI nonAnchorAt(Vector2f location) {
        var jumpPointMock = mock(JumpPointAPI.class);
        when(jumpPointMock.isStarAnchor()).thenReturn(false);
        when(jumpPointMock.getLocation()).thenReturn(location);
        return jumpPointMock;
    }

    private static StarSystemAPI systemAt(Vector2f location) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getLocation()).thenReturn(location);
        return systemMock;
    }
}
