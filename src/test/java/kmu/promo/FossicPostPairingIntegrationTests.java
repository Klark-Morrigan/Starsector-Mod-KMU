package kmu.promo;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Holds the Fossic post to its review pairing, over the committed sources under {@code promo/}.
 *
 * <p>The pairing is how the Chinese post is reviewed: every line of it beside the English line it
 * translates. Nothing else reads the two together, so a sentence edited in one and not the other
 * drifts unseen until the next review reads a translation of words the post no longer says.
 */
final class FossicPostPairingIntegrationTests {

    private static final Path PAIRING_FILE = Path.of("promo", "fossic-post.pairs.md");

    private static final Path POST_FILE = Path.of("promo", "fossic-post.bbcode");

    @Nested
    class ListPairedSentences {

        @Test
        void everyPairedLineIsInThePostSource() {

            // The form's values sit in the post's notes rather than its body, so the whole source is
            // the text a paired line must come from.
            var source = PostPairing.readNormalisedSource(POST_FILE);

            assertThat(PostPairing.listPairedSentences(PAIRING_FILE))
                .allSatisfy(line -> assertThat(source).contains(line));
        }
    }

    @Nested
    class ListPostSentences {

        @Test
        void everyPostSentenceIsPaired() {

            var paired = String.join("", PostPairing.listPairedSentences(PAIRING_FILE));

            assertThat(PostPairing.listPostSentences(POST_FILE))
                .allSatisfy(sentence -> assertThat(paired).contains(sentence));
        }
    }
}
