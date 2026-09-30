package kmu.maplayers.base.faces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.SettingsAPI;
import com.fs.starfarer.api.campaign.FactionAPI;
import com.fs.starfarer.api.campaign.SectorAPI;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.econ.EconomyAPI;
import com.fs.starfarer.api.campaign.econ.MarketAPI;

import org.json.JSONObject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins what each kind of probed text is read from, and that a blank name or an unreadable strings file
 * costs nothing but its own text.
 */
final class ProbeTextsTests {

    // "Hegemony" (U+9738 U+4E3B), a faction's short name as a localised install carries it.
    private static final String LOCALISED_SHORT_NAME = "霸主";

    @AfterEach
    void clearSettings() {
        Global.setSettings(null);
    }

    private static FactionAPI mockFaction(String shortName, String longName) {

        var factionMock = mock(FactionAPI.class);

        when(factionMock.getDisplayName())
            .thenReturn(shortName);
        when(factionMock.getDisplayNameLong())
            .thenReturn(longName);

        return factionMock;
    }

    private static StarSystemAPI mockSystem(String name) {

        var systemMock = mock(StarSystemAPI.class);

        when(systemMock.getName())
            .thenReturn(name);

        return systemMock;
    }

    private static MarketAPI mockMarket(String name) {

        var marketMock = mock(MarketAPI.class);

        when(marketMock.getName())
            .thenReturn(name);

        return marketMock;
    }

    @Nested
    final class ReadTexts {

        @Test
        void readsEveryFactionsShortAndLongNameLeavingBlanksOut() {
            // Plenty of modded factions declare no long name; a blank has no glyph to probe.
            var sectorMock = mock(SectorAPI.class);
            var factions = List.of(
                mockFaction(LOCALISED_SHORT_NAME, "Hegemony Admiralty"),
                mockFaction("Pather", " "));

            when(sectorMock.getAllFactions())
                .thenReturn(factions);

            assertThat(ProbeTexts.readTexts(sectorMock, ProbedText.FACTION_NAMES))
                .containsExactly(LOCALISED_SHORT_NAME, "Hegemony Admiralty", "Pather");
        }

        @Test
        void readsEverySystemAndColonyName() {

            var sectorMock = mock(SectorAPI.class);
            var economyMock = mock(EconomyAPI.class);
            var systems = List.of(mockSystem("Corvus Star System"));
            var markets = List.of(mockMarket("Jangala"));

            when(sectorMock.getStarSystems())
                .thenReturn(systems);
            when(sectorMock.getEconomy())
                .thenReturn(economyMock);
            when(economyMock.getMarketsCopy())
                .thenReturn(markets);

            assertThat(ProbeTexts.readTexts(sectorMock, ProbedText.PLACE_NAMES))
                .containsExactly("Corvus Star System", "Jangala");
        }

        @Test
        void readsSystemNamesAloneWhereTheSectorHasNoEconomyYet() {

            var sectorMock = mock(SectorAPI.class);
            var systems = List.of(mockSystem("Corvus Star System"));

            when(sectorMock.getStarSystems())
                .thenReturn(systems);

            assertThat(ProbeTexts.readTexts(sectorMock, ProbedText.PLACE_NAMES))
                .containsExactly("Corvus Star System");
        }

        @Test
        void readsEveryStringInKmusOwnCategory() throws Exception {
            // Any other category is not KMU's text to probe.
            var settingsMock = mock(SettingsAPI.class);

            when(settingsMock.loadJSON("data/strings/strings.json", "kmu"))
                .thenReturn(new JSONObject(
                    "{ \"kmu\": { \"layer_name\": \"Political map\", \"blank\": \"\" },"
                        + " \"other\": { \"elsewhere\": \"Another mod\" } }"));

            Global.setSettings(settingsMock);

            assertThat(ProbeTexts.readTexts(mock(SectorAPI.class), ProbedText.MOD_STRINGS))
                .containsExactly("Political map");
        }

        @Test
        void readsNoStringsWhereTheGameCannotHandTheFileOver() throws Exception {
            // The faces then settle on the sector's names alone rather than failing the map.
            var settingsMock = mock(SettingsAPI.class);

            when(settingsMock.loadJSON("data/strings/strings.json", "kmu"))
                .thenThrow(new IOException("missing"));

            Global.setSettings(settingsMock);

            assertThat(ProbeTexts.readTexts(mock(SectorAPI.class), ProbedText.MOD_STRINGS))
                .isEmpty();
        }
    }
}
