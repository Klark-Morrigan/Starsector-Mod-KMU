package kmu;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the mod id against its literal, because it is not only ours to choose: LunaLib keys the
 * settings screen on it, and it names the subtree the library's scoped loggers land in. Changing it
 * silently orphans a player's stored settings and stops their verbosity setting reaching library
 * code, neither of which fails loudly.
 */
final class KmuModTest {

    @Nested
    class ModIdentity {

        @Test
        void exposesStableModIdentityConstants() {
            assertThat(KmuMod.MOD_ID).isEqualTo("kmu");
            assertThat(KmuMod.MOD_NAME).isEqualTo("Klark Morrigan's Utilities");
        }
    }
}
