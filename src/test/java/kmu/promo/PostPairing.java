package kmu.promo;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Reads the Fossic post and its review pairing as plain sentences, so a suite can hold the two
 * together.
 *
 * <p>The post source carries English notes above a {@code POST BEGINS} line and the Discuz post below
 * it; the pairing file lists every line of that post under a {@code - ZH:} item beside its English.
 * Both are compared with markup and whitespace stripped, since the pairing quotes the words and not the
 * tags around them.
 */
final class PostPairing {

    /** The pairing's word for a line only the English post carries. */
    private static final String NO_CHINESE_LINE = "none";

    private static final String POST_BEGINS_MARKER = "==== POST BEGINS ====";

    // An attachment ID is the forum's handle on a file, not words of the post, and a re-upload changes it.
    private static final Pattern ATTACHMENT = Pattern.compile("\\[(attach|attachimg)\\][^\\[]*\\[/\\1\\]");

    private static final Pattern BBCODE_TAG = Pattern.compile("\\[/?[a-z*][^\\]]*\\]");

    private static final Pattern CHINESE_CHARACTER = Pattern.compile("[\\p{IsHan}]");

    private static final Pattern PAIRED_CHINESE_LINE = Pattern.compile("^\\s+- ZH: (.*)$", Pattern.MULTILINE);

    // The post's sentence ends, with the line break: a list item or a caption is a sentence of its own.
    private static final Pattern SENTENCE_END = Pattern.compile("(?<=[。：；！？])|\\n");

    // The one tag whose attribute the reader sees: a folded section's title, kept as a line of text.
    private static final Pattern SPOILER_TITLE = Pattern.compile("\\[spoiler=([^\\]]*)\\]");

    // A value filled in at release time, as <version> in the notes and <<version>> in the post; the
    // pairing quotes them as written, so both sides lose them before they are compared.
    private static final Pattern TEXT_PLACEHOLDER = Pattern.compile("<+[^<>]*>+");

    private static final Pattern WHITESPACE_OR_BACKTICK = Pattern.compile("[\\s`]");

    private PostPairing() {
    }

    /**
     * Every Chinese line the pairing quotes, normalised, leaving out the lines marked as English-only.
     */
    static List<String> listPairedSentences(Path pairingFile) {

        return PAIRED_CHINESE_LINE.matcher(readText(pairingFile))
            .results()
            .map(match -> match.group(1).trim())
            .filter(line -> !line.equals(NO_CHINESE_LINE))
            .filter(line -> CHINESE_CHARACTER.matcher(line).find())
            .map(PostPairing::stripMarkup)
            .map(PostPairing::normalise)
            .toList();
    }

    /** Every Chinese sentence of the post body, normalised, with its markup and placeholders removed. */
    static List<String> listPostSentences(Path postFile) {

        var text = readText(postFile);
        var post = text.substring(text.indexOf(POST_BEGINS_MARKER) + POST_BEGINS_MARKER.length());

        return SENTENCE_END.splitAsStream(stripMarkup(post))
            .filter(sentence -> CHINESE_CHARACTER.matcher(sentence).find())
            .map(PostPairing::normalise)
            .toList();
    }

    /** The whole post source, notes included, normalised the way the sentences are. */
    static String readNormalisedSource(Path postFile) {
        return normalise(stripMarkup(readText(postFile)));
    }

    private static String normalise(String text) {
        return WHITESPACE_OR_BACKTICK.matcher(text).replaceAll("");
    }

    private static String readText(Path file) {
        try {
            return Files.readString(file, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String stripMarkup(String text) {
        var withTitles = SPOILER_TITLE.matcher(text).replaceAll("$1\n");
        var withoutAttachments = ATTACHMENT.matcher(withTitles).replaceAll("");
        var withoutPlaceholders = TEXT_PLACEHOLDER.matcher(withoutAttachments).replaceAll("");
        return BBCODE_TAG.matcher(withoutPlaceholders).replaceAll("");
    }
}
