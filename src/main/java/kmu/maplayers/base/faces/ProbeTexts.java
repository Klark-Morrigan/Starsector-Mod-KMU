package kmu.maplayers.base.faces;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.factions.FactionNameForm;
import kmlib.starsector.factions.FactionNames;

import kmu.KmuMod;
import kmu.util.KmuStringKeys;
import kmu.util.KmuValues;

import org.apache.log4j.Logger;
import org.json.JSONException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Reads the text of each {@link ProbedText} kind off a sector and off the running build's strings.
 *
 * <p>Every name the sector holds, discovered or not: a face is settled once for the sector, and a name
 * the player has yet to see is one it will draw later in the same face. Blank names are left out, a
 * blank having no glyph to probe.
 */
final class ProbeTexts {

    private static final Logger LOG = Global.getLogger(ProbeTexts.class);

    // KMU's strings as the game loads them: the running build's locale, written there by the build.
    private static final String STRINGS_PATH = "data/strings/strings.json";

    // Reads only; never instantiated.
    private ProbeTexts() {
    }

    /**
     * @param sector the sector whose names are read
     * @param probe  which kind of text to read
     * @return that kind's text, blank entries left out
     */
    static List<String> readTexts(SectorAPI sector, ProbedText probe) {

        return switch (probe) {
            case FACTION_NAMES -> readFactionNames(sector);
            case PLACE_NAMES -> readPlaceNames(sector);
            case MOD_STRINGS -> readModStrings();
        };
    }

    private static List<String> readFactionNames(SectorAPI sector) {

        var names = new ArrayList<String>();

        for (var faction : sector.getAllFactions()) {
            for (var form : FactionNameForm.values()) {

                names.add(FactionNames.resolveName(faction, form));
            }
        }
        names.removeIf(name -> !KmuValues.hasText(name));

        return names;
    }

    // Star systems and colonies both: the box titles a system and lists its colonies, and the sidebar rows
    // name either.
    private static List<String> readPlaceNames(SectorAPI sector) {

        var names = new ArrayList<String>();

        for (var system : sector.getStarSystems()) {
            names.add(system.getName());
        }
        // A sector with no economy yet has no colonies to name.
        var economy = sector.getEconomy();

        if (economy != null) {

            for (var market : economy.getMarketsCopy()) {
                names.add(market.getName());
            }
        }
        names.removeIf(name -> !KmuValues.hasText(name));

        return names;
    }

    // Every value in KMU's category. A file the game cannot hand over leaves the faces settled on the
    // sector's names alone - they then keep the faces they ask for wherever those hold the names - and says
    // so once, since a face moving for no visible reason would otherwise have no trace.
    private static List<String> readModStrings() {

        try {
            var category = Global
                .getSettings()
                .loadJSON(STRINGS_PATH, KmuMod.MOD_ID)
                .getJSONObject(KmuStringKeys.CATEGORY);

            var strings = new ArrayList<String>();
            var keys = category.keys();

            while (keys.hasNext()) {
                strings.add(category.optString((String) keys.next()));
            }
            strings.removeIf(text -> !KmuValues.hasText(text));

            return strings;

        } catch (IOException | JSONException | RuntimeException exception) {

            LOG.warn(
                "KMU's strings could not be read to settle the faces its text draws in; "
                    + "the faces are settled on the sector's names alone",
                exception);

            return List.of();
        }
    }
}
