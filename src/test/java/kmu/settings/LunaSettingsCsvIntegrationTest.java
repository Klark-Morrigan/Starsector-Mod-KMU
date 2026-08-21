package kmu.settings;

import kmlib.settings.LabeledChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.ArgumentsProvider;
import org.junit.jupiter.params.provider.ArgumentsSource;
import org.junit.jupiter.params.support.ParameterDeclarations;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped LunaLib settings table against the code that reads it. A Radio field's stored value
 * is its selected option's label, so a label that drifts between the CSV and its {@link LabeledChoice}
 * enum fails silently in play: the read finds no match, falls back to the default, and the player's
 * pick simply does nothing with no error to trace. Nothing else checks the two agree - the enums say so
 * only in a doc comment - so this reads the real data file rather than a fixture.
 *
 * <p>An option label is therefore a stored key wearing the costume of a caption, and is frozen for the
 * same reason a field id is: tidying the wording of one resets that setting for every player who had
 * picked it. The label check is only as wide as the table below, so the coverage walk holds every Radio
 * row in the file against that table - a row added or reworded by a pass over the settings screen has to
 * be classified here before the suite goes green, rather than slipping past the guard unnoticed.
 *
 * <p>The field id one column left is stored the same way and drifts the same way, so the last two walks
 * hold the rows and the sources that name them against each other. They read the ids out of the source
 * text because they are private constants of whichever class reads the field, and they are not all in one
 * class: the two keybind ids sit with the tabs they bind.
 *
 * <p>Both directions are walked because they fail differently. A row no source names is a row whose value
 * nothing can reach - a renamed row or a renamed constant, since either leaves the id unread. An id no row
 * declares is the opposite: a getter reading a key the shipped file never writes, which returns its
 * fallback forever and so looks exactly like a setting the player has not touched. The prefix alone does
 * not mark a string as a setting - unrelated ids such as the terrain plugin's share it - so that second
 * walk names its exceptions rather than assuming there are none.
 *
 * <p>A retired id is the other exception, and the one that has to keep working after the row is gone.
 * LunaLib seeds defaults and prunes nothing, so a withdrawn field's value stays in the player's settings
 * file until something takes it out; the sweep that does is the only source that may still name the id.
 * The pair of walks over those ids holds both ends of that: the file declares no row for one, and some
 * source still sweeps it - so a row deleted without a sweep, or a sweep dropped while the id sits listed,
 * fails here rather than leaving a value nothing will ever reach again.
 *
 * <p>A Boolean row's default column is held the same way where the value is a decision rather than a
 * taste: the hover tiers ship on so that switching them is the player's move and not the file's, the
 * visibility overrides ship off so that a fresh player is shown what the ordinary rules admit and
 * nothing beyond it, and
 * the Java fallback beside each getter cannot stand in for that - it answers only while LunaLib has
 * no stored value, so it is this column a fresh player is actually given. Which way a switch ships is
 * that one table's business; that the two spellings of it agree is every Boolean row's, so the
 * fallback beside each is walked against its own column exactly as the numeric ones are - the two
 * checks reading the same column for different reasons, one for what it says and one for whether
 * anything else says it differently.
 *
 * <p>That same fallback is held against a choice row's default column, for the case the sentence above
 * sets aside: the two answer at different moments - the constant while LunaLib has nothing stored, the
 * column once it has - so two spellings of one default give a panel one look before the settings load
 * and another after, with nothing on screen to say why. The constants are private, so the walk reads
 * them out of the source text rather than calling the getters.
 *
 * <p>The last column fails the same way one column at a time: LunaLib creates a tab by being named, so a
 * mistyped tab name opens a new tab holding that row alone rather than raising anything. The tab walk
 * holds every row's placement against the set the screen is laid out into, which is why the layout is
 * spelled out here rather than left implicit in the file.
 *
 * <p>A section caption is the one row whose displayed text is free to change - it is drawn and forgotten
 * rather than stored - but it is carried in two columns and LunaLib draws only one of them, so the walk
 * over captions holds the pair together. Editing the column that reads like the caption while the other
 * one draws is a change that lands nowhere: the build stays green, the screen is unchanged, and nothing
 * says why.
 */
final class LunaSettingsCsvIntegrationTest {

    private static final Path SETTINGS_CSV = Path.of("data", "config", "LunaSettings.csv");
    
    // The CSV's own column order, as its header row declares it.
    private static final int FIELD_ID_COLUMN = 0;
    private static final int FIELD_NAME_COLUMN = 4;
    private static final int FIELD_TYPE_COLUMN = 6;
    private static final int DEFAULT_VALUE_COLUMN = 7;
    private static final int OPTIONS_COLUMN = 8;
    private static final int TAB_COLUMN = 16;
    private static final String RADIO_FIELD_TYPE = "Radio";
    private static final String BOOLEAN_FIELD_TYPE = "Boolean";
    private static final String BOOLEAN_ON_VALUE = "TRUE";
    private static final String BOOLEAN_OFF_VALUE = "FALSE";

