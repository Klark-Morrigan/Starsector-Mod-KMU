package kmu.maplayers.ownermap.owners;

import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionPalette;
import kmlib.starsector.systems.SystemKey;

import kmu.maplayers.ownermap.holding.HolderGrouping;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.awt.Color;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.entry;
import static org.mockito.Mockito.mock;

/**
 * Pins the holder the render layer consumes: a bloc is coloured through its grouping's colour faction
 * rather than looked up by its own ID, a colour faction the sector cannot resolve drops the system as
 * unowned, and the two adapters onto the geometry carry each system's bloc ID in the order handed
 * over. The palette keeps the authored slots exactly - bright into primary, dark into secondary - since
 * which element draws in which is the player's choice made downstream.
 */
final class SystemOwnerTest {

    private static final String GROUP_BLOC_ID = "group-1";

    private static final Color HEGEMONY_DARK =
        SectorOwnershipFixtures.buildDarkTheme(SectorOwnershipFixtures.HEGEMONY_BRIGHT);

    // A grouping folding the Hegemony into a group it leads, so the group's bloc ID is no faction ID
    // and its colour can only come through the leading member.
    private static final HolderGrouping HEGEMONY_LED_GROUPING = new HolderGrouping(
        Map.of("hegemony", GROUP_BLOC_ID),
        Map.of(GROUP_BLOC_ID, "hegemony"),
        Map.of(GROUP_BLOC_ID, "Hegemony Compact"));

    // Two systems and the cell a void pocket beside the first draws as, keyed as the geometry keys
    // them.
    private static final SystemKey CORVUS_KEY = new SystemKey("corvus", null, "corvus_anchor");
    private static final SystemKey ASKONIA_KEY = new SystemKey("askonia", null, "askonia_anchor");
    private static final SystemKey POCKET_CELL_KEY = new SystemKey("pocket", null, "pocket_anchor");

    @Nested
    class ResolveForBloc {

        @Test
        void resolveForBlocPairsAFactionBlocWithItsOwnAuthoredShades() {

            var sector = buildSectorResolving(
                SectorOwnershipFixtures.buildFaction("hegemony", SectorOwnershipFixtures.HEGEMONY_BRIGHT));

            assertThat(SystemOwner.resolveForBloc(sector, HolderGrouping.identity(), "hegemony"))
                .isEqualTo(new SystemOwner(
                    "hegemony",
                    SectorOwnershipFixtures.HEGEMONY_BRIGHT,
                    HEGEMONY_DARK));
        }

        @Test
        void resolveForBlocColoursAGroupByItsLeadingMemberWhileKeepingTheGroupsId() {
            // The group's ID is no faction, so the palette is looked up through the leading member;
            // the holder still carries the group's ID, which is what the cells cluster by.
            var sector = buildSectorResolving(
                SectorOwnershipFixtures.buildFaction("hegemony", SectorOwnershipFixtures.HEGEMONY_BRIGHT));

            assertThat(SystemOwner.resolveForBloc(sector, HEGEMONY_LED_GROUPING, GROUP_BLOC_ID))
                .isEqualTo(new SystemOwner(
                    GROUP_BLOC_ID,
                    SectorOwnershipFixtures.HEGEMONY_BRIGHT,
                    HEGEMONY_DARK));
        }

        @Test
        void resolveForBlocReturnsNullWhenNoColourFactionIsPresent() {
            // A faction a mod removed mid-save leaves a bloc with no palette to paint in, so the
            // system drops as unowned rather than drawing in colours nobody authored.
            var sectorMock = mock(SectorAPI.class);

            assertThat(SystemOwner.resolveForBloc(sectorMock, HolderGrouping.identity(), "vanished"))
                .isNull();
        }
    }

    @Nested
    class MapFactionIdBySystemKey {

        @Test
        void mapFactionIdBySystemKeyKeepsEachSystemsBlocIdInTheOrderHandedOver() {
            // The geometry fuses on these IDs and walks them in order, so the order is part of the
            // answer rather than an accident of the map type.
            assertThat(SystemOwner.mapFactionIdBySystemKey(buildOwnerBySystemKey()))
                .containsExactly(
                    entry(ASKONIA_KEY, "sindrian_diktat"),
                    entry(CORVUS_KEY, "hegemony"));
        }

        @Test
        void mapFactionIdBySystemKeyAnswersNothingForNoOwnedSystem() {

            assertThat(SystemOwner.mapFactionIdBySystemKey(Map.of()))
                .isEmpty();
        }
    }

    @Nested
    class MapCellGrouping {

        @Test
        void mapCellGroupingOwnsACellByTheSystemItDrawsAs() {
            // A pocket cell has no star of its own; it is owned through the system it draws as, so
            // it clusters with that system's holder.
            var cellGrouping = SystemOwner.mapCellGrouping(
                Map.of(POCKET_CELL_KEY, CORVUS_KEY, ASKONIA_KEY, ASKONIA_KEY),
                buildOwnerBySystemKey());

            assertThat(cellGrouping.resolveOwnerOf(POCKET_CELL_KEY))
                .isEqualTo("hegemony");
            assertThat(cellGrouping.resolveOwnerOf(ASKONIA_KEY))
                .isEqualTo("sindrian_diktat");
        }

        @Test
        void mapCellGroupingLeavesACellUnownedWhenItsSystemHasNoHolder() {

            var cellGrouping = SystemOwner.mapCellGrouping(
                Map.of(POCKET_CELL_KEY, CORVUS_KEY),
                Map.of());

            assertThat(cellGrouping.resolveOwnerOf(POCKET_CELL_KEY))
                .isNull();
        }
    }

    @Nested
    class ResolvePalette {

        @Test
        void resolvePaletteKeepsTheBrightShadePrimaryAndTheDarkSecondary() {

            var owner = new SystemOwner("hegemony", Color.RED, Color.BLUE);

            assertThat(owner.resolvePalette())
                .isEqualTo(new FactionPalette(Color.RED, Color.BLUE));
        }
    }

    // Two owned systems in a set order - Askonia first - so an adapter reordering them shows.
    private static Map<SystemKey, SystemOwner> buildOwnerBySystemKey() {

        var ownerBySystemKey = new LinkedHashMap<SystemKey, SystemOwner>();

        ownerBySystemKey.put(ASKONIA_KEY, new SystemOwner("sindrian_diktat", Color.GREEN, Color.BLACK));
        ownerBySystemKey.put(CORVUS_KEY, new SystemOwner("hegemony", Color.RED, Color.BLUE));

        return ownerBySystemKey;
    }

    // A sector resolving the given factions by ID and nothing else.
    private static SectorAPI buildSectorResolving(FactionAPI... factions) {
        return SectorOwnershipFixtures.buildSectorWithSystems(List.of(factions));
    }
}
