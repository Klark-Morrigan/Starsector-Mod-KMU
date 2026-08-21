package kmu;

import com.fs.starfarer.api.BaseModPlugin;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins what the entry point itself is answerable for: being the plugin the engine constructs.
 *
 * <p>What each installer registers is pinned beside that installer, not here. The entry point only
 * names them in order, and a suite that re-asserted their registrations would be a second copy of
 * every installer's own contract - one that passes for as long as nobody moves a listener.
 */
class KMU_ModPluginTest {

    @Nested
    class ModIdentity {

        @Test
        void extendsStarsectorBaseModPlugin() {
            assertThat(new KMU_ModPlugin())
                .isInstanceOf(BaseModPlugin.class);
        }
    }
}
