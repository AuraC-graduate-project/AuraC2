package com.server.contestControl.contestServer.moderation.service;

import com.server.contestControl.contestServer.moderation.dto.ModerationAuditLogResponse;
import com.server.contestControl.contestServer.moderation.enums.ModerationActionType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ContestModerationCsvExporterTest {

    private final ContestModerationCsvExporter exporter = new ContestModerationCsvExporter();

    @Test
    void csvExportEscapesDangerousSpreadsheetValues() {
        ModerationAuditLogResponse log = new ModerationAuditLogResponse(
                1L,
                4L,
                "Contest",
                2L,
                "team1",
                9L,
                "admin",
                null,
                null,
                null,
                null,
                null,
                ModerationActionType.HIDE_FROM_SCOREBOARD,
                "=cmd|' /C calc'!A0",
                "{\"status\":\"ACTIVE\"}",
                "{\"hiddenFromScoreboard\":true}",
                LocalDateTime.parse("2026-05-25T10:15:00")
        );

        String csv = new String(exporter.export(List.of(log)), StandardCharsets.UTF_8);

        assertThat(csv).contains("\"'=cmd|' /C calc'!A0\"");
        assertThat(csv).contains("\"{\"\"status\"\":\"\"ACTIVE\"\"}\"");
    }
}
