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

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static kmu.settings.LunaSettingsTable.BOOLEAN_FIELD_TYPE;
import static kmu.settings.LunaSettingsTable.DOUBLE_FIELD_TYPE;
import static kmu.settings.LunaSettingsTable.INT_FIELD_TYPE;
import static kmu.settings.LunaSettingsTable.KEYCODE_FIELD_TYPE;
import static kmu.settings.LunaSettingsTable.RADIO_FIELD_TYPE;
import static kmu.settings.LunaSettingsTable.SETTINGS_CSV;
import static kmu.settings.SettingsSourceText.MAIN_SOURCE_ROOT;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the shipped LunaLib settings table against the code that reads it. A Radio field's stored value
 * is its selected option's label, so a label that drifts between the CSV and its {@link LabeledChoice}
 * enum fails silently in play: the read finds no match, falls back to the default, and the player's
 * pick simply does nothing with no error to trace. Nothing else checks the two agree - the enums say so
 * only in a doc comment - so this reads the real data file rather than a fixture.
 *
 * <p>Every check here is one of the two readings held against the other, or against a table stated
 * below: {@link LunaSettingsTable} is the shipped file as rows and cells, {@link SettingsSourceText}
 * the shipped Java as the constants a row is reached through. Neither judges anything, which is what
 * this file is for - what each reading ought to say is the whole of what is written here, and the
 * tables that say it are the only thing a pass over the settings screen has to keep current.
 *
 * <p>An option label is therefore a stored key wearing the costume of a caption, and is frozen for the
 * same reason a field ID is: tidying the wording of one resets that setting for every player who had
 * picked it. The label check is only as wide as the table below, so the coverage walk holds every Radio
 * row in the file against that table - a row added or reworded by a pass over the settings screen has to
 * be classified here before the suite goes green, rather than slipping past the guard unnoticed.
 *
 * <p>The field ID one column left is stored the same way and drifts the same way, so the last two walks
 * hold the rows and the sources that name them against each other. They read the IDs out of the source
 * text because they are private constants of whichever class reads the field, and they are not all in one
 * class: the two keybind IDs sit with the tabs they bind.
 *
 * <p>Both directions are walked because they fail differently. A row no source names is a row whose value
 * nothing can reach - a renamed row or a renamed constant, since either leaves the ID unread. An ID no row
 * declares is the opposite: a getter reading a key the shipped file never writes, which returns its
 * fallback forever and so looks exactly like a setting the player has not touched. The prefix alone does
 * not mark a string as a setting - unrelated IDs such as the terrain plugin's share it - so that second
 * walk names its exceptions rather than assuming there are none.
 *
 * <p>A Boolean row's default column is held the same way where the value is a decision rather than a
 * taste: the hover tiers ship on so that switching them is the player's move and not the file's, the
 * visibility overrides ship off so that a fresh player is shown what the ordinary rules admit and
 * nothing beyond it, the unfinished feature ships off so that nothing the mod does not yet stand
 * behind runs unasked, the escape hatch over the vanilla filter row ships on so that the control it
 * gates is there before anyone needs it, the decivilised-territory switch ships on so that the
 * reading the map already drew is the one a player keeps by doing nothing, and
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
 * <p>Two row types are drawn and forgotten rather than stored, so their displayed text is the one thing
 * here free to change: a section caption, and a note carrying in one place what every description under
 * it would otherwise repeat. Both are drawn from the default-value column and the columns beside it are
 * inert, so an edit to one of those lands nowhere: the build stays green, the screen is unchanged, and
 * nothing says why. A caption carries its text in two columns and is held to keeping them equal; a note
 * draws from one of them alone, and is held to filling that one and leaving the other empty.
 */
final class LunaSettingsCsvIntegrationTest {

    private static final String BOOLEAN_ON_VALUE = "TRUE";
    private static final String BOOLEAN_OFF_VALUE = "FALSE";

