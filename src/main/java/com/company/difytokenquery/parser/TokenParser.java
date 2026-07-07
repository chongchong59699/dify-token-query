package com.company.difytokenquery.parser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TokenParser {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("app-[A-Za-z0-9_-]{16,128}");

    public List<String> parse(String input) {
        Set<String> tokens = new LinkedHashSet<String>();
        if (input == null || input.isEmpty()) {
            return new ArrayList<String>();
        }
        Matcher matcher = TOKEN_PATTERN.matcher(input);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return new ArrayList<String>(tokens);
    }

    public boolean isValidToken(String token) {
        return token != null && TOKEN_PATTERN.matcher(token).matches();
    }
}
