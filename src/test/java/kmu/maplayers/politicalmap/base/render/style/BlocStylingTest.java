package kmu.maplayers.politicalmap.base.render.style;

import kmu.maplayers.politicalmap.base.BlocStyleAdjustment;
import kmu.maplayers.politicalmap.base.render.style.theme.BorderSmoothingStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.CategoryStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.ElementStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.GlobalStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HatchStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverGlowStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverHighlightStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.HoverWashStyle;
import kmu.maplayers.politicalmap.base.render.style.theme.MapCategory;
import kmu.maplayers.politicalmap.base.render.style.theme.RenderStyle;
import kmu.settings.FactionPaletteChoice;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.EnumMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the cascade from a resolved style decision onto the pass's actual theme: which category
 * bundle a bloc paints from, and the one field that crosses between bundles - a desaturated
 * bloc's fill opacity, held at the faction weight so the desaturated surface reads uniform.
 */
final class BlocStylingTest {
    // The two owned categories' fill opacities, kept distinct so an observed opacity names the
    // category it was sourced from.
    private static final double FACTION_FILL_OPACITY = 0.4;
    private static final double INDEPENDENT_FILL_OPACITY = 0.2;
    // Values only the independent bundle carries, so they witness that the rest of that bundle
    // survives a desaturated pass rather than being replaced wholesale.
    private static final double INDEPENDENT_INNER_WIDTH = 2.0;
    private static final double INDEPENDENT_OUTER_OPACITY = 0.5;

    @Nested
    class ResolveFrom {

        @Test
        void resolveFromPaintsFromTheFactionBundleWhenTheBlocDoesNotRecede() {
            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(false, BlocStyleAdjustment.NONE));

            assertThat(styling.style().fill().opacity()).isEqualTo(FACTION_FILL_OPACITY);
        }

        @Test
        void resolveFromCarriesTheDecisionsAdjustmentThrough() {
            var adjustment = new BlocStyleAdjustment(0.5, true);

            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(false, adjustment));

            assertThat(styling.adjustment()).isEqualTo(adjustment);
        }

        @Test
        void resolveFromKeepsTheIndependentFillOpacityWhenTheBlocRecedesInFullColour() {
            // In full colour the independent bundle keeps its own lighter fill, so independent
            // space still recedes behind faction ground.
            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(true, BlocStyleAdjustment.NONE));

            assertThat(styling.style().fill().opacity()).isEqualTo(INDEPENDENT_FILL_OPACITY);
        }

        @Test
        void resolveFromHoldsADesaturatedBlocAtTheFactionFillOpacity() {
            // Desaturated ground holds the one faction fill opacity, so the whole desaturated
            // surface reads uniform rather than splitting into two weights of grey.
            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(true, new BlocStyleAdjustment(1.0, true)));

            assertThat(styling.style().fill().opacity()).isEqualTo(FACTION_FILL_OPACITY);
        }

        @Test
        void resolveFromKeepsTheIndependentFillColourWhenDesaturated() {
            // Only the opacity crosses over: which palette slot the fill names is still the
            // independent bundle's own choice.
            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(true, new BlocStyleAdjustment(1.0, true)));

            assertThat(styling.style().fill().color()).isEqualTo(FactionPaletteChoice.SECONDARY);
        }

        @Test
        void resolveFromKeepsTheRestOfTheIndependentBundleWhenDesaturated() {
            // The borders and widths distinguish independent ground without breaking the fill's
            // uniformity, so they survive the crossover untouched.
            var styling = BlocStyling.resolveFrom(
                    theme(),
                    new BlocStyleDecision(true, new BlocStyleAdjustment(1.0, true)));

            assertThat(styling.style().innerWidth()).isEqualTo(INDEPENDENT_INNER_WIDTH);
            assertThat(styling.style().outer().opacity()).isEqualTo(INDEPENDENT_OUTER_OPACITY);
        }
    }

    // A theme whose two owned bundles differ in every field these tests read back, so an
    // assertion on any one of them names which bundle the cascade sourced it from. The two
    // factionless categories are never reached by this cascade, so they reuse the faction bundle.
    private static RenderStyle theme() {
        var factionStyle = new CategoryStyle(
                new ElementStyle(FactionPaletteChoice.PRIMARY, FACTION_FILL_OPACITY),
                new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0), 3.0,
                new ElementStyle(FactionPaletteChoice.PRIMARY, 1.0), 1.0);
        var independentStyle = new CategoryStyle(
                new ElementStyle(FactionPaletteChoice.SECONDARY, INDEPENDENT_FILL_OPACITY),
                new ElementStyle(FactionPaletteChoice.SECONDARY, INDEPENDENT_OUTER_OPACITY), 3.0,
                new ElementStyle(FactionPaletteChoice.SECONDARY, 1.0), INDEPENDENT_INNER_WIDTH);
        Map<MapCategory, CategoryStyle> categories = new EnumMap<>(MapCategory.class);
        categories.put(MapCategory.FACTION, factionStyle);
        categories.put(MapCategory.INDEPENDENT, independentStyle);
        categories.put(MapCategory.DECIVILISED, factionStyle);
        categories.put(MapCategory.UNINHABITED, factionStyle);
        return new RenderStyle(inertGlobalStyle(), categories);
    }

    // The global tier is carried untouched by this cascade - it maps categories, not sector-wide
    // knobs - so every value here is inert.
    private static GlobalStyle inertGlobalStyle() {
        return new GlobalStyle(
                new HatchStyle(0, 0, 0),
                new BorderSmoothingStyle(false, false, 0, 0, 0),
                new HoverHighlightStyle(
                        FactionPaletteChoice.NONE,
                        new HoverGlowStyle(0, 0, 0, 0, 0),
                        new HoverWashStyle(0, 0, 0)),
                0.3);
    }
}
