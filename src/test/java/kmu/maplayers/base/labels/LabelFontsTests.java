package kmu.maplayers.base.labels;

import kmlib.starsector.ui.font.StarsectorFont;

import kmu.maplayers.base.faces.ProbedText;
import kmu.maplayers.base.faces.SettledFaces;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pins which face the map labels ask for and what they ask it to hold: the largest insignia cut, held
 * to faction names alone, since a label draws a name and nothing else.
 */
final class LabelFontsTests {

    @Nested
    final class SettleMapLabelFace {

        @Test
        void answersWhatTheSectorSettledTheLargestCutOnAgainstFactionNames() {
            // A localised install's answer: the largest cut holds no localised glyph, the next one does.
            var settledFacesMock = mock(SettledFaces.class);

            when(settledFacesMock.settleFace(StarsectorFont.VANILLA_INSIGNIA_42, Set.of(ProbedText.FACTION_NAMES)))
                .thenReturn(StarsectorFont.VANILLA_INSIGNIA_25);

            assertThat(LabelFonts.settleMapLabelFace(settledFacesMock))
                .isEqualTo(StarsectorFont.VANILLA_INSIGNIA_25);
        }
    }
}
