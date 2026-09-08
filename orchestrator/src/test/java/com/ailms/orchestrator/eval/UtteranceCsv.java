package com.ailms.orchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Shared loader for the P1-B1 labeled utterance set. Test scope only. */
final class UtteranceCsv {

  record Utterance(String intent, String message, int line) {}

  private UtteranceCsv() {}

  static List<Utterance> load(String resource) throws Exception {
    InputStream in = UtteranceCsv.class.getResourceAsStream(resource);
    assertNotNull(in, "missing test resource: " + resource + " (check eval symlink)");
    List<Utterance> rows = new ArrayList<>();
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
      String header = br.readLine();
      assertEquals("intent,message", header == null ? null : header.strip(), "unexpected CSV header");
      String line;
      int lineNo = 1;
      while ((line = br.readLine()) != null) {
        lineNo++;
        if (line.isBlank()) {
          continue;
        }
        int comma = line.indexOf(',');
        assertTrue(comma > 0, "line " + lineNo + ": missing comma separator");
        String intent = line.substring(0, comma).strip();
        String message = unquote(line.substring(comma + 1).strip());
        rows.add(new Utterance(intent, message, lineNo));
      }
    }
    return rows;
  }

  static String unquote(String value) {
    if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
      return value.substring(1, value.length() - 1).replace("\"\"", "\"");
    }
    return value;
  }
}
