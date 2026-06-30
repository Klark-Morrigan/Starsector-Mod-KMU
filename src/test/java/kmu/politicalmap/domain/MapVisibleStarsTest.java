package kmu.politicalmap.domain;

import com.fs.starfarer.api.campaign.JumpPointAPI;
import com.fs.starfarer.api.campaign.LocationAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Tags;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins {@link MapVisibleStars}: a system is map-visible when an untagged star
 * anchor leads into it; a hidden anchor, a non-anchor jump point, an anchor
 * leading into a different system, an anchor leading nowhere, or a missing
 * hyperspace all read as not visible. Anchors are resolved to systems by the
 * destination they lead into, never by location.
 */
final class MapVisibleStarsTest {

    @Nested
    class IsStarVisibleForSystem {

        @Test
        void isStarVisibleForSystemIsTrueWhenAVisibleStarAnchorLeadsIntoIt() {
            var system = systemWithId("alpha");
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorLeadingTo(system, false)));

            assertThat(visibleStars.isStarVisibleForSystem(system)).isTrue();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenItsStarAnchorIsHiddenOnMap() {
            var system = systemWithId("alpha");
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorLeadingTo(system, true)));

            assertThat(visibleStars.isStarVisibleForSystem(system)).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenTheJumpPointIsNotAStarAnchor() {
            var visibleStars = MapVisibleStars.scan(sectorWithHyperEntities(nonAnchor()));

            assertThat(visibleStars.isStarVisibleForSystem(systemWithId("alpha"))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenTheOnlyAnchorLeadsIntoAnotherSystem() {
            // Resolution is by the destination system's identity, so an anchor for
            // "alpha" cannot make "beta" read as visible.
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorLeadingTo(systemWithId("alpha"), false)));

            assertThat(visibleStars.isStarVisibleForSystem(systemWithId("beta"))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIgnoresAStarAnchorThatLeadsNowhere() {
            // A malformed anchor with no destination must drop out of the scan
            // rather than crash it or admit a phantom system.
            var visibleStars = MapVisibleStars.scan(
                    sectorWithHyperEntities(starAnchorLeadingTo(null, false)));

            assertThat(visibleStars.isStarVisibleForSystem(systemWithId("alpha"))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseWhenSectorHasNoHyperspace() {
            // getHyperspace() defaults to null on the mock - the empty-index path.
            var visibleStars = MapVisibleStars.scan(mock(SectorAPI.class));

            assertThat(visibleStars.isStarVisibleForSystem(systemWithId("alpha"))).isFalse();
        }

        @Test
        void isStarVisibleForSystemIsFalseForNullSector() {
            var visibleStars = MapVisibleStars.scan(null);

            assertThat(visibleStars.isStarVisibleForSystem(systemWithId("alpha"))).isFalse();
        }
    }

    private static SectorAPI sectorWithHyperEntities(JumpPointAPI... entities) {
        var hyperspaceMock = mock(LocationAPI.class);
        when(hyperspaceMock.getEntities(JumpPointAPI.class)).thenReturn(List.of(entities));
        var sectorMock = mock(SectorAPI.class);
        when(sectorMock.getHyperspace()).thenReturn(hyperspaceMock);
        return sectorMock;
    }

    private static JumpPointAPI starAnchorLeadingTo(StarSystemAPI destination, boolean isHiddenOnMap) {
        var jumpPointMock = mock(JumpPointAPI.class);
        when(jumpPointMock.isStarAnchor()).thenReturn(true);
        when(jumpPointMock.hasTag(Tags.STAR_HIDDEN_ON_MAP)).thenReturn(isHiddenOnMap);
        when(jumpPointMock.getDestinationStarSystem()).thenReturn(destination);
        return jumpPointMock;
    }

    private static JumpPointAPI nonAnchor() {
        var jumpPointMock = mock(JumpPointAPI.class);
        when(jumpPointMock.isStarAnchor()).thenReturn(false);
        return jumpPointMock;
    }

    private static StarSystemAPI systemWithId(String id) {
        var systemMock = mock(StarSystemAPI.class);
        when(systemMock.getId()).thenReturn(id);
        return systemMock;
    }
}