    // The five rows hovering is switched at - the master, the pair that answers for every map layer,
    // and the political map's own pair - which are held to shipping on. The Java fallback beside each
    // getter only answers while LunaLib has no stored value, so it is this column a fresh player
    // actually gets; a row shipped off would read as a feature that is broken rather than one that is
    // switched off, and the two ids a player may already have turned off are meant to keep switching
    // the same feedback off after being lifted a tier.
    private static final List<String> HOVER_TIER_FIELD_IDS = List.of(
        "kmu_mapVisualsHoveringEnabled",
        "kmu_politicalMapHoverEnabled",
        "kmu_politicalMapHoverTooltipEnabled",
        "kmu_mapPoliticsVisualsHoverEffectsEnabled",
        "kmu_mapPoliticsVisualsHoverTooltipEnabled");

    // The visibility overrides, held to shipping off for the reason the hover tiers above are
    // held to shipping on, and with more riding on it. Every one is named for what switching it
    // on reveals, so off is the whole section's safe state: a row shipped on would put an
    // abandoned station, a hidden base or an undiscovered colony on the map from the first day of
    // a campaign, which is a thing a player cannot un-see once the map has drawn it.
    private static final List<String> VISIBILITY_OVERRIDE_FIELD_IDS = List.of(
        "kmu_map_visibility_overrides_showUnseenAbandonedStations",
        "kmu_map_visibility_overrides_showUnseenHiddenMarkets",
        "kmu_map_visibility_overrides_showUndiscoveredMarkets",
        "kmu_map_visibility_overrides_showHiddenSystems");

    // The tabs the settings screen is laid out into. LunaLib creates a tab by being asked for one,
    // so a mistyped tab name is not an error there - it silently opens a tab of its own holding
    // that one row, which reads to a player as a knob that has gone missing from where it lived.
    // Held here so the layout is a decision the file cannot drift away from by typo.
    //
    // The "Map - " prefix is what marks a tab as belonging to the map, so the two diagnostics
    // tabs read as what they tune: "Map - Dev" is the map's own, and the unprefixed "Dev" the
    // mod-wide one - which is why logging keeps the bare name rather than taking a prefix that
    // would claim it for a feature it is not part of.
    private static final Set<String> KNOWN_TABS = Set.of(
        "Map - Visuals",
        "Map - Politics - Visuals",
        "Map - Politics - Domination",
        "Map - Sound",
        "Map - Keybinds",
        "Map - Visibility",
        "Map - Compatibility",
        "Map - Dev",
        "Dev",
        "Market Condition Manager (MCM)");
    
    // LunaLib splits a Radio's options on commas; the authored rows space them out for readability.
    private static final String OPTION_SEPARATOR = ",";
    
    // The Radio fields whose options are not a LabeledChoice enum's labels, and so cannot be held
    // against one. The log level's options are log4j's own level names, which KmLogging hands
    // straight to the logger; naming them here is what keeps the coverage walk exhaustive without
    // pretending the row is choice-backed.
    private static final List<String> NON_CHOICE_BACKED_RADIO_FIELDS = List.of("kmu_logLevel");

    // The Radio fields whose stored label a LabeledChoice enum maps back to a choice, each with the
    // constant its getter names as a fallback. Listed here rather than read from the settings classes
    // because the field ids and the constants are private there - a typo in this table fails loudly
    // (no such row, or no such declaration) rather than quietly skipping a field.
    private static final List<ChoiceBackedRadio> CHOICE_BACKED_RADIOS = List.of(
        new ChoiceBackedRadio(
            "kmu_politicalMapSidebarColourScheme",
            "DEFAULT_SIDEBAR_COLOUR_SCHEME",
            SidebarColourSchemeChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapSidebarChevronColor",
            "DEFAULT_SIDEBAR_CHEVRON_COLOUR",
            NotchChevronColourChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapHiddenMarketScaling",
            "DEFAULT_HIDDEN_MARKET_SCALING",
            HiddenMarketScalingChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_presenceRibbons_nameClearance",
            "DEFAULT_RIBBON_NAME_CLEARANCE",
            RibbonNameClearanceChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_fills",
            "DEFAULT_NEBULA_DRAW_ORDER_FILLS",
            NebulaDrawOrderChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_borders",
            "DEFAULT_NEBULA_DRAW_ORDER_BORDERS",
            NebulaDrawOrderChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_presenceRibbons",
            "DEFAULT_NEBULA_DRAW_ORDER_RIBBONS",
            NebulaDrawOrderChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_starscape_positionRelativeToNebulae_labels",
            "DEFAULT_NEBULA_DRAW_ORDER_LABELS",
            NebulaDrawOrderChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapFactionOuterBorderColor",
            "DEFAULT_FACTION_OUTER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapFactionInnerBorderColor",
            "DEFAULT_FACTION_INNER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapFactionFillColor",
            "DEFAULT_FACTION_FILL_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapIndependentOuterBorderColor",
            "DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapIndependentInnerBorderColor",
            "DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapIndependentFillColor",
            "DEFAULT_INDEPENDENT_FILL_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_politicalMapHoverHighlightColor",
            "DEFAULT_HOVER_HIGHLIGHT_COLOUR",
            FactionPaletteChoice.values()));