    // The five rows hovering is switched at - the master, the pair that answers for every map layer,
    // and the political map's own pair - which are held to shipping on. The Java fallback beside each
    // getter only answers while LunaLib has no stored value, so it is this column a fresh player
    // actually gets; a row shipped off would read as a feature that is broken rather than one that is
    // switched off, and the two IDs a player may already have turned off are meant to keep switching
    // the same feedback off after being lifted a tier.
    private static final List<String> HOVER_TIER_FIELD_IDS = List.of(
        "kmu_map_visuals_hovering_isEnabled",
        "kmu_map_visuals_hovering_areEffectsEnabled",
        "kmu_map_visuals_hovering_areTooltipsEnabled",
        "kmu_map_politics_visuals_hoverHighlight_areEffectsEnabled",
        "kmu_map_politics_visuals_hoverTooltip_isEnabled");

    // The visibility overrides, held to shipping off for the reason the hover tiers above are
    // held to shipping on, and with more riding on it. Every one is named for what switching it
    // on reveals, so off is the whole section's safe state: a row shipped on would put an
    // abandoned station, a hidden base or an undiscovered colony on the map from the first day of
    // a campaign, which is a thing a player cannot un-see once the map has drawn it.
    //
    // The section's survey-level row is not among them and cannot be: it is a Radio, and there is
    // no "off" for it to ship at - a survey bar always applies. What holds its shipped value is the
    // choice-backed table above, which pins it to the constant the getter falls back on.
    private static final List<String> VISIBILITY_OVERRIDE_FIELD_IDS = List.of(
        "kmu_map_visibility_overrides_shouldShowUnseenAbandonedStations",
        "kmu_map_visibility_overrides_shouldShowUnseenHiddenMarkets",
        "kmu_map_visibility_overrides_shouldShowUndiscoveredMarkets",
        "kmu_map_visibility_overrides_shouldShowHiddenSystems");

    // The feature toggle of the one feature that is not finished, held to shipping off. Its own
    // check rather than a row in the walk above, because what it pins is not a value agreeing with
    // a constant but a decision: a feature the mod does not yet stand behind is not switched on for
    // a player who never asked for it, and this one writes to the campaign rather than drawing over
    // it. The row leaves this table when the feature is finished, along with the wording that says
    // so on the settings screen.
    private static final String UNFINISHED_FEATURE_FIELD_ID =
        "kmu_features_toggles_isMarketConditionManagerEnabled";

    // The escape hatch over the one widget the mod writes into that is not its own, held to shipping
    // on. Its own check for the reason the row above has one: what it pins is a decision rather than
    // two spellings of a value agreeing, which the walk over every switch already holds.
    private static final String FILTER_ROW_TOGGLE_FIELD_ID =
        "kmu_map_dev_ui_controls_mapLayersToggle_isEnabled";

    // The switch deciding whether a decivilised system is territory to paint, held to shipping on
    // for the same kind of reason and with its own check for the same one. Both readings of such a
    // system are defensible, so which one ships is a decision rather than a value: on is what the
    // map drew before the switch existed, and a player who prefers the other reading is the one who
    // should have to say so.
    private static final String DECIVILISED_TERRITORY_FIELD_ID =
        "kmu_map_politics_visuals_decivilised_shouldDrawTerritory";

    // The three Keycode rows. They are held here rather than in the numeric walk because there is
    // nothing to walk them against: no Java fallback mirrors a keycode, deliberately, so this column is
    // the only place in the mod each key is stated and nothing else would notice one drifting. What a
    // key must be worth is a decision like the two rows above, and it is made of two halves - the key
    // has to be free on both screens the shortcut is live on, and it has to still be the key the tab
    // prints, since a player reads it off the tab rather than out of the settings screen.
    private static final String NO_LAYER_KEY_FIELD_ID = "kmu_map_keybinds_layers_noLayer";
    private static final String POLITICAL_MAP_KEY_FIELD_ID = "kmu_map_keybinds_layers_factions";

    private static final String FILTER_ROW_TOGGLE_KEY_FIELD_ID =
        "kmu_map_keybinds_filters_mapLayersToggle";

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
        "Features",
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

    // The Radio fields whose options are not a LabeledChoice enum's labels, and so cannot be held
    // against one. The log level's options are log4j's own level names, which KmLogging hands
    // straight to the logger; naming them here is what keeps the coverage walk exhaustive without
    // pretending the row is choice-backed.
    private static final List<String> NON_CHOICE_BACKED_RADIO_FIELDS = List.of("kmu_dev_logging_level");

