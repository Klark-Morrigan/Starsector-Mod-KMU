package kmu.maplayers.base.faces;

import com.fs.starfarer.api.campaign.SectorAPI;

import kmlib.starsector.ui.font.FaceResolver;
import kmlib.starsector.ui.font.StarsectorFont;
import kmlib.testfixtures.starsector.ui.font.FaceLineHeightReaderFake;
import kmlib.testfixtures.starsector.ui.font.GlyphCoverageReaderFake;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Pins which face a KMU text settles on: the one it asks for where the atlas holds what it draws, the next
 * cut down where it does not, held to the kinds of text it is made of and to nothing else.
 */
final class SettledFacesTests {

    // "Hegemony" (U+9738 U+4E3B), a faction name as a localised install draws it.
    private static final String LOCALISED_FACTION_NAME = "霸主";

    // A localised install: every atlas holds Latin text, and the middle insignia cut the localised script
    // besides - so the largest cut, asked for a localised name, has one step to take.
    private static final FaceResolver LOCALISED_INSTALL_RESOLVER = new FaceResolver(
        FaceLineHeightReaderFake.createVanillaLineHeights(),
        GlyphCoverageReaderFake.createLatinOnlyCoverage().coveringEveryCharacter(StarsectorFont.VANILLA_INSIGNIA_25),
        StarsectorFont.VANILLA_INSIGNIA_15);

    // The sector's names: a faction named in the localised script, and everything else Latin.
    private static final Map<ProbedText, List<String>> LOCALISED_TEXTS = Map.of(
        ProbedText.FACTION_NAMES, List.of("Tri-Tachyon", LOCALISED_FACTION_NAME),
        ProbedText.PLACE_NAMES, List.of("Corvus", "Jangala"),
        ProbedText.MOD_STRINGS, List.of("Political map"));

    private static SettledFaces createLocalisedFaces(BiFunction<SectorAPI, ProbedText, List<String>> reader) {
        return new SettledFaces(mock(SectorAPI.class), () -> LOCALISED_INSTALL_RESOLVER, reader);
    }

    private static SettledFaces createLocalisedFaces() {
        return createLocalisedFaces((sector, probe) -> LOCALISED_TEXTS.get(probe));
    }

    @Nested
    final class SettleFace {

        @Test
        void keepsTheRequestedFaceWhereItsAtlasHoldsEveryProbedText() {

            assertThat(createLocalisedFaces()
                    .settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.PLACE_NAMES)))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_42);
        }

        @Test
        void stepsDownTheFamilyWhereTheRequestedAtlasLacksAProbedText() {
            // The map-label case: the largest cut holds no localised glyph, the next one down does.
            assertThat(createLocalisedFaces()
                    .settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES)))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_25);
        }

        @Test
        void holdsTheFaceToTheKindsItsTextIsMadeOfAndNoOthers() {
            // The localised name is among the factions, which this text never draws.
            assertThat(createLocalisedFaces()
                    .settleFace(
                        StarsectorFont.VANILLA_INSIGNIA_42,
                        Set.of(ProbedText.PLACE_NAMES, ProbedText.MOD_STRINGS)))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_42);
        }

        @Test
        void readsEachKindOnceHoweverManyTextsAreHeldToIt() {
            // Reading a kind walks every name the sector holds, which is the cost holding the faces saves.
            var factionReads = new int[1];
            var faces = createLocalisedFaces((sector, probe) -> {
                if (probe == ProbedText.FACTION_NAMES) {
                    factionReads[0]++;
                }
                return LOCALISED_TEXTS.get(probe);
            });

            faces.settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES));
            faces.settleFace(StarsectorFont.VANILLA_INSIGNIA_15, Set.of(ProbedText.FACTION_NAMES));
            faces.settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES));

            assertThat(factionReads[0])
                .isEqualTo(1);
        }

        @Test
        void keepsTheRequestedFaceWithoutReadingAnythingWhereThereIsNoSector() {
            // The detached machinery's faces: no game is loaded, so there is neither a name to hold a face to
            // nor an install worth asking about.
            Supplier<FaceResolver> refusingResolverSource = () -> {
                throw new AssertionError("no resolver should be built without a sector");
            };

            var faces = new SettledFaces(
                null,
                refusingResolverSource,
                (sector, probe) -> {
                    throw new AssertionError("no text should be read without a sector");
                });

            assertThat(faces.settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES)))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_42);
        }
    }
}
