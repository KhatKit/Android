package heizige.kk.khatkit.app.core.ui.components.message

import org.junit.Assert.assertEquals
import org.junit.Test

class MarkdownParagraphExtensionsTest {
    @Test
    fun `splits prose on blank lines`() {
        assertEquals(listOf("Hello.", "Next paragraph."), splitMarkdownParagraphs("Hello.\n\nNext paragraph."))
    }

    @Test
    fun `ignores extra blank lines outside blocks`() {
        assertEquals(listOf("One", "Two"), splitMarkdownParagraphs("\n\nOne\n  \n\nTwo\n\n"))
    }

    @Test
    fun `preserves blank lines inside fenced code`() {
        assertEquals(listOf("Before", "```kotlin\nval first = 1\n\nval second = 2\n```", "After"), splitMarkdownParagraphs("Before\n\n```kotlin\nval first = 1\n\nval second = 2\n```\n\nAfter"))
    }

    @Test
    fun `keeps shorter fences inside longer fences`() {
        assertEquals(listOf("````markdown\n```\n\ninside\n```\n````", "After"), splitMarkdownParagraphs("````markdown\n```\n\ninside\n```\n````\n\nAfter"))
    }

    @Test
    fun `preserves tilde fenced blocks`() {
        assertEquals(listOf("~~~\none\n\ntwo\n~~~", "After"), splitMarkdownParagraphs("~~~\none\n\ntwo\n~~~\n\nAfter"))
    }

    @Test
    fun `preserves blank lines in dollar math`() {
        assertEquals(listOf("\$\$\nx=1\n\ny=2\n$$", "After"), splitMarkdownParagraphs("\$\$\nx=1\n\ny=2\n$$\n\nAfter"))
    }

    @Test
    fun `preserves bracket math`() {
        assertEquals(listOf("\\[\nx=1\n\ny=2\n\\]", "After"), splitMarkdownParagraphs("\\[\nx=1\n\ny=2\n\\]\n\nAfter"))
    }

    @Test
    fun `keeps indented list continuation with its item`() {
        assertEquals(listOf("- item\n\n  continuation", "After"), splitMarkdownParagraphs("- item\n\n  continuation\n\nAfter"))
    }

    @Test
    fun `keeps ordered list continuation with its item`() {
        assertEquals(listOf("1. item\n\n   continuation", "After"), splitMarkdownParagraphs("1. item\n\n   continuation\n\nAfter"))
    }

    @Test
    fun `keeps unfinished code block intact while streaming`() {
        assertEquals(listOf("Before", "```\nfirst\n\nsecond"), splitMarkdownParagraphs("Before\n\n```\nfirst\n\nsecond"))
    }

    @Test
    fun `does not split a single line break`() {
        assertEquals(listOf("One\nTwo"), splitMarkdownParagraphs("One\nTwo"))
    }

    @Test
    fun `empty content has no paragraphs`() {
        assertEquals(emptyList<String>(), splitMarkdownParagraphs("\n  \n"))
    }
}
