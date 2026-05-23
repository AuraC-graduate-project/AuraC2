package com.server.contestControl.submissionServer.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Data
public class Judge0Response {

    private String stdout;
    private String stderr;

    @JsonProperty("compile_output")
    private String compileOutput;

    private String message;
    private String time;
    private Integer memory;
    private Status status;

    // Useful if Judge0 sends token in callback payload.
    private String token;

    @Data
    public static class Status {
        private int id;
        private String description;
    }

    public int getTimeAsInt() {
        if (time == null) {
            return 0;
        }

        try {
            return (int) (Double.parseDouble(time) * 1000);
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public int getMemoryAsInt() {
        return memory != null ? memory : 0;
    }

    public String getDecodedStdout() {
        return decodeTextIfBase64(stdout);
    }

    public String getDecodedStderr() {
        return decodeTextIfBase64(stderr);
    }

    public String getDecodedCompileOutput() {
        return decodeTextIfBase64(compileOutput);
    }

    public String getDecodedMessage() {
        return decodeTextIfBase64(message);
    }

    private String decodeTextIfBase64(String value) {
        if (value == null || value.isBlank()) {
            return value;
        }

        String candidate = value.trim();

        if (!looksLikeBase64(candidate)) {
            return value;
        }

        try {
            byte[] decodedBytes = Base64.getDecoder().decode(candidate);
            if (decodedBytes.length == 0) {
                return value;
            }

            String decoded = new String(decodedBytes, StandardCharsets.UTF_8);

            if (!isSafeDecodedText(decoded)) {
                return value;
            }

            return decoded;
        } catch (IllegalArgumentException ex) {
            return value;
        }
    }

    private boolean looksLikeBase64(String value) {
        if (value.length() % 4 != 0) {
            return false;
        }

        return value.matches("^[A-Za-z0-9+/]+={0,2}$");
    }

    private boolean isSafeDecodedText(String value) {
        if (value.indexOf('\uFFFD') >= 0) {
            return false;
        }

        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);

            boolean printable =
                    ch == '\n'
                            || ch == '\r'
                            || ch == '\t'
                            || ch >= 32;

            if (!printable) {
                return false;
            }
        }

        return true;
    }
}