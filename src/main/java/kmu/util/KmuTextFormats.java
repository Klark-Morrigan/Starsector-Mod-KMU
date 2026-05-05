package kmu.util;

import java.util.Optional;

public final class KmuTextFormats {
    private KmuTextFormats() {
    }

    /**
     * Combines two optional strings into "main (parenthetical)" form.
     * Returns whichever is present when only one is, or empty when neither is.
     */
    public static Optional<String> joinWithParenthetical(
            Optional<String> main,
            Optional<String> parenthetical) {
        if (!main.isPresent() && !parenthetical.isPresent()) {
            return Optional.empty();
        }
        if (!main.isPresent()) {
            return parenthetical;
        }
        if (!parenthetical.isPresent()) {
            return main;
        }
        return Optional.of(main.get() + " (" + parenthetical.get() + ")");
    }
}
