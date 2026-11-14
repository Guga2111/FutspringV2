package com.futspring.backend.domain.telemetry;

import com.futspring.backend.domain.telemetry.dto.ClientErrorRequestDTO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Writes an error reported by the web app as one WARN line with event=client_error (no database). Alloy ships it
 * to Loki; the key-value pairs are fields of the ECS JSON line. Never the caller, the IP or a query string.
 */
@Slf4j
@Service
public class ClientErrorService {

    public void record(ClientErrorRequestDTO error) {
        String path = ClientErrorRedaction.redact(error.path());
        log.atWarn()
                .addKeyValue("event", "client_error")
                .addKeyValue("client_path", path)
                .addKeyValue("client_source", error.source())
                .addKeyValue("client_release", ClientErrorRedaction.redact(error.release()))
                .addKeyValue("client_user_agent", ClientErrorRedaction.redact(error.userAgent()))
                .addKeyValue("client_stack", ClientErrorRedaction.redact(error.stack()))
                .log("Erro no cliente em {}: {}", path, ClientErrorRedaction.redact(error.message()));
    }

    // The client already drops them (a reset link carries its token in the query); this covers an old or forged one
    static String withoutQuery(String path) {
        int cut = path.length();
        for (char c : new char[]{'?', '#'}) {
            int i = path.indexOf(c);
            if (i >= 0 && i < cut) cut = i;
        }
        return path.substring(0, cut);
    }
}
