package dev.prateekthakur.openrest

import dev.prateekthakur.openrest.utils.*

import dev.prateekthakur.openrest.utils.UrlCodec
import kotlin.test.*

class UrlCodecTest {
    @Test fun decode() {
        assertEquals("a b&c=d/é", UrlCodec.decode("a%20b%26c%3Dd%2F%C3%A9"))
        assertEquals("1+2", UrlCodec.decode("1+2"))          // '+' is kept, like decodeURIComponent
        assertEquals("100%", UrlCodec.decode("100%"))        // malformed input is returned unchanged
    }
    @Test fun encode() {
        assertEquals("a%20b%26c", UrlCodec.encode("a b&c"))
        assertEquals("{{host}}/a%20b", UrlCodec.encode("{{host}}/a b").replace("%2F", "/"))   // variables are never encoded
        assertEquals("~-_.", UrlCodec.encode("~-_."))
    }
    @Test fun roundTrip() { val s = "name=Zoë & co/100%?x"; assertEquals(s, UrlCodec.decode(UrlCodec.encode(s))) }
    @Test fun detect() { assertTrue(UrlCodec.looksEncoded("a%20b")); assertFalse(UrlCodec.looksEncoded("plain 100%")) }
}
