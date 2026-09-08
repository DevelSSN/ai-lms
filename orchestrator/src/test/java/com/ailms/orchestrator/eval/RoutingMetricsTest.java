package com.ailms.orchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailms.orchestrator.eval.RoutingMetrics.LabelScores;
import com.ailms.orchestrator.eval.RoutingMetrics.Pair;
import com.ailms.orchestrator.eval.RoutingMetrics.Result;
import com.ailms.orchestrator.eval.UtteranceCsv.Utterance;
import com.ailms.orchestrator.util.TextUtils;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * Harness for RQ1 routing precision/recall/F1. Test scope only.
 *
 * <ul>
 *   <li>{@code scorerMath} pins the one-vs-rest formulas on a hand-rolled confusion matrix.</li>
 *   <li>{@code shortCircuitLayer} routes the full corpus through the deterministic prefix with a
 *       stub classifier; asserts short-circuit behavior and prints the metrics table.</li>
 *   <li>{@code livePredictions} scores a live LLM run: {@code -Deval.predictions=<csv>} with header
 *       {@code message,truth,predicted}. Skipped when the property is absent.</li>
 * </ul>
 */
class RoutingMetricsTest {

  @Test
  void scorerMath() {
    List<Pair> pairs =
        List.of(
            new Pair("CONVERSATION", "CONVERSATION"),
            new Pair("CONVERSATION", "CONVERSATION"),
            new Pair("CONVERSATION", "VIDEO_SEARCH"),
            new Pair("VIDEO_SEARCH", "CONVERSATION"),
            new Pair("VIDEO_SEARCH", "VIDEO_SEARCH"));
    Result r = RoutingMetrics.score(pairs);

    LabelScores conv = r.perLabel().get("CONVERSATION");
    assertEquals(2.0 / 3, conv.precision(), 1e-9);
    assertEquals(2.0 / 3, conv.recall(), 1e-9);
    assertEquals(2.0 / 3, conv.f1(), 1e-9);
    assertEquals(3, conv.support());

    LabelScores video = r.perLabel().get("VIDEO_SEARCH");
    assertEquals(0.5, video.precision(), 1e-9);
    assertEquals(0.5, video.recall(), 1e-9);
    assertEquals(0.5, video.f1(), 1e-9);
    assertEquals(2, video.support());

    assertEquals(0.6, r.accuracy(), 1e-9);
    assertEquals(5, r.total());
  }

  @Test
  void shortCircuitLayer() throws Exception {
    List<Utterance> rows = UtteranceCsv.load("/eval/utterances.csv");

    List<RoutingMetrics.Row> report = new ArrayList<>();
    for (Utterance u : rows) {
      String routed = TestRouter.route(u.message(), msg -> "CONVERSATION");
      if (u.intent().equals("CONVERSATION") && TextUtils.isBareGreeting(u.message())) {
        assertEquals("CONVERSATION", routed, "greeting short-circuit broke for: " + u.message());
      }
      if (u.intent().equals("VIDEO_SEARCH")
          && TestRouter.EXPLICIT_VIDEO_LINK.matcher(u.message()).find()) {
        assertEquals("VIDEO_SEARCH", routed, "video-link short-circuit broke for: " + u.message());
      }
      report.add(new RoutingMetrics.Row(u.message(), u.intent(), routed));
    }

    Path dir = Path.of("target", "eval");
    Result r = RoutingMetrics.writeReports(dir, "short-circuit", report);
    System.out.println("Short-circuit layer routing metrics (stub classifier=CONVERSATION):");
    System.out.println(r.formatTable());
    System.out.println("CSV reports written to " + dir.toAbsolutePath());
    assertEquals(rows.size(), r.total());
    assertEquals(RoutingMetrics.LABELS.size(), r.perLabel().size());
  }

  @Test
  void livePredictions() throws Exception {
    String path = System.getProperty("eval.predictions");
    Assumptions.assumeTrue(
        path != null && !path.isBlank(), "set -Deval.predictions=<csv> to score a live LLM run");

    List<Utterance> rows = UtteranceCsv.load("/eval/utterances.csv");
    Map<String, String> truthByMessage = new HashMap<>();
    for (Utterance u : rows) {
      truthByMessage.put(u.message().strip().toLowerCase(), u.intent());
    }

    List<String> lines = Files.readAllLines(Path.of(path), StandardCharsets.UTF_8);
    Assumptions.assumeTrue(!lines.isEmpty(), "empty predictions file: " + path);
    assertEquals("message,truth,predicted", lines.get(0).strip(), "unexpected predictions header");

    List<Pair> pairs = new ArrayList<>();
    List<RoutingMetrics.Row> report = new ArrayList<>();
    for (int i = 1; i < lines.size(); i++) {
      if (lines.get(i).isBlank()) {
        continue;
      }
      List<String> cols = splitCsv(lines.get(i));
      assertEquals(3, cols.size(), "line " + (i + 1) + ": expected 3 columns");
      String message = UtteranceCsv.unquote(cols.get(0).strip());
      String truth = UtteranceCsv.unquote(cols.get(1).strip());
      String predicted = UtteranceCsv.unquote(cols.get(2).strip());
      String corpusTruth = truthByMessage.get(message.strip().toLowerCase());
      assertTrue(corpusTruth != null, "line " + (i + 1) + ": message not in corpus");
      assertEquals(corpusTruth, TestRouter.normalizeIntent(truth), "line " + (i + 1) + ": stale truth");
      pairs.add(new Pair(corpusTruth, predicted));
      report.add(new RoutingMetrics.Row(message, corpusTruth, predicted));
    }

    Path dir = Path.of("target", "eval");
    Result r = RoutingMetrics.writeReports(dir, "live", report);
    System.out.println("Live-run routing metrics (" + pairs.size() + " predictions):");
    System.out.println(r.formatTable());
    System.out.println("CSV reports written to " + dir.toAbsolutePath());
    assertEquals(rows.size(), r.total(), "predictions must cover the full corpus");
  }

  /** Minimal quote-aware CSV splitter (handles "..." with "" escapes). */
  private static List<String> splitCsv(String line) {
    List<String> cols = new ArrayList<>();
    StringBuilder cur = new StringBuilder();
    boolean inQuotes = false;
    for (int i = 0; i < line.length(); i++) {
      char c = line.charAt(i);
      if (c == '"') {
        if (inQuotes && i + 1 < line.length() && line.charAt(i + 1) == '"') {
          cur.append('"');
          i++;
        } else {
          inQuotes = !inQuotes;
          cur.append(c);
        }
      } else if (c == ',' && !inQuotes) {
        cols.add(cur.toString());
        cur.setLength(0);
      } else {
        cur.append(c);
      }
    }
    cols.add(cur.toString());
    return cols;
  }
}
