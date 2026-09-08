package com.ailms.orchestrator.eval;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * One-vs-rest precision/recall/F1 scorer over (truth, predicted) intent pairs. Test scope only.
 * Zero-division yields 0.0.
 */
final class RoutingMetrics {

  static final List<String> LABELS =
      List.of("CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT");

  record Pair(String truth, String predicted) {}

  record Row(String message, String truth, String predicted) {}

  record LabelScores(double precision, double recall, double f1, int support) {}

  record Result(
      Map<String, LabelScores> perLabel,
      double accuracy,
      double macroPrecision,
      double macroRecall,
      double macroF1,
      int total,
      Map<String, Map<String, Integer>> confusion) {

    String formatTable() {
      StringBuilder sb = new StringBuilder();
      sb.append(String.format("%-16s %7s %10s %10s %10s%n", "intent", "support", "precision", "recall", "f1"));
      for (String label : LABELS) {
        LabelScores s = perLabel.get(label);
        sb.append(
            String.format(
                "%-16s %7d %10.4f %10.4f %10.4f%n",
                label, s.support(), s.precision(), s.recall(), s.f1()));
      }
      sb.append(String.format("accuracy=% .4f macro-P=% .4f macro-R=% .4f macro-F1=% .4f n=%d%n",
          accuracy, macroPrecision, macroRecall, macroF1, total));
      return sb.toString();
    }
  }

  private RoutingMetrics() {}

  /**
   * Scores rows and writes three CSVs into {@code dir}: {@code <prefix>-metrics.csv} (per-intent
   * support/precision/recall/f1), {@code <prefix>-summary.csv} (accuracy, macro averages, n), and
   * {@code <prefix>-predictions.csv} (message,truth,predicted audit trail). Returns the result.
   */
  static Result writeReports(Path dir, String prefix, List<Row> rows) throws IOException {
    List<Pair> pairs = rows.stream().map(r -> new Pair(r.truth(), r.predicted())).toList();
    Result result = score(pairs);
    Files.createDirectories(dir);

    try (BufferedWriter w =
        Files.newBufferedWriter(dir.resolve(prefix + "-metrics.csv"), StandardCharsets.UTF_8)) {
      w.write("intent,support,precision,recall,f1\n");
      for (String label : LABELS) {
        LabelScores s = result.perLabel().get(label);
        w.write(
            String.format(
                Locale.ROOT,
                "%s,%d,%.6f,%.6f,%.6f%n",
                label, s.support(), s.precision(), s.recall(), s.f1()));
      }
    }

    try (BufferedWriter w =
        Files.newBufferedWriter(dir.resolve(prefix + "-summary.csv"), StandardCharsets.UTF_8)) {
      w.write("metric,value\n");
      w.write(String.format(Locale.ROOT, "accuracy,%.6f%n", result.accuracy()));
      w.write(String.format(Locale.ROOT, "macro_precision,%.6f%n", result.macroPrecision()));
      w.write(String.format(Locale.ROOT, "macro_recall,%.6f%n", result.macroRecall()));
      w.write(String.format(Locale.ROOT, "macro_f1,%.6f%n", result.macroF1()));
      w.write(String.format(Locale.ROOT, "n,%d%n", result.total()));
    }

    try (BufferedWriter w =
        Files.newBufferedWriter(
            dir.resolve(prefix + "-predictions.csv"), StandardCharsets.UTF_8)) {
      w.write("message,truth,predicted\n");
      for (Row r : rows) {
        w.write(
            escape(r.message())
                + ","
                + TestRouter.normalizeIntent(r.truth())
                + ","
                + TestRouter.normalizeIntent(r.predicted())
                + "\n");
      }
    }
    return result;
  }

  private static String escape(String value) {
    if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
      return "\"" + value.replace("\"", "\"\"") + "\"";
    }
    return value;
  }

  static Result score(List<Pair> pairs) {
    Map<String, Map<String, Integer>> confusion = new LinkedHashMap<>();
    for (String truth : LABELS) {
      Map<String, Integer> row = new LinkedHashMap<>();
      for (String predicted : LABELS) {
        row.put(predicted, 0);
      }
      confusion.put(truth, row);
    }
    int correct = 0;
    for (Pair p : pairs) {
      String truth = TestRouter.normalizeIntent(p.truth());
      String predicted = TestRouter.normalizeIntent(p.predicted());
      confusion.get(truth).merge(predicted, 1, Integer::sum);
      if (truth.equals(predicted)) {
        correct++;
      }
    }
    Map<String, LabelScores> perLabel = new LinkedHashMap<>();
    double sumP = 0;
    double sumR = 0;
    double sumF = 0;
    for (String label : LABELS) {
      int tp = confusion.get(label).get(label);
      int fp = 0;
      int fn = 0;
      for (String truth : LABELS) {
        if (!truth.equals(label)) {
          fp += confusion.get(truth).get(label);
        }
      }
      for (String predicted : LABELS) {
        if (!predicted.equals(label)) {
          fn += confusion.get(label).get(predicted);
        }
      }
      int support = tp + fn;
      double precision = tp + fp == 0 ? 0.0 : (double) tp / (tp + fp);
      double recall = tp + fn == 0 ? 0.0 : (double) tp / (tp + fn);
      double f1 =
          precision + recall == 0 ? 0.0 : 2 * precision * recall / (precision + recall);
      perLabel.put(label, new LabelScores(precision, recall, f1, support));
      sumP += precision;
      sumR += recall;
      sumF += f1;
    }
    int n = pairs.size();
    return new Result(
        perLabel,
        n == 0 ? 0.0 : (double) correct / n,
        sumP / LABELS.size(),
        sumR / LABELS.size(),
        sumF / LABELS.size(),
        n,
        confusion);
  }
}
