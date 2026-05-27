package com.server.contestControl.contestServer.moderation.service;

import com.server.contestControl.contestServer.moderation.dto.ModerationAuditLogResponse;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class ContestModerationCsvExporter {

    public byte[] export(List<ModerationAuditLogResponse> logs) {
        StringBuilder csv = new StringBuilder();
        csv.append("timestamp,contest_id,contest,team_id,team,admin_id,admin,problem_id,problem,language_id,source_hash,execution_mode,action,reason,old_value,new_value\n");
        for (ModerationAuditLogResponse log : logs) {
            appendRow(csv,
                    log.createdAt() == null ? "" : log.createdAt().toString(),
                    log.contestId(),
                    log.contestTitle(),
                    log.teamId(),
                    log.teamUsername(),
                    log.adminId(),
                    log.adminUsername(),
                    log.problemId(),
                    log.problemTitle(),
                    log.languageId(),
                    log.sourceHash(),
                    log.executionMode(),
                    log.actionType() == null ? "" : log.actionType().name(),
                    log.reason(),
                    log.oldValueJson(),
                    log.newValueJson()
            );
        }
        return csv.toString().getBytes(StandardCharsets.UTF_8);
    }

    private void appendRow(StringBuilder csv, Object... values) {
        for (int i = 0; i < values.length; i++) {
            if (i > 0) {
                csv.append(',');
            }
            csv.append(csvCell(values[i] == null ? "" : String.valueOf(values[i])));
        }
        csv.append('\n');
    }

    private String csvCell(String value) {
        String safe = spreadsheetSafe(value);
        return "\"" + safe.replace("\"", "\"\"") + "\"";
    }

    private String spreadsheetSafe(String value) {
        if (value.isEmpty()) {
            return value;
        }
        char first = value.charAt(0);
        if (first == '=' || first == '+' || first == '-' || first == '@' || first == '\t'
                || first == '\r' || first == '\n') {
            return "'" + value;
        }
        return value;
    }
}