    // A named Java fallback as the settings classes declare it: the constant, then the enum constant
    // it is assigned. Anchored on the constant's own name so the two rows backed by the same enum
    // with different defaults are still told apart, and the enum is left unnamed so a choice moved to
    // another type still resolves.
    private static final String CHOICE_DEFAULT_PATTERN = "\\b%s\\s*=\\s*\\w+\\.([A-Z][A-Z0-9_]*)\\s*;";

    // The numeric field types, whose default column holds a number the Java fallback beside the
    // getter has to agree with. Radio and Boolean rows are held against their own defaults above,
    // each in the terms that type is spelt in.
    private static final Set<String> NUMERIC_FIELD_TYPES = Set.of("Double", "Int");

    // The three links a row's two defaults are followed along, each anchored on the name the
    // previous one yielded: the field id to the constant declaring it, that constant to the
    // fallback passed beside it at the read, and that fallback to the value it is declared as.
    // Following the shipped text rather than tabulating the pairs is what holds the convention the
    // getters are written to, since a getter written some other way fails the walk rather than
    // dropping out of it.
    //
    // The first link is the same whatever the row holds - a field id is a field id - so it is
    // shared, and only the two that read a value are spelt per type.
    private static final String FIELD_CONSTANT_PATTERN = "(\\w+)\\s*=\\s*\"%s\"";

    // Every typed read a numeric row can be fetched through. Named one by one rather than as a
    // wildcard so that a read this walk has no answer for - a choice or a boolean read against a
    // numeric row - fails as an unfollowed link instead of being matched and held against the wrong
    // kind of default.
    private static final String NUMERIC_FALLBACK_READ_PATTERN =
        "read(?:Double|Float|Int)\\(\\s*%s\\s*,\\s*(\\w+)\\s*\\)";

    private static final String NUMERIC_DEFAULT_PATTERN = "\\b%s\\s*=\\s*(-?[\\d.]+[fFdD]?)\\s*;";

    // The same last two links for a Boolean row. Only one read can fetch one, so unlike the numeric
    // alternation this names a single method - which is what makes a switch read through anything
    // else fail the walk rather than pass it.
    private static final String BOOLEAN_FALLBACK_READ_PATTERN =
        "readBoolean\\(\\s*%s\\s*,\\s*(\\w+)\\s*\\)";

    private static final String BOOLEAN_DEFAULT_PATTERN = "\\b%s\\s*=\\s*(true|false)\\s*;";

    // Section captions carry an id so LunaLib can place them, but store nothing, so no source reads
    // one. Every other row holds a value.
    private static final String HEADER_FIELD_TYPE = "Header";
    
    // KMU's field ids all carry the mod's prefix, which is also what tells a field row from the
    // file's own column-header line.
    private static final String FIELD_ID_PREFIX = "kmu_";

    // Strings that carry the mod prefix without being settings fields, and so are held against no row.
    // Every sector-map render surface registers its terrain id with the game rather than with
    // LunaLib; they share the prefix because they are KMU's, not because they are settings. Listed one
    // by one so a genuine field id cannot join them by accident.
    private static final Set<String> NON_SETTINGS_PREFIXED_IDS = Set.of(
            "kmu_sector_map_layer_terrain",
            "kmu_sector_map_layer_starscape_terrain",
            "kmu_sector_map_layer_above_starscape_nebulae_terrain");

    // The ids of rows KMU has shipped and since withdrawn. Each is still named by a source - the
    // load-time sweep that takes its orphaned value out of the player's settings file - so the walk
    // over named ids has to know them, or a retired row would read as a getter fetching a key the
    // file never writes. LunaLib prunes nothing itself, which is why the sweep exists at all.
    //
    // Restated here rather than read off KmuRetiredSettings, whose list is private, and read out of
    // the source text by nothing: the two checks below are what stop this pair falling out of step -
    // an id the file still declares, or an id no source sweeps, fails rather than sitting here as a
    // stale exemption.
    private static final Set<String> RETIRED_FIELD_IDS = Set.of(
            "kmu_map_politics_visuals_presenceRibbons_uncontestedEnabled",
            "kmu_map_dev_visibilityOverrides_showUndiscoveredMarkets",
            "kmu_map_dev_visibilityOverrides_showHiddenSystems");

    // A field id as the sources spell it: quoted, so a mention in prose or a comment does not count
    // as reading the field.
    private static final Pattern FIELD_ID_LITERAL = Pattern.compile("\"(kmu_[A-Za-z0-9_]+)\"");
    private static final Path MAIN_SOURCE_ROOT = Path.of("src", "main", "java");
    private static final String JAVA_SOURCE_SUFFIX = ".java";

    @Nested
    class RadioOptionLabels {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ChoiceBackedRadioFieldsProvider.class)
        void radioOptionLabelsAllResolveToTheirChoiceEnum(String fieldId, LabeledChoice[] choices) {

            var expectedLabels = Arrays.stream(choices).map(LabeledChoice::getLabel).toList();

            // A subset is legitimate - a field may offer only some of its enum's options - but an
            // option the enum cannot name is dead: picking it reads back as the fallback.
            assertThat(readOptions(fieldId))
                .isSubsetOf(expectedLabels);
        }