    // The Radio fields whose stored label a LabeledChoice enum maps back to a choice, each with the
    // constant its getter names as a fallback. Listed here rather than read from the settings classes
    // because the field IDs and the constants are private there - a typo in this table fails loudly
    // (no such row, or no such declaration) rather than quietly skipping a field.
    private static final List<ChoiceBackedRadio> CHOICE_BACKED_RADIOS = List.of(
        new ChoiceBackedRadio(
            "kmu_map_visuals_sidebar_colours_scheme",
            "DEFAULT_SIDEBAR_COLOUR_SCHEME",
            SidebarColourSchemeChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_visuals_sidebar_colours_chevron",
            "DEFAULT_SIDEBAR_CHEVRON_COLOUR",
            NotchChevronColourChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_domination_hiddenMarkets_weight_scaling",
            "DEFAULT_HIDDEN_MARKET_SCALING",
            HiddenMarketScalingChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_visibility_overrides_decivilisedWorldSurveyLevel",
            "DEFAULT_DECIVILISED_WORLD_SURVEY_LEVEL",
            SurveyLevelChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_dev_profiling_level",
            "DEFAULT_PROFILING_LEVEL",
            ProfilingLevelChoice.values()),
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
            "kmu_map_politics_visuals_faction_outerBorder_colour",
            "DEFAULT_FACTION_OUTER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_faction_innerBorder_colour",
            "DEFAULT_FACTION_INNER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_faction_fill_colour",
            "DEFAULT_FACTION_FILL_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_independent_outerBorder_colour",
            "DEFAULT_INDEPENDENT_OUTER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_independent_innerBorder_colour",
            "DEFAULT_INDEPENDENT_INNER_BORDER_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_independent_fill_colour",
            "DEFAULT_INDEPENDENT_FILL_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_hoverHighlight_colour",
            "DEFAULT_HOVER_HIGHLIGHT_COLOUR",
            FactionPaletteChoice.values()),
        new ChoiceBackedRadio(
            "kmu_map_politics_visuals_previewHighlight_colour",
            "DEFAULT_PREVIEW_HIGHLIGHT_COLOUR",
            FactionPaletteChoice.values()));

    // The numeric field types, whose default column holds a number the Java fallback beside the
    // getter has to agree with. Radio and Boolean rows are held against their own defaults above,
    // each in the terms that type is spelt in.
    private static final Set<String> NUMERIC_FIELD_TYPES = Set.of(DOUBLE_FIELD_TYPE, INT_FIELD_TYPE);

    // Strings that carry the mod prefix without being settings fields, and so are held against no row.
    // Every one is registered with the game rather than with LunaLib - a render surface's terrain ID,
    // or a tag another mod hangs on its own content - and they share the prefix because they are
    // KMU's, not because they are settings. Listed one by one so a genuine field ID cannot join them
    // by accident.
    private static final Set<String> NON_SETTINGS_PREFIXED_IDS = Set.of(
            "kmu_sector_map_layer_terrain",
            "kmu_sector_map_layer_starscape_terrain",
            "kmu_sector_map_layer_above_starscape_nebulae_terrain",
            "kmu_openly_known_colony");

    @Nested
    class RadioOptionLabels {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ChoiceBackedRadioFieldsProvider.class)
        void radioOptionLabelsAllResolveToTheirChoiceEnum(String fieldId, LabeledChoice[] choices) {

            var expectedLabels = Arrays.stream(choices).map(LabeledChoice::getLabel).toList();

            // A subset is legitimate - a field may offer only some of its enum's options - but an
            // option the enum cannot name is dead: picking it reads back as the fallback.
            assertThat(LunaSettingsTable.readOptions(fieldId))
                .isSubsetOf(expectedLabels);
        }

        // Takes the ID alone: the row's default is held against the row's own options, so the enum
        // the other check needs would only be an argument nothing reads.
        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ChoiceBackedRadioFieldIdsProvider.class)
        void radioOptionLabelsIncludeTheRowsOwnDefault(String fieldId) {

            assertThat(LunaSettingsTable.readOptions(fieldId))
                .contains(LunaSettingsTable.readDefaultValue(fieldId, RADIO_FIELD_TYPE));
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

            assertThat(SettingsSourceText.readFallbackLabel(defaultConstant, choices))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is what a fresh player is given and the constant is what answers while"
                        + " LunaLib has none, so two spellings give one look before the settings"
                        + " load and another after, with nothing to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(LunaSettingsTable.readDefaultValue(fieldId, RADIO_FIELD_TYPE));
        }
    }

    @Nested
    class RadioFieldCoverage {

        @Test
        void everyRadioFieldInTheFileIsClassifiedBySuite() {

            assertThat(LunaSettingsTable.readRadioFieldIds())
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

            assertThat(LunaSettingsTable.readDefaultValue(fieldId, BOOLEAN_FIELD_TYPE))
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

            assertThat(LunaSettingsTable.readDefaultValue(fieldId, BOOLEAN_FIELD_TYPE))
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
    class UnfinishedFeatureDefaults {

        @Test
        void theUnfinishedFeatureRowShipsSwitchedOff() {

            assertThat(LunaSettingsTable.readDefaultValue(UNFINISHED_FEATURE_FIELD_ID, BOOLEAN_FIELD_TYPE))
                .as(
                    "default of %s in %s: the feature is unfinished and changes campaign state, so"
                        + " it ships off and a player runs it only by asking for it",
                    UNFINISHED_FEATURE_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo(BOOLEAN_OFF_VALUE);
        }
    }

    @Nested
    class FilterRowToggleDefaults {

        @Test
        void theFilterRowToggleShipsSwitchedOn() {

            assertThat(LunaSettingsTable.readDefaultValue(FILTER_ROW_TOGGLE_FIELD_ID, BOOLEAN_FIELD_TYPE))
                .as(
                    "default of %s in %s: shipped off, neither map screen gets the control that shows"
                        + " and hides the layers, and a screen with no control never hides them - so"
                        + " the feature draws with no way to put it away, which reads as the"
                        + " attachment having broken rather than as a switch nobody has turned on",
                    FILTER_ROW_TOGGLE_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo(BOOLEAN_ON_VALUE);
        }
    }

    @Nested
    class DecivilisedTerritoryDefaults {

        @Test
        void theDecivilisedTerritoryRowShipsSwitchedOn() {

            assertThat(LunaSettingsTable.readDefaultValue(DECIVILISED_TERRITORY_FIELD_ID, BOOLEAN_FIELD_TYPE))
                .as(
                    "default of %s in %s: a decivilised system holds a settled place nobody speaks"
                        + " for, and the map has always read that as somewhere people live - so the"
                        + " switch ships at the reading already on screen, and the player who wants"
                        + " the other one is the one who says so",
                    DECIVILISED_TERRITORY_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo(BOOLEAN_ON_VALUE);
        }
    }

    @Nested
    class KeycodeDefaults {

        @Test
        void theNoLayerTabShipsOnTheNKey() {

            assertThat(LunaSettingsTable.readDefaultValue(NO_LAYER_KEY_FIELD_ID, KEYCODE_FIELD_TYPE))
                .as(
                    "default of %s in %s: 49 is LWJGL's KEY_N, the letter of the tab it jumps to, and"
                        + " free on both the map and intel screens the bar draws on",
                    NO_LAYER_KEY_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo("49");
        }

        @Test
        void thePoliticalMapTabShipsOnThePKey() {

            assertThat(LunaSettingsTable.readDefaultValue(POLITICAL_MAP_KEY_FIELD_ID, KEYCODE_FIELD_TYPE))
                .as(
                    "default of %s in %s: 25 is LWJGL's KEY_P, and clear of the intel screen's own"
                        + " bindings - its item actions take T, U and G, its tag filter Q and Ctrl+S",
                    POLITICAL_MAP_KEY_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo("25");
        }

        @Test
        void theFilterRowToggleShipsOnTheMKey() {

            assertThat(LunaSettingsTable.readDefaultValue(FILTER_ROW_TOGGLE_KEY_FIELD_ID, KEYCODE_FIELD_TYPE))
                .as(
                    "default of %s in %s: 50 is LWJGL's KEY_M, for map. Deliberately not a digit - the"
                        + " vanilla filter row this box is appended to keys its own six buttons to"
                        + " digits, and nothing in the game can be asked which of them a screen has"
                        + " already taken",
                    FILTER_ROW_TOGGLE_KEY_FIELD_ID,
                    SETTINGS_CSV)
                .isEqualTo("50");
        }
    }

    @Nested
    class NumericFallbackDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(NumericFieldDefaultsProvider.class)
        void numericFallbackDefaultsMatchTheirRowsOwnDefault(String fieldId, String fieldType) {

            var defaultConstant = SettingsSourceText.findNumericFallbackConstant(fieldId);

            assertThat(SettingsSourceText.readDeclaredNumber(defaultConstant))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is the number a fresh player is given and the constant is what answers"
                        + " while LunaLib has none, so two values paint one map before the settings"
                        + " load and another after, with nothing on screen to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(Double.parseDouble(
                    LunaSettingsTable.readDefaultValue(fieldId, fieldType)));
        }
    }

    @Nested
    class NumericFieldBounds {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(ClampedFieldBoundsProvider.class)
        void numericFieldBoundsMatchTheClampTheirGetterApplies(
                String fieldId,
                String fieldType,
                String minimumConstant,
                String maximumConstant) {

            assertThat(SettingsSourceText.readDeclaredNumber(minimumConstant))
                .as(
                    "%s in the settings sources against the low end of %s in %s: the slider's end is"
                        + " as far as a player can drag the row, and the clamp is how far the value"
                        + " is allowed once read, so two bounds let a saved value sit somewhere the"
                        + " slider will not go back to",
                    minimumConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(Double.parseDouble(
                    LunaSettingsTable.readMinValue(fieldId, fieldType)));

            assertThat(SettingsSourceText.readDeclaredNumber(maximumConstant))
                .as(
                    "%s in the settings sources against the high end of %s in %s",
                    maximumConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(Double.parseDouble(
                    LunaSettingsTable.readMaxValue(fieldId, fieldType)));
        }
    }

    @Nested
    class BooleanFallbackDefaults {

        @ParameterizedTest(name = "{0}")
        @ArgumentsSource(BooleanFieldIdsProvider.class)
        void booleanFallbackDefaultsMatchTheirRowsOwnDefault(String fieldId) {

            var defaultConstant = SettingsSourceText.findBooleanFallbackConstant(fieldId);

            assertThat(SettingsSourceText.readDeclaredFlag(defaultConstant))
                .as(
                    "%s in the settings sources against the default of %s in %s: the row's default"
                        + " is the state a fresh player is given and the constant is what answers"
                        + " while LunaLib has none, so a feature is switched one way before the"
                        + " settings load and the other after, with nothing on screen to say why",
                    defaultConstant,
                    fieldId,
                    SETTINGS_CSV)
                .isEqualTo(LunaSettingsTable.readDefaultValue(fieldId, BOOLEAN_FIELD_TYPE)
                    .equalsIgnoreCase(BOOLEAN_ON_VALUE));
        }
    }

    @Nested
    class ValueFieldIds {

        @Test
        void everyValueFieldIdIsNamedBySomeSource() {

            var namedFieldIds = SettingsSourceText.readFieldIdLiteralsInMainSources();

            assertThat(LunaSettingsTable.readValueFieldIds())
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
                .concat(
                    LunaSettingsTable.readDeclaredFieldIds().stream(),
                    NON_SETTINGS_PREFIXED_IDS.stream())
                .toList();

            assertThat(SettingsSourceText.readFieldIdLiteralsInMainSources())
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
    class TabPlacement {

        @Test
        void everyRowIsPlacedOnAKnownTab() {

            assertThat(LunaSettingsTable.readDeclaredTabs())
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

            assertThat(LunaSettingsTable.readDeclaredTabs())
                .as("tabs the layout names that %s places no row on", SETTINGS_CSV)
                .containsAll(KNOWN_TABS);
        }

        @Test
        void everyValueRowSitsOnItsSectionTab() {

            assertThat(LunaSettingsTable.findRowsStrandedFromTheirSection())
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

            assertThat(LunaSettingsTable.findTabsDeclaredInMoreThanOneRun())
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

            assertThat(LunaSettingsTable.findHeaderRowsWhoseCaptionColumnsDisagree())
                .as(
                    "section captions in %s whose name and drawn columns differ: LunaLib draws a"
                        + " Header from its default-value column, so the other one is inert and an"
                        + " edit to it changes nothing on screen",
                    SETTINGS_CSV)
                .isEmpty();
        }
    }

    @Nested
    class TextNotes {

        @Test
        void everyTextRowCarriesItsWordsInTheDrawnColumn() {

            assertThat(LunaSettingsTable.findTextRowsWhoseWordsAreNotDrawn())
                .as(
                    "prose rows in %s whose words are not where LunaLib reads them: a Text row is"
                        + " drawn from its default-value column alone and shows no name column, so"
                        + " words put beside it appear nowhere and an empty one draws a blank",
                    SETTINGS_CSV)
                .isEmpty();
        }
    }

    // The same table's field IDs alone, read off the record rather than back out of the arguments
    // the cases are handed, so the ID stays a String the compiler knows about.
    private static List<String> listChoiceBackedRadioFieldIds() {
        return CHOICE_BACKED_RADIOS
            .stream()
            .map(ChoiceBackedRadio::fieldId)
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

    /**
     * The choice-backed rows paired with the enum whose labels their options have to be.
     *
     * <p>The providers below are classes rather than factory methods so the cases name them by
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

    /** The same table's field IDs alone, for the checks that hold a row against itself. */
    static final class ChoiceBackedRadioFieldIdsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return listChoiceBackedRadioFieldIds().stream().map(Arguments::of);
        }
    }

    /**
     * The same table's field IDs paired with the constant naming each row's Java fallback, for the
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

            return LunaSettingsTable.readFieldIdsAndTypes()
                .stream()
                .filter(row -> NUMERIC_FIELD_TYPES.contains(row.fieldType()))
                .map(row -> Arguments.of(row.fieldId(), row.fieldType()));
        }
    }

    /**
     * The rows whose bounds are declared twice - once as the slider's ends in the file, once as the
     * clamp the getter puts round whatever LunaLib hands back - each with its own field type and
     * the two constants that clamp it.
     *
     * <p>A hand table rather than a walk of the file, because nothing in a row says whether its
     * getter clamps: every other numeric getter takes the stored value as it comes, and a walk over
     * all of them would have to invent a rule for the rows that bound nothing. The cost is that a
     * new clamp added without a line here goes unheld, which is why the clamp's own bounds are
     * named where they are declared and this list only holds them against the file.
     */
    static final class ClampedFieldBoundsProvider implements ArgumentsProvider {

        @Override
        public Stream<? extends Arguments> provideArguments(
                ParameterDeclarations parameters,
                ExtensionContext context) {

            return Stream.of(
                Arguments.of(
                    "kmu_map_visuals_sidebar_opacity",
                    INT_FIELD_TYPE,
                    "MIN_SIDEBAR_OPACITY_PERCENT",
                    "MAX_SIDEBAR_OPACITY_PERCENT"),
                Arguments.of(
                    "kmu_map_visuals_sidebar_scrollbarThickness",
                    INT_FIELD_TYPE,
                    "MIN_SIDEBAR_SCROLLBAR_THICKNESS",
                    "MAX_SIDEBAR_SCROLLBAR_THICKNESS"),
                Arguments.of(
                    "kmu_map_dev_refresh_pollSeconds",
                    INT_FIELD_TYPE,
                    "MIN_POLL_SECONDS",
                    "MAX_POLL_SECONDS"),
                Arguments.of(
                    "kmu_map_dev_hatchFill_width",
                    DOUBLE_FIELD_TYPE,
                    "MIN_HATCH_WIDTH_PERCENT",
                    "MAX_HATCH_WIDTH_PERCENT"));
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

            return LunaSettingsTable.readFieldIdsAndTypes()
                .stream()
                .filter(row -> BOOLEAN_FIELD_TYPE.equals(row.fieldType()))
                .map(row -> Arguments.of(row.fieldId()));
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
     * @param fieldId         the LunaLib field ID the row is stored under
     * @param defaultConstant the name of the private constant the getter passes as its fallback
     * @param choices         the enum constants the row's options are the labels of
     */
    private record ChoiceBackedRadio(
        String fieldId,
        String defaultConstant,
        LabeledChoice[] choices) {
    }
}
