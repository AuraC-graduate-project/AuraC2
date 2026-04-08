package com.server.contestControl.submissionServer.dto;


import lombok.Data;

@Data
public class Judge0Response {

    private String stdout;
    private String stderr;
    private String time;        // example: "0.008"
    private Integer memory;     // in KB
    private Status status;      // Nested object: {id, description}

    @Data
    public static class Status {
        private int id;         // Judge0 status ID
        private String description;
    }

    // Helpers ↓
    public int getTimeAsInt() {
        if (time == null) return 0;
        return (int) (Double.parseDouble(time) * 1000); // milliseconds
    }

    public int getMemoryAsInt() {
        return memory != null ? memory : 0;
    }
}