        // Takes the id alone: the row's default is held against the row's own options, so the enum
        // the other check needs would only be an argument nothing reads.
        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ChoiceBackedRadioFieldIdsProvider.class)
        void radioOptionLabelsIncludeTheRowsOwnDefault(String fieldId) {

            assertThat(readOptions(fieldId))
                .contains(readColumn(fieldId, DEFAULT_VALUE_COLUMN, RADIO_FIELD_TYPE));
        }
    }

    @Nested
    class RadioFallbackDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ChoiceBackedRadioDefaultsProvider.class)
        void radioFallbackDefaultsNameTheirRowsOwnDefault(
                String fieldId,
                String defaultConstant,
                LabeledChoice[] choices) {

            assertThat(readFallbackLabel(defaultConstant, choices))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is what a fresh player is given and the constant is what answers while"
                        + " LunaLib has none, so two spellings give one look before the settings"
                        + " load and another after, with nothing to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(readColumn(fieldId, DEFAULT_VALUE_COLUMN, RADIO_FIELD_TYPE));
        }
    }

    @Nested
    class RadioFieldCoverage {

        @Test
        void everyRadioFieldInTheFileIsClassifiedBySuite() {

            assertThat(readRadioFieldIds())
                .as(
                    "Radio rows in %s not listed as choice-backed or as non-choice-backed,"
                        + " so nothing holds their option labels frozen",
                    SETTINGS_CSV)
                .isSubsetOf(listClassifiedRadioFieldIds());
        }
    }

    @Nested
    class HoverTierDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(HoverTierFieldIdsProvider.class)
        void hoverTierRowsAllShipSwitchedOn(String fieldId) {

            assertThat(readColumn(fieldId, DEFAULT_VALUE_COLUMN, BOOLEAN_FIELD_TYPE))
                .as(
                    "default of %s in %s: every hover tier ships on, so the tiering is invisible"
                        + " to a player who has switched none of them",
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(BOOLEAN_ON_VALUE);
        }
    }

    @Nested
    class VisibilityOverrideDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(VisibilityOverrideFieldIdsProvider.class)
        void visibilityOverrideRowsAllShipSwitchedOff(String fieldId) {

            assertThat(readColumn(fieldId, DEFAULT_VALUE_COLUMN, BOOLEAN_FIELD_TYPE))
                .as(
                    "default of %s in %s: a visibility override ships off, so what a fresh player"
                        + " is shown of the sector is what the ordinary rules admit and nothing"
                        + " beyond it",
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(BOOLEAN_OFF_VALUE);
        }
    }

    @Nested
    class NumericFallbackDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(NumericFieldDefaultsProvider.class)
        void numericFallbackDefaultsMatchTheirRowsOwnDefault(String fieldId, String fieldType) {

            var defaultConstant = findNumericFallbackConstant(fieldId);

            assertThat(readDeclaredNumber(defaultConstant))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is the number a fresh player is given and the constant is what answers"
                        + " while LunaLib has none, so two values paint one map before the settings"
                        + " load and another after, with nothing on screen to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(Double.parseDouble(readColumn(fieldId, DEFAULT_VALUE_COLUMN, fieldType)));
        }
    }

    @Nested
    class BooleanFallbackDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(BooleanFieldIdsProvider.class)
        void booleanFallbackDefaultsMatchTheirRowsOwnDefault(String fieldId) {

            var defaultConstant = findBooleanFallbackConstant(fieldId);

            assertThat(readDeclaredFlag(defaultConstant))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is the state a fresh player is given and the constant is what answers"
                        + " while LunaLib has none, so a feature is switched one way before the"
                        + " settings load and the other after, with nothing on screen to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(readColumn(fieldId, DEFAULT_VALUE_COLUMN, BOOLEAN_FIELD_TYPE)
                    .equalsIgnoreCase(BOOLEAN_ON_VALUE));
        }
    }

    @Nested
    class ValueFieldIds {

        @Test
        void everyValueFieldIdIsNamedBySomeSource() {

            var namedFieldIds = readFieldIdLiteralsInMainSources();
            
            assertThat(readValueFieldIds())
                .as(
                    "field ids declared in %s that no source under %s names, so either the row or"
                        + " the constant behind it was renamed and the player's stored value is"
                        + " now unreachable",
                    SETTINGS_CSV,
                    MAIN_SOURCE_ROOT)
                .isSubsetOf(namedFieldIds);
        }

        @Test
        void everyFieldIdNamedBySourceIsDeclaredInTheFile() {

            var declaredFieldIds = Stream
                .of(
                    readDeclaredFieldIds().stream(),
                    NON_SETTINGS_PREFIXED_IDS.stream(),
                    RETIRED_FIELD_IDS.stream())
                .flatMap(ids -> ids)
                .toList();

            assertThat(readFieldIdLiteralsInMainSources())
                .as(
                    "prefixed ids named under %s that %s declares no row for, so a getter reads a"
                        + " key the shipped file never writes and silently answers its fallback"
                        + " forever",
                    MAIN_SOURCE_ROOT,
                    SETTINGS_CSV)
                .isSubsetOf(declaredFieldIds);
        }
    }

    @Nested
    class RetiredFieldIds {

        @Test
        void noRetiredFieldIdIsStillDeclaredInTheFile() {

            assertThat(readDeclaredFieldIds())
                .as(
                    "rows %s still declares for ids listed as retired, so a field the mod treats as"
                        + " withdrawn is still on the settings screen - and the sweep at load"
                        + " deletes the value the player just set on it",
                    SETTINGS_CSV)
                .doesNotContainAnyElementsOf(RETIRED_FIELD_IDS);
        }

        @Test
        void everyRetiredFieldIdIsStillNamedBySomeSource() {
            // The sweep is the only thing that may name one, and it is what stops the orphaned
            // value travelling in the player's file forever. An id listed here and named nowhere
            // is a sweep that was dropped along with the reader it belonged to.
            assertThat(RETIRED_FIELD_IDS)
                .as(
                    "ids listed as retired that no source under %s names, so nothing takes their"
                        + " orphaned values out of the player's stored settings",
                    MAIN_SOURCE_ROOT)
                .isSubsetOf(readFieldIdLiteralsInMainSources());
        }
    }

    @Nested
    class TabPlacement {

        @Test
        void everyRowIsPlacedOnAKnownTab() {

            assertThat(readDeclaredTabs())
                .as(
                    "tab names declared in %s that the settings screen's layout does not know,"
                        + " so a mistyped one strands its field on a tab of its own",
                    SETTINGS_CSV)
                .isSubsetOf(KNOWN_TABS);
        }

        // The other direction: a tab the file no longer places anything on is a tab that does not
        // exist on the screen, so leaving it listed above would let the next typo match a dead name
        // instead of failing.
        @Test
        void everyKnownTabHoldsAtLeastOneRow() {

            assertThat(readDeclaredTabs())
                .as("tabs the layout names that %s places no row on", SETTINGS_CSV)
                .containsAll(KNOWN_TABS);
        }

        @Test
        void everyValueRowSitsOnItsSectionTab() {

            assertThat(findRowsStrandedFromTheirSection())
                .as(
                    "value rows in %s on a different tab from the section caption above them, so"
                        + " the section's heading and its knobs draw on different tabs",
                    SETTINGS_CSV)
                .isEmpty();
        }

        // The check above holds a row against its own caption, which a whole section moved
        // together satisfies. This is what notices that move: three of the tabs differ from each
        // other only by a prefix, so a section landing on the wrong one is a plausible slip that
        // every other walk here reads as legitimate.
        @Test
        void everyTabsRowsSitInOneUnbrokenRun() {

            assertThat(findTabsDeclaredInMoreThanOneRun())
                .as(
                    "tabs in %s whose rows are interrupted by another tab's, so the file no longer"
                        + " reads as one block per tab",
                    SETTINGS_CSV)
                .isEmpty();
        }
    }

    @Nested
    class HeaderCaptions {

        @Test
        void everyHeaderRowDrawsTheCaptionItNames() {
            
            assertThat(findHeaderRowsWhoseCaptionColumnsDisagree())
                .as(
                    "section captions in %s whose name and drawn columns differ: LunaLib draws a"
                        + " Header from its default-value column, so the other one is inert and an"
                        + " edit to it changes nothing on screen",
                    SETTINGS_CSV)
                .isEmpty();
        }
    }

    // The same table's field ids alone, read off the record rather than back out of the arguments
    // the cases are handed, so the id stays a String the compiler knows about.
    private static List<String> listChoiceBackedRadioFieldIds() {
        return CHOICE_BACKED_RADIOS
            .stream()
            .map(ChoiceBackedRadio::fieldId)
            .toList();
    }

    // Every Radio field the shipped file declares, in file order.
    private static List<String> readRadioFieldIds() {
        return readSettingsRows().stream()
            .filter(row -> row.size() > FIELD_TYPE_COLUMN)
            .filter(row -> RADIO_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The Radio fields this suite has an answer for: those held against a choice enum above, plus
    // those declared to have no enum behind them.
    private static List<String> listClassifiedRadioFieldIds() {
        return Stream
            .concat(
                listChoiceBackedRadioFieldIds().stream(),
                NON_CHOICE_BACKED_RADIO_FIELDS.stream())
            .toList();
    }

    // The tabs whose rows are split into more than one run by another tab's. LunaLib places a row
    // by its tab column alone, so an interleaved file still draws the same screen - what breaks is
    // reading it. The file is authored one unbroken block per tab, separated by a spacer row, and
    // that is what makes a misplaced section show up as a stray run of its own rather than as a
    // handful of cells among a hundred-odd identical-looking ones.
    private static List<String> findTabsDeclaredInMoreThanOneRun() {
        var runsPerTab = new LinkedHashMap<String, Integer>();

        // Empty rather than any tab name, so the file's first row opens a run instead of joining
        // one. No tab is named by the empty string, the spacer rows carrying no prefixed id.
        var previousTab = "";

        for (var tab : readDeclaredTabs()) {
            if (!tab.equals(previousTab)) {
                runsPerTab.merge(tab, 1, Integer::sum);
                previousTab = tab;
            }
        }
        return runsPerTab
            .entrySet()
            .stream()
            .filter(tabRuns -> tabRuns.getValue() > 1)
            .map(Map.Entry::getKey)
            .toList();
    }

    // The value rows placed on a different tab from the caption that heads their section. LunaLib
    // draws a section as the caption plus the rows following it, so the file's order is what binds
    // the two - a row moved between tabs on its own leaves its heading behind, and a row added under
    // the wrong caption inherits a tab nobody chose for it.
    private static List<String> findRowsStrandedFromTheirSection() {
        var strandedFieldIds = new ArrayList<String>();

        // Empty until the first caption, so a value row ahead of every caption reads as stranded -
        // it has no section to belong to.
        var sectionTab = "";
        
        for (var row : readFieldRows()) {
            if (HEADER_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN))) {
                sectionTab = row.get(TAB_COLUMN);
            } else if (!sectionTab.equals(row.get(TAB_COLUMN))) {
                strandedFieldIds.add(row.get(FIELD_ID_COLUMN));
            }
        }
        return strandedFieldIds;
    }

    // The section captions whose two caption cells hold different text. A Header is drawn through
    // addSectionHeading(defaultValue), so the name column beside it is inert for this row type
    // alone - every other row type shows its name column and stores its default. Both are authored
    // to the same text so that the row reads the same however it is skimmed, and this is what says
    // so: without it, an edit to the inert column is a caption change that silently does not happen.
    private static List<String> findHeaderRowsWhoseCaptionColumnsDisagree() {
        return readFieldRows()
            .stream()
            .filter(row -> HEADER_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .filter(row -> !row.get(FIELD_NAME_COLUMN).equals(row.get(DEFAULT_VALUE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // Every row the file declares for a KMU field, section captions included, in file order. The
    // spacing rows between sections and the file's own column-header line carry no prefixed id, so
    // the prefix is also what tells a row from the file's furniture.
    private static List<List<String>> readFieldRows() {
        return readSettingsRows()
            .stream()
            .filter(row -> row.size() > TAB_COLUMN)
            .filter(row -> row.get(FIELD_ID_COLUMN).startsWith(FIELD_ID_PREFIX))
            .toList();
    }

    // Every field the screen stores a value for, in file order.
    private static List<String> readValueFieldIds() {
        return readFieldRows()
            .stream()
            .filter(row -> !HEADER_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // Every prefixed id the file declares a row for, section captions included: a caption stores nothing,
    // but it is still a row the file declares, so a source naming one is not naming a key that does not
    // exist.
    private static List<String> readDeclaredFieldIds() {
        return readFieldRows()
            .stream()
            .map(row -> row.get(FIELD_ID_COLUMN))
            .toList();
    }

    // The tab every row asks to be placed on, section captions included: a caption is what carries a
    // section onto a tab, so it is placed the same way a value row is.
    private static List<String> readDeclaredTabs() {
        return readFieldRows()
            .stream()
            .map(row -> row.get(TAB_COLUMN))
            .toList();
    }

    // Every field id the shipped sources name, wherever they hold it.
    private static Set<String> readFieldIdLiteralsInMainSources() {
        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {
            return sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .flatMap(LunaSettingsCsvIntegrationTest::findFieldIdLiterals)
                .collect(Collectors.toSet());
        } catch (IOException failure) {
            // Surfaced for the reason the CSV read is: an unreadable source tree means the walk is
            // looking in the wrong place, not that every field is read.
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    private static Stream<String> findFieldIdLiterals(Path source) {
        return FIELD_ID_LITERAL
            .matcher(readSource(source))
            .results()
            .map(match -> match.group(1));
    }

    private static String readSource(Path source) {
        try {
            return Files.readString(source, StandardCharsets.UTF_8);
        } catch (IOException failure) {
            throw new UncheckedIOException("Could not read " + source.toAbsolutePath(), failure);
        }
    }

    // The option label the named Java fallback constant resolves to. Read out of the source text
    // rather than restated in the table above, so this holds the shipped constant and not a copy of
    // it - the constants are private, so there is no other way to reach one.
    private static String readFallbackLabel(String defaultConstant, LabeledChoice[] choices) {

        var declaredChoice = findDeclaredChoiceName(defaultConstant);

        return Arrays
            .stream(choices)
            .filter(choice -> ((Enum<?>) choice).name().equals(declaredChoice))
            .map(LabeledChoice::getLabel)
            .findFirst()
            .orElseThrow(() -> new AssertionError(
                defaultConstant + " is declared as " + declaredChoice
                    + ", which is no option of the enum this row's table names"));
    }

    // The constant a numeric row's getter passes as its fallback, followed through any of the typed
    // reads a number may be fetched by.
    private static String findNumericFallbackConstant(String fieldId) {
        return findFallbackConstant(fieldId, NUMERIC_FALLBACK_READ_PATTERN);
    }

    // The same two links for a Boolean row, followed through the one read a switch is fetched by.
    private static String findBooleanFallbackConstant(String fieldId) {
        return findFallbackConstant(fieldId, BOOLEAN_FALLBACK_READ_PATTERN);
    }

    // The constant a row's getter passes as its fallback, found by following the two links the
    // sources spell out: the field id to the constant holding it, then that constant to the read it
    // is passed to. Walked rather than tabulated so the pairing is the shipped one; a getter written
    // some other way is named by the failure rather than quietly skipped, which is what keeps the
    // convention itself held.
    //
    // Only the second link varies by type, so it is the parameter: which reads may fetch this kind
    // of row is the caller's statement, and a row fetched through some other kind of read then fails
    // as an unfollowed link rather than being matched and held against the wrong kind of default.
    private static String findFallbackConstant(String fieldId, String fallbackReadPattern) {

        var fieldConstant = findSoleMatch(
            FIELD_CONSTANT_PATTERN.formatted(Pattern.quote(fieldId)),
            "the constant holding field id " + fieldId);

        return findSoleMatch(
            fallbackReadPattern.formatted(Pattern.quote(fieldConstant)),
            "the fallback passed beside " + fieldConstant);
    }

    // The number a fallback constant is declared as. Read out of the source text rather than off the
    // class, since these constants are private - the same reason the choice fallbacks above are read
    // this way. A float literal's trailing suffix is not part of the number and is dropped.
    private static double readDeclaredNumber(String defaultConstant) {

        var declared = findSoleMatch(
            NUMERIC_DEFAULT_PATTERN.formatted(Pattern.quote(defaultConstant)),
            "a declaration of " + defaultConstant);

        return Double.parseDouble(declared.replaceAll("[fFdD]$", ""));
    }

    // The state a switch's fallback constant is declared as, read out of the source text for the
    // same reason its numeric neighbour is: the constants are private, so there is no other way to
    // reach one.
    private static boolean readDeclaredFlag(String defaultConstant) {

        return Boolean.parseBoolean(findSoleMatch(
            BOOLEAN_DEFAULT_PATTERN.formatted(Pattern.quote(defaultConstant)),
            "a declaration of " + defaultConstant));
    }

    // The one capture the pattern finds across every shipped source. Exactly one is expected: none
    // means the sources no longer spell the thing this walk follows, and two would leave it holding
    // whichever file happened to be read first.
    private static String findSoleMatch(String pattern, String soughtDescription) {

        var matches = Pattern
            .compile(pattern)
            .matcher(readMainSourceText())
            .results()
            .map(match -> match.group(1))
            .distinct()
            .toList();

        assertThat(matches)
            .as("%s in %s", soughtDescription, MAIN_SOURCE_ROOT)
            .hasSize(1);

        return matches.get(0);
    }

    // Every shipped source as one text, so a walk that follows a link across classes - a field id
    // declared in one and read in another - sees both ends of it.
    private static String readMainSourceText() {
        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {
            return sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .map(LunaSettingsCsvIntegrationTest::readSource)
                .collect(Collectors.joining("\n"));
        } catch (IOException failure) {
            // Surfaced for the reason the CSV read is: an unreadable source tree means the walk is
            // looking in the wrong place, not that every fallback agrees.
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    // The enum constant a fallback is declared as. Exactly one declaration is expected: none means
    // the table names a constant the sources no longer hold, and two would leave the walk holding
    // whichever the file listed first.
    private static String findDeclaredChoiceName(String defaultConstant) {

        var pattern = Pattern.compile(CHOICE_DEFAULT_PATTERN.formatted(defaultConstant));

        try (var sources = Files.walk(MAIN_SOURCE_ROOT)) {

            var declarations = sources
                .filter(source -> source.toString().endsWith(JAVA_SOURCE_SUFFIX))
                .flatMap(source -> pattern.matcher(readSource(source)).results())
                .map(match -> match.group(1))
                .toList();

            assertThat(declarations)
                .as("declarations of %s under %s", defaultConstant, MAIN_SOURCE_ROOT)
                .hasSize(1);

            return declarations.get(0);

        } catch (IOException failure) {
            throw new UncheckedIOException(
                "Could not walk " + MAIN_SOURCE_ROOT.toAbsolutePath(),
                failure);
        }
    }

    // The row's offered option labels, trimmed of the spacing the authored rows use.
    private static List<String> readOptions(String fieldId) {
        return Arrays
            .stream(readColumn(fieldId, OPTIONS_COLUMN, RADIO_FIELD_TYPE).split(OPTION_SEPARATOR))
            .map(String::trim)
            .filter(option -> !option.isEmpty())
            .toList();
    }

    // One cell of the named field's row. Fails the test outright when the row is missing or is not of
    // the type the caller reads it as, since either means the tables below no longer describe the
    // shipped file - and a cell read off a row of the wrong type would otherwise be held against a
    // column that means something else there.
    private static String readColumn(String fieldId, int column, String expectedFieldType) {
        var row = findRow(fieldId);
        assertThat(row.get(FIELD_TYPE_COLUMN))
            .as("field type of %s", fieldId)
            .isEqualTo(expectedFieldType);
        return row.get(column);
    }

    private static List<String> findRow(String fieldId) {
        
        var rows = readSettingsRows().stream()
            .filter(row -> row.size() > OPTIONS_COLUMN)
            .filter(row -> fieldId.equals(row.get(FIELD_ID_COLUMN)))
            .toList();

        assertThat(rows)
            .as("rows for field %s in %s", fieldId, SETTINGS_CSV)
            .hasSize(1);

        return rows.get(0);
    }

    private static List<List<String>> readSettingsRows() {
        try {
            return Files
                .readAllLines(SETTINGS_CSV, StandardCharsets.UTF_8)
                .stream()
                .map(LunaSettingsCsvIntegrationTest::splitCsvLine)
                .toList();
        } catch (IOException failure) {
            // Surfaced rather than swallowed: the file is shipped data, so a read failure means the
            // test is looking in the wrong place, not that the settings are fine.
            throw new UncheckedIOException(
                "Could not read " + SETTINGS_CSV.toAbsolutePath(),
                failure);
        }
    }

    // Splits one CSV line into its cells, honouring double quotes - the description and option columns
    // both contain commas, so a plain split would shift every later column.
    private static List<String> splitCsvLine(String line) {
        var cells = new ArrayList<String>();
        var cell = new StringBuilder();
        var isQuoted = false;
        for (var character : line.toCharArray()) {
            if (character == '"') {
                isQuoted = !isQuoted;
            } else if (character == ',' && !isQuoted) {
                cells.add(cell.toString().trim());
                cell.setLength(0);
            } else {
                cell.append(character);
            }
        }
        cells.add(cell.toString().trim());
        return cells;
    }

    /**
     * The choice-backed rows paired with the enum whose labels their options have to be.
     *
     * <p>The four providers below are classes rather than factory methods so the cases name them by
     * class literal: a factory method is reached by a fully-qualified string that no rename ever
     * follows, which leaves the suite compiling and failing at run time instead.
     */
    static final class ChoiceBackedRadioFieldsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return CHOICE_BACKED_RADIOS
                .stream()
                .map(radio -> Arguments.of(radio.fieldId(), radio.choices()));
        }
    }

    /** The same table's field ids alone, for the checks that hold a row against itself. */
    static final class ChoiceBackedRadioFieldIdsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return listChoiceBackedRadioFieldIds().stream().map(Arguments::of);
        }
    }

    /**
     * The same table's field ids paired with the constant naming each row's Java fallback, for the
     * walk that holds the two defaults together.
     */
    static final class ChoiceBackedRadioDefaultsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return CHOICE_BACKED_RADIOS
                .stream()
                .map(radio -> Arguments.of(radio.fieldId(), radio.defaultConstant(), radio.choices()));
        }
    }

    /**
     * Every numeric row the file declares, paired with its own field type.
     *
     * <p>Read off the file rather than listed in a table, unlike the Radio rows above. A Radio has
     * to name the enum its options belong to, which nothing in the CSV records, so those rows can
     * only be paired by hand; a numeric row needs no such pairing, and a hand table of seventy-odd
     * of them would be a second list of the file that a new row could be left out of - which is the
     * one failure this check exists to catch.
     */
    static final class NumericFieldDefaultsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return readFieldRows()
                .stream()
                .filter(row -> NUMERIC_FIELD_TYPES.contains(row.get(FIELD_TYPE_COLUMN)))
                .map(row -> Arguments.of(row.get(FIELD_ID_COLUMN), row.get(FIELD_TYPE_COLUMN)));
        }
    }

    /**
     * Every switch in the file, one case each, so the row whose two defaults disagree is named by
     * the failure rather than found by reading the file afterwards.
     */
    static final class BooleanFieldIdsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return readFieldRows()
                .stream()
                .filter(row -> BOOLEAN_FIELD_TYPE.equals(row.get(FIELD_TYPE_COLUMN)))
                .map(row -> Arguments.of(row.get(FIELD_ID_COLUMN)));
        }
    }

    /**
     * The rows hovering is switched at, one case each, so a row shipped off is named by the failure
     * rather than hidden inside one assertion over five ids.
     */
    static final class HoverTierFieldIdsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return HOVER_TIER_FIELD_IDS.stream().map(Arguments::of);
        }
    }

    /**
     * The visibility overrides, one case each, so the row shipped the wrong way round is named by
     * the failure rather than hidden inside one assertion over all four ids.
     */
    static final class VisibilityOverrideFieldIdsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return VISIBILITY_OVERRIDE_FIELD_IDS.stream().map(Arguments::of);
        }
    }

    /**
     * One choice-backed Radio row, as the two sides of it are spelt: the field the CSV declares, the
     * constant the reading class declares its fallback as, and the enum whose labels the row's options
     * have to be. Held together because every walk over these rows needs some pair of the three, and a
     * row described in two tables is a row that can be listed in one and forgotten in the other.
     *
     * @param fieldId         the LunaLib field id the row is stored under
     * @param defaultConstant the name of the private constant the getter passes as its fallback
     * @param choices         the enum constants the row's options are the labels of
     */
    private record ChoiceBackedRadio(
        String fieldId,
        String defaultConstant,
        LabeledChoice[] choices) {
    }
}
