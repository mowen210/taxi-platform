package com.taxi.common.config;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DotenvParserTest {

    @Test
    void parsesCommentsExportQuotesAndInlineComments() {
        Map<String, String> values = DotenvParser.parse(List.of(
                "# comment",
                "",
                "DB_HOST=mysql",
                "export SERVER_PORT=8080",
                "JWT_SECRET=\"a b#c\"",
                "EMPTY=",
                "PLAIN=value # tail",
                "INVALID LINE"
        ));

        assertEquals("mysql", values.get("DB_HOST"));
        assertEquals("8080", values.get("SERVER_PORT"));
        assertEquals("a b#c", values.get("JWT_SECRET"));
        assertEquals("", values.get("EMPTY"));
        assertEquals("value", values.get("PLAIN"));
        assertFalse(values.containsKey("INVALID LINE"));
    }

    @Test
    void unescapesDoubleQuotedValues() {
        Map<String, String> values = DotenvParser.parse(List.of(
                "A=\"line1\\nline2\"",
                "B='keep\\nliteral'"
        ));
        assertEquals("line1\nline2", values.get("A"));
        assertEquals("keep\\nliteral", values.get("B"));
    }

    @Test
    void ignoresBlankAndMalformedLines() {
        Map<String, String> values = DotenvParser.parse(List.of(" ", "#x", "=x", "1BAD=x"));
        assertTrue(values.isEmpty());
    }
}
