package com.ailms.orchestrator.eval;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ailms.orchestrator.eval.UtteranceCsv.Utterance;
import com.ailms.orchestrator.util.TextUtils;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;

/**
 * Validates the P1-B1 labeled utterance set ({@code eval/utterances.csv}).
 *
 * <p>Canonical corpus lives in {@code evaluation/utterances.csv} at repo root; this module
 * references it via symlink {@code orchestrator/src/test/resources/eval/utterances.csv}.
 */
class UtteranceSetTest {

  @Test
  void utteranceSetMeetsB1Requirements() throws Exception {
    List<Utterance> rows = UtteranceCsv.load("/eval/utterances.csv");

    assertTrue(rows.size() >= 200, "expected >=200 utterances, found " + rows.size());

    Set<String> validIntents = Set.of("CONVERSATION", "VIDEO_SEARCH", "CONTENT_ANALYSIS", "ASSESSMENT", "INSIGHT");
    Map<String, Integer> perIntent = new HashMap<>();
    Set<String> seenMessages = new HashSet<>();
    for (Utterance u : rows) {
      assertTrue(validIntents.contains(u.intent()), "line " + u.line() + ": invalid intent '" + u.intent() + "'");
      assertTrue(u.message() != null && !u.message().isBlank(), "line " + u.line() + ": blank message");
      assertTrue(seenMessages.add(u.message().strip().toLowerCase()), "line " + u.line() + ": duplicate message");
      perIntent.merge(u.intent(), 1, Integer::sum);
    }
    for (String intent : validIntents) {
      assertTrue(
          perIntent.getOrDefault(intent, 0) >= 20,
          "intent " + intent + " under-represented: " + perIntent.getOrDefault(intent, 0));
    }

    long greetingShortCircuits =
        rows.stream()
            .filter(u -> u.intent().equals("CONVERSATION") && TextUtils.isBareGreeting(u.message()))
            .count();
    assertTrue(
        greetingShortCircuits >= 5,
        "expected >=5 bare-greeting CONVERSATION rows, found " + greetingShortCircuits);

    long videoLinkShortCircuits =
        rows.stream()
            .filter(
                u ->
                    u.intent().equals("VIDEO_SEARCH")
                        && TestRouter.EXPLICIT_VIDEO_LINK.matcher(u.message()).find())
            .count();
    assertTrue(
        videoLinkShortCircuits >= 5,
        "expected >=5 bare-URL VIDEO_SEARCH rows, found " + videoLinkShortCircuits);

    long adversarialYoutubeAsConversation =
        rows.stream()
            .filter(
                u ->
                    u.intent().equals("CONVERSATION")
                        && u.message().toLowerCase().contains("youtube")
                        && !TestRouter.EXPLICIT_VIDEO_LINK.matcher(u.message()).find())
            .count();
    assertTrue(
        adversarialYoutubeAsConversation >= 1,
        "expected >=1 'youtube'-keyword CONVERSATION adversarial row (e.g. 'Explain what YouTube is')");

    long adversarialVideoTopicAsConversation =
        rows.stream()
            .filter(
                u ->
                    u.intent().equals("CONVERSATION")
                        && u.message().toLowerCase().matches(".*\\bvideo\\b.*")
                        && !TestRouter.EXPLICIT_VIDEO_LINK.matcher(u.message()).find())
            .count();
    assertTrue(
        adversarialVideoTopicAsConversation >= 1,
        "expected >=1 'video'-topic CONVERSATION row (e.g. 'Explain video games')");
  }
}
