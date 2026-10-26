package kmu.maplayers.politicalmap.holders;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.colour.ColourPair;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.base.render.clusters.FillSplit.FillState;
import kmu.maplayers.base.render.clusters.SystemFillStates;
import kmu.maplayers.ownermap.owners.SystemOwner;
import kmu.maplayers.ownermap.owners.holders.base.BlocSpotlight;
import kmu.maplayers.ownermap.owners.holders.base.HolderGrouping;
import kmu.maplayers.ownermap.owners.holders.base.HolderPass;
import kmu.maplayers.ownermap.owners.holders.base.HolderResolution;
import kmu.maplayers.politicalmap.dominance.FilteredPolitics;
import kmu.maplayers.politicalmap.dominance.SectorPolitics;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.Map;
import java.util.Optional;

import static kmu.maplayers.base.geometry.CellKeyFixture.buildCellKey;
import static kmu.maplayers.ownermap.owners.holders.base.ColonyReadRulesFixtures.UNDER_THE_FOG;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

/**
 * Pins the held-dominance holding provider's one job: delegating to one of two resolvers,
 * branching on whether a spotlight opened. With none it reproduces
 * {@link SectorPolitics#resolveDominantHolderBySystemKey} with nothing contested; under one it hands
 * on {@link FilteredPolitics#resolveFilteredHolder}'s resolution whole. The resolvers and the
 * spotlight are stubbed here and covered in their own suites.
 */
final class DominanceHolderProviderTests {

    private static final Color PRIMARY = Color.RED;
    private static final Color SECONDARY = Color.BLUE;

    private static final SystemKey OWNED_SYSTEM = buildCellKey("owned-system");

    private static final SystemOwner HEGEMONY_HOLDER =
        new SystemOwner("hegemony", new ColourPair(PRIMARY, SECONDARY));
    private static final SystemOwner SPOTLIT_HOLDER =
        new SystemOwner("$spotlit", new ColourPair(PRIMARY, SECONDARY));

    private static final BlocSpotlight HEGEMONY_SPOTLIGHT = new BlocSpotlight("hegemony", SPOTLIT_HOLDER);

    private final HolderPass pass =
        HolderPass.createOver(mock(SectorAPI.class), UNDER_THE_FOG, HolderGrouping.identity());

    @Nested
    class ResolveHolder {

        // No pick opens no spotlight, so every system goes to its single dominant holder.
        @Test
        void returnsTheDominantHoldersOffFilter() {

            // ARRANGE
            var holders = Map.of(OWNED_SYSTEM, HEGEMONY_HOLDER);

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemKey(pass))
                    .thenReturn(holders);

                // ACT
                var actualResolution = DominanceHolderProvider.INSTANCE.resolveHolder(pass, null);

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsExactly(Map.entry(OWNED_SYSTEM, HEGEMONY_HOLDER));
            }
        }

        // The pick is opened into a spotlight over the pass the holding is resolved over, and the
        // presence-aware resolve is handed that spotlight.
        @Test
        void resolvesUnderTheSpotlightItOpensForThePick() {

            // ARRANGE
            var filtered = new HolderResolution(Map.of(OWNED_SYSTEM, SPOTLIT_HOLDER), SystemFillStates.ALL_SOLID);

            try (var spotlightMock = mockStatic(BlocSpotlight.class);
                    var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                spotlightMock
                    .when(() -> BlocSpotlight.openOver(pass, "hegemony"))
                    .thenReturn(Optional.of(HEGEMONY_SPOTLIGHT));

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, HEGEMONY_SPOTLIGHT))
                    .thenReturn(filtered);

                // ACT
                var actualResolution = DominanceHolderProvider.INSTANCE.resolveHolder(pass, "hegemony");

                // ASSERT
                assertThat(actualResolution)
                    .isSameAs(filtered);
            }
        }
    }

    @Nested
    class ResolveHolderUnder {

        // With no spotlight - off filter, or a pick whose colour faction has gone - each system goes
        // to its single dominant holder and nothing is contested.
        @Test
        void returnsTheDominantHoldersAndNothingContestedWithNoSpotlight() {

            // ARRANGE
            var holders = Map.of(OWNED_SYSTEM, HEGEMONY_HOLDER);

            try (var sectorPoliticsMock = mockStatic(SectorPolitics.class)) {

                sectorPoliticsMock
                    .when(() -> SectorPolitics.resolveDominantHolderBySystemKey(pass))
                    .thenReturn(holders);

                // ACT
                var actualResolution = DominanceHolderProvider.INSTANCE.resolveHolderUnder(pass, Optional.empty());

                // ASSERT
                assertThat(actualResolution.ownerBySystemKey())
                    .containsExactly(Map.entry(OWNED_SYSTEM, HEGEMONY_HOLDER));
                assertThat(actualResolution.fillStates().getNonSolidFillStateBySystemKey())
                    .isEmpty();
            }
        }

        // Handed on whole: the presence-aware resolver already answers the full resolution, so
        // nothing about its holders or its fill states is restated here.
        @Test
        void passesThroughThePresenceAwareResolverUnderASpotlight() {

            // ARRANGE
            var filtered = new HolderResolution(
                Map.of(OWNED_SYSTEM, SPOTLIT_HOLDER),
                SystemFillStates.createOf(Map.of(OWNED_SYSTEM, FillState.HATCHED)));

            try (var filteredPoliticsMock = mockStatic(FilteredPolitics.class)) {

                filteredPoliticsMock
                    .when(() -> FilteredPolitics.resolveFilteredHolder(pass, HEGEMONY_SPOTLIGHT))
                    .thenReturn(filtered);

                // ACT
                var actualResolution = DominanceHolderProvider.INSTANCE.resolveHolderUnder(
                    pass,
                    Optional.of(HEGEMONY_SPOTLIGHT));

                // ASSERT
                assertThat(actualResolution)
                    .isSameAs(filtered);
            }
        }
    }
}
