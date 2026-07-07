package com.company.difytokenquery.parser;

import org.junit.Test;

import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TokenParserTest {
    private final TokenParser parser = new TokenParser();

    @Test
    public void parseShouldExtractTokensFromMixedTextAndKeepOrder() {
        String text = "token='app-PBumrt3c1kmbb5PU9Nt3Kcgl';\n"
                + "Authorization: Bearer app-ABCDEFGH12345678_extra\n"
                + "重复 app-PBumrt3c1kmbb5PU9Nt3Kcgl, 无关内容";

        List<String> tokens = parser.parse(text);

        assertEquals(2, tokens.size());
        assertEquals("app-PBumrt3c1kmbb5PU9Nt3Kcgl", tokens.get(0));
        assertEquals("app-ABCDEFGH12345678_extra", tokens.get(1));
    }

    @Test
    public void parseShouldReturnEmptyListWhenNoTokenExists() {
        assertTrue(parser.parse("没有 token").isEmpty());
        assertTrue(parser.parse(null).isEmpty());
    }

    @Test
    public void isValidTokenShouldFollowConfiguredRule() {
        assertTrue(parser.isValidToken("app-PBumrt3c1kmbb5PU9Nt3Kcgl"));
    }
}
