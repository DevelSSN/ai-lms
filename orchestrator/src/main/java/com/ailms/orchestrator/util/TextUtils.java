package com.ailms.orchestrator.util;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public final class TextUtils {

  private static final int MAX_GREETING_LENGTH = 30;

  private static final Pattern BARE_GREETING =
      Pattern.compile(
          "^(?:(?:hi|hiya|hello|heya|hey|yo|sup|namaste|namaskar|hola)(?:\\s+there)?"
              + "|good\\s+(?:morning|afternoon|evening)|how(?:'s| is|"
              + " are)?\\s+(?:it\\s+going|things\\s+going|you\\s+doing|are\\s+you|are\\s+things)"
              + ")[\\s!.,'?]*$",
          Pattern.CASE_INSENSITIVE);

  /**
   * Syntactically rigid video-request surface forms, e.g. "find a video on X", "search for a clip
   * about X". Requires a request frame plus a video noun so messages that merely mention videos
   * (e.g. "Do you watch movies?") do not match. Mirrored in evaluation/compile_rq1.py.
   */
  private static final Pattern EXPLICIT_VIDEO_REQUEST =
      Pattern.compile(
          "(?i)(?:\\b(?:search\\s+for|look\\s+for|find|get\\s+me|recommend|suggest\\s+(?:a|some)|"
              + "link\\s+(?:a|\\w+\\s+)?|show(?:\\s+me)?|i\\s+(?:need|want(?:\\s+to\\s+watch)?)|"
              + "do\\s+you\\s+have|can\\s+you\\s+find|is\\s+there|have\\s+you\\s+got|any)\\b"
              + "(?:.*?)\\b(?:video|videos|clip|clips|tutorial|tutorials|youtube|"
              + "visual\\s+guide|educational\\s+videos?)\\b"
              + "|\\b(?:tutorial\\s+videos?|video\\s+examples?)\\s+(?:about|on|for)\\b)");

  private static final Pattern THINK_BLOCK =
      Pattern.compile("(?is)\\bresponse\\s*<think\\b.*?</think\\s*>|<think\\b.*?</think\\s*>");

  private static final Pattern THINK_REMNANT = Pattern.compile("(?is)</?think\\s*>");

  /**
   * Explicit quiz/question generation asks: a generation verb ("generate", "create", "make"...)
   * within 40 characters of a "quiz"/"question" noun, plus the bare "quiz me" form. Lets a
   * deterministic route beat the classifier, which otherwise tends to answer generation asks
   * with content analysis. Phrasings without a generation verb ("Questions based on the
   * document", "What questions should I ask?") are left to the classifier.
   */
  private static final Pattern QUESTION_GENERATION =
      Pattern.compile(
          "(?i)\\b(?:generate|create|make|write|draft|give|come\\s+up\\s+with)\\b"
              + ".{0,40}\\b(?:quiz|quizzes|questions?)\\b"
              + "|^\\s*quiz\\s+me\\b");

  /**
   * Explicit references to the uploaded material: a material noun ("the document", "this file",
   * "my notes") or a material noun as the subject of a content verb ("the chapter says"). Used by
   * the relevance gate — an explicit pointer keeps CONTENT_ANALYSIS even when retrieval finds no
   * matching chunk, so the agent can state the document does not cover the query instead of the
   * router silently falling back to general conversation. Deliberately does NOT match bare
   * "summarize X" (which may be about any topic), only references to the material itself.
   *
   * <p>Up to two modifier words are tolerated between the determiner and the noun so forms like
   * "the last chapter" or "the second page of the report" still count as a pointer. Deliberately
   * excludes head nouns that denote no document ("the main takeaways", "the key points"): those
   * have no material reference and belong to the retrieval branch of the gate.
   */
  private static final Pattern MATERIAL_POINTER =
      Pattern.compile(
          "(?i)\\b(?:the|this|that|my|your|uploaded|attached|original|provided|above)\\s+"
              + "(?:\\w+\\s+){0,2}?(?:document|file|pdf|paper|chapter|article|slide|slides|"
              + "material|text|book|notes?|section|handout|report|page)"
              + "|\\b(?:document|file|pdf|paper|chapter|article|slide|material|text|section|"
              + "handout|report)s?\\s+"
              + "(?:itself|say|says|said|mention|mentions|contain|contains|cover|covers|"
              + "discuss|discusses|state|states|teach|teaches)"
              + "|\\bsummar(?:y|ize|ise)\\s+(?:it|this|that|these|the\\s+(?:document|file|"
              + "material|paper|chapter|article|text|slides?|notes?|section|handout|report))\\b");

  /**
   * Self-report frames: the learner asks about their own standing, gaps, progress, or what to do
   * next ("Where do I stand in Genetics?", "What are my weak spots?"). Deterministically routed to
   * INSIGHT because the 3B classifier reads a named course topic as the subject and answers the
   * topic rather than the self-report — at temperature 0 it ignored the same frames supplied as
   * few-shot examples, so the surface forms are handled here instead.
   *
   * <p>Paired with {@link #SELF_REPORT_EXCLUDE}, which removes quiz/test asks and "summary of the
   * document" forms that share the action-plus-noun shape but belong to ASSESSMENT and
   * CONTENT_ANALYSIS. Mirrored in evaluation/compile_rq1.py.
   */
  private static final Pattern SELF_REPORT =
      Pattern.compile(
          "(?i)"
              + "\\b(?:what(?:'s| is| are)\\s+my|where\\s+do\\s+i\\s+stand|where\\s+are\\s+my|"
              + "where\\s+do\\s+i\\s+need|how\\s+am\\s+i\\s+doing|"
              + "how\\s+(?:have\\s+i|am\\s+i|can\\s+i|far\\s+along\\s+am\\s+i)|"
              + "am\\s+i\\s+(?:good\\s+at|falling\\s+behind|improving|weak\\s+at))\\b"
              + "|\\bmy\\s+(?:progress|level|knowledge\\s+gaps?|weak\\s+spots?|strengths?|"
              + "learning|achievements?|growth|improvement|completed|recent|study\\s+habits?|"
              + "performance)\\b"
              + "|\\b(?:are\\s+there|what\\s+are\\s+the|which)\\s+gaps?\\b"
              + "|\\b(?:which|what)\\s+topics?\\s+do\\s+i\\s+(?:struggle|need|review|prioritize)\\b"
              + "|\\bwhat\\s+have\\s+i\\s+(?:accomplished|achieved)\\b"
              + "|\\bwhat\\s+(?:should|can|did)\\s+i\\s+(?:study|revise|review|learn|work\\s+on)\\b"
              + "|\\bwhat\\s+do\\s+i\\s+(?:review|study|revise|need|prioritize)\\b"
              + "|\\btell\\s+me\\s+(?:what\\s+(?:i\\s+should|to\\s+study)|about\\s+my)\\b"
              + "|\\bi\\s+need\\s+(?:a\\s+)?(?:learning\\s+)?(?:plan|insights?|feedback)\\b"
              + "|\\brecommend\\s+what\\s+to\\s+(?:study|revise|review|learn)\\b"
              + "|\\bwhat\\s+do\\s+you\\s+recommend(?:\\s+for\\s+me)?\\b"
              + "|\\bany\\s+feedback\\s+for\\s+me\\b"
              + "|\\b(?:show|give|provide|display|summar(?:y|ize)|analyz?e|build|make|plan|"
              + "tell\\s+me)\\b.{0,25}\\b(?:my|me|for\\s+me)\\b.{0,25}\\b(?:report|feedback|"
              + "insights?|progress|performance|achievements?|milestones?|plan|schedule|growth|"
              + "improvement|summary|activity|habits?|recommendations?)\\b"
              + "|\\bprogress\\s+report\\b"
              + "|\\bfeedback\\s+on\\s+how\\s+i'?m\\s+doing\\b"
              + "|\\bplan\\s+my\\b"
              + "|\\bshow\\s+my\\s+completed\\s+topics\\b");

  /** Quiz/test asks and material summaries that share the self-report action shape. */
  private static final Pattern SELF_REPORT_EXCLUDE =
      Pattern.compile(
          "(?i)\\b(?:quiz|quizzes|questions?|test|exam|practice\\s+test|"
              + "(?:test|evaluate|check)\\s+my\\s+knowledge)\\b"
              + "|\\b(?:summary|summar(?:y|ize|ise))\\s+(?:of\\s+)?(?:the|this|these|a)?\\s*"
              + "(?:paper|notes|material|pdf|document|report|section|chapter|article|slides?|"
              + "file|text)\\b");

  private TextUtils() {}

  public static boolean isBareGreeting(String message) {
    if (message == null) return false;
    String trimmed = message.trim();
    if (trimmed.isEmpty() || trimmed.length() > MAX_GREETING_LENGTH) return false;
    return BARE_GREETING.matcher(trimmed).matches();
  }

  public static boolean isExplicitVideoRequest(String message) {
    return message != null && EXPLICIT_VIDEO_REQUEST.matcher(message).find();
  }

  public static boolean isQuestionGenerationRequest(String message) {
    return message != null && QUESTION_GENERATION.matcher(message).find();
  }

  public static boolean referencesMaterial(String message) {
    return message != null && MATERIAL_POINTER.matcher(message).find();
  }

  /**
   * True when the message asks about the learner's own standing rather than about a topic, so it
   * can be routed to INSIGHT without consulting the classifier.
   */
  public static boolean isSelfReportRequest(String message) {
    return message != null
        && SELF_REPORT.matcher(message).find()
        && !SELF_REPORT_EXCLUDE.matcher(message).find();
  }

  public static String stripThinking(String text) {
    if (text == null) return null;
    if (!THINK_BLOCK.matcher(text).find() && !THINK_REMNANT.matcher(text).find()) {
      return text;
    }
    String stripped = THINK_BLOCK.matcher(text).replaceAll(" ");
    stripped = THINK_REMNANT.matcher(stripped).replaceAll(" ");
    return stripped.replaceAll("\\s+", " ").trim();
  }

  public static String extractJsonObject(String text) {
    if (text == null) return null;
    String trimmed = text.trim();
    if (trimmed.startsWith("```")) {
      trimmed = trimmed.replaceFirst("^```[a-zA-Z]*\\s*", "");
      trimmed = trimmed.replaceFirst("```\\s*$", "").trim();
    }
    int start = trimmed.indexOf('{');
    int end = trimmed.lastIndexOf('}');
    if (start >= 0 && end > start) {
      return trimmed.substring(start, end + 1);
    }
    return trimmed;
  }

  /**
   * Returns every balanced JSON object/array slice in {@code text}, in source order. Scanning
   * each start position (rather than slicing from the first {@code [} to the last {@code ]})
   * means prose such as "Based on [1], ..." cannot truncate the real payload. Callers validate
   * candidates, because prose brackets also produce short, non-JSON slices.
   */
  public static List<String> jsonCandidates(String text) {
    if (text == null || text.isBlank()) return List.of();
    List<String> candidates = new ArrayList<>();
    for (int i = 0; i < text.length(); i++) {
      char c = text.charAt(i);
      if (c != '[' && c != '{') continue;
      int end = matchingClose(text, i);
      if (end > i) candidates.add(text.substring(i, end + 1));
    }
    return candidates;
  }

  private static int matchingClose(String text, int start) {
    char open = text.charAt(start);
    char close = open == '[' ? ']' : '}';
    int depth = 0;
    boolean inString = false;
    boolean escaped = false;
    for (int i = start; i < text.length(); i++) {
      char c = text.charAt(i);
      if (inString) {
        if (escaped) {
          escaped = false;
        } else if (c == '\\') {
          escaped = true;
        } else if (c == '"') {
          inString = false;
        }
        continue;
      }
      if (c == '"') {
        inString = true;
      } else if (c == open) {
        depth++;
      } else if (c == close) {
        depth--;
        if (depth == 0) return i;
      }
    }
    return -1;
  }
}
