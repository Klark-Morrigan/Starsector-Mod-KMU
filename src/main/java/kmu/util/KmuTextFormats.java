package kmu.util;

import kmlib.text.KmlibStrings;

import java.util.Optional;

public final class KmuTextFormats {

    private KmuTextFormats() {
    }

    /** See {@link KmlibStrings#joinWithParenthetical}. */
    public static Optional<String> joinWithParenthetical(
            Optional<String> main,
            Optional<String> parenthetical) {

        return KmlibStrings.joinWithParenthetical(
            main.orElse(null),
            parenthetical.orElse(null));
    }
}
