package com.server.contestControl.submissionServer.util;

public class LanguageMapper {

    public static int convertLanguage(String language) {
        return switch (language.toLowerCase()) {
            case "c"       -> 50;
            case "cpp", "c++", "g++" -> 54;
            case "java"    -> 62;
            case "python", "py", "python3" -> 71;
            case "javascript", "js", "node" -> 63;
            case "go"      -> 60;
            default -> throw new IllegalArgumentException("Unsupported language: " + language);
        };
    }
}