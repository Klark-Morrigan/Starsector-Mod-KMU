package kmu.maplayers.politicalmap.base.politics.ownership;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmu.maplayers.politicalmap.base.politics.DominantOwner;
import kmu.maplayers.politicalmap.base.politics.FilteredPolitics;
import kmu.maplayers.politicalmap.base.politics.OwnershipGrouping;
import kmu.maplayers.politicalmap.base.politics.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the default ownership provider's one job: delegating to the same two resolvers the
 * render pass used to call inline, branching on whether a bloc is spotlighted. Off filter it
 * reproduces {@link SectorPolitics#resolveDominantOwnerBySystemId} with nothing contested;
 * under a filter it reproduces {@link FilteredPolitics#resolveFilteredOwnership}, passing its
 * owners and contested set straight through. The resolvers themselves read the live economy,
 * so they are stubbed here and covered end to end in their own integration suites.
 */
final class DefaultOwnershipProviderTest {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    @Nested
    class ResolveOwnership {

        @Test
        void resolveOwnershipReturnsTheDominantOwnersAndNothingContestedOffFilter() {
            var sectorMock = mock(SectorAPI.class);
            var grouping = OwnershipGrouping.identity();
            Map<String, DominantOwner> owners =
                    Map.of("owned-system", new DominantOwner("hegemony", PRIMARY, SECONDARY));
            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {
                sectorPoliticsMock.when(() -> SectorPolitics
                        .resolveDominantOwnerBySystemId(sectorMock, grouping)).thenReturn(owners);

                var resolution = DefaultOwnershipProvider.INSTANCE
                        .resolveOwnership(sectorMock, grouping, null);

                assertThat(resolution.ownerBySystemId()).isEqualTo(owners);
                assertThat(resolution.contestedSystemIds()).isEmpty();
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }

        @Test
        void resolveOwnershipPassesThroughThePresenceAwareResolverWhenABlocIsSpotlighted() {
            var sectorMock = mock(SectorAPI.class);
            var grouping = OwnershipGrouping.identity();
            Map<String, DominantOwner> owners =
                    Map.of("owned-system", new DominantOwner("$spotlit", PRIMARY, SECONDARY));
            Set<String> contested = Set.of("owned-system");
            var filtered = new FilteredPolitics.FilteredOwnership(owners, contested);
            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {
                filteredPoliticsMock.when(() -> FilteredPolitics
                        .resolveFilteredOwnership(sectorMock, grouping, "hegemony"))
                        .thenReturn(filtered);

                var resolution = DefaultOwnershipProvider.INSTANCE
                        .resolveOwnership(sectorMock, grouping, "hegemony");

                assertThat(resolution.ownerBySystemId()).isEqualTo(owners);
                assertThat(resolution.contestedSystemIds()).isEqualTo(contested);
                // The presence-aware resolver reports no unfilled systems; the filter path leaves
                // that fill state empty.
                assertThat(resolution.unfilledSystemIds()).isEmpty();
            }
        }
    }
}
