package com.ailms.orchestrator.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class TextUtilsTest {

  @Test
  void recognizesBareGreetings() {
    for (String greeting :
        new String[] {
          "Hi",
          "hi",
          "Hello",
          "hello!",
          "Hey",
          "hey there",
          "Hey there!",
          "hiya",
          "yo",
          "sup",
          "namaste",
          "hola",
          "Good morning",
          "good afternoon",
          "Good evening!",
          "How are you?",
          "How's it going?",
          "How is it going",
          "how are you doing",
          "How are things",
          "hi  there"
        }) {
      assertTrue(TextUtils.isBareGreeting(greeting), "should be a greeting: '" + greeting + "'");
    }
  }

  @Test
  void rejectsNonGreetings() {
    for (String message :
        new String[] {
          "hi what is xyz",
          "Hello, please help me",
          "hey explain neural networks",
          "hi can you explain neural networks",
          "how do you calculate x",
          "Good",
          "",
          "   ",
          null
        }) {
      assertFalse(TextUtils.isBareGreeting(message), "should NOT be a greeting: '" + message + "'");
    }
  }

  @Test
  void recognizesQuestionGenerationRequests() {
    for (String message :
        new String[] {
          "Generate 10 questions on Emacs commands",
          "generate a quiz on recursion",
          "Create 5 questions about closures",
          "Make me a quiz on sorting",
          "Write 3 questions about the uploaded document",
          "Give me some questions on pipes",
          "quiz me",
          "Quiz me on regular expressions"
        }) {
      assertTrue(
          TextUtils.isQuestionGenerationRequest(message),
          "should be a question generation request: '" + message + "'");
    }
  }

  @Test
  void rejectsNonQuestionGenerationRequests() {
    for (String message :
        new String[] {
          "Questions based on the document",
          "What questions should I ask my teacher?",
          "Generate assessment for content doc-1",
          "explain recursion",
          "Find a video about Emacs",
          "Can you make sense of these instructions?",
          "",
          null
        }) {
      assertFalse(
          TextUtils.isQuestionGenerationRequest(message),
          "should NOT be a question generation request: '" + message + "'");
    }
  }

  @Test
  void stripsThinkBlocks() {
    assertEquals(
        "The answer is 42.",
        TextUtils.stripThinking("<think>Let me reason.</think>The answer is 42."));
    assertEquals("Hello", TextUtils.stripThinking(" response<think>hmm</think>Hello"));
    assertEquals("x y", TextUtils.stripThinking("<think>a\nb</think> x  y"));
    assertEquals("", TextUtils.stripThinking("<think></think>"));
  }

  @Test
  void leavesPlainTextUntouched() {
    assertEquals("No thinking here", TextUtils.stripThinking("No thinking here"));
    assertEquals("Hi", TextUtils.stripThinking("Hi"));
  }

  @Test
  void preservesNewlinesWithoutThinkBlock() {
    String markdown = "## Key concepts\n- one\n- two\n\n## Summary\nText.";
    assertEquals(markdown, TextUtils.stripThinking(markdown));
    assertEquals("line1\nline2", TextUtils.stripThinking("line1\nline2"));
  }

  @Test
  void recognizesMaterialPointers() {
    for (String message :
        new String[] {
          "Summarize the document",
          "What does the file say about Calculus?",
          "Review the slide for me",
          "Does the handout mention Thermodynamics?",
          "What does the section say about Linear Algebra?",
          "What does the report say about Machine Learning?",
          "Explain the second page of the report",
          "Compare this with the last chapter",
          "Break down the key concepts in the handout",
          "Give me a summary of the notes"
        }) {
      assertTrue(
          TextUtils.referencesMaterial(message), "should reference material: '" + message + "'");
    }
  }

  @Test
  void rejectsNonMaterialPointers() {
    for (String message :
        new String[] {
          "What are the main takeaways?",
          "Give me the key points",
          "Are the findings consistent?",
          "Summarize recursion",
          "Explain the capital of France",
          "Find a video on Calculus",
          "",
          null
        }) {
      assertFalse(
          TextUtils.referencesMaterial(message), "should NOT reference material: '" + message + "'");
    }
  }

  @Test
  void recognizesSelfReportRequests() {
    for (String message :
        new String[] {
          "Where do I stand in Molecular Biology?",
          "Am I good at Quantum Physics?",
          "Are there gaps in my Psychology knowledge?",
          "What are my weak spots?",
          "What's my level in Data Structures?",
          "Show my achievements",
          "What should I revise?",
          "Recommend what to study",
          "Give me a learning report",
          "What have I accomplished?",
          "Progress report",
          "Feedback on how I'm doing"
        }) {
      assertTrue(
          TextUtils.isSelfReportRequest(message), "should be a self-report: '" + message + "'");
    }
  }

  @Test
  void rejectsNonSelfReportRequests() {
    for (String message :
        new String[] {
          "Test my knowledge on Linear Algebra",
          "Give me a quiz on Organic Chemistry",
          "Give me 10 questions on Data Structures",
          "Evaluate my knowledge of Ethics",
          "Give me a summary of the document",
          "Give me a summary of the report",
          "Find a video on Calculus",
          "Where is the nearest library?",
          "",
          null
        }) {
      assertFalse(
          TextUtils.isSelfReportRequest(message),
          "should NOT be a self-report: '" + message + "'");
    }
  }

  @Test
  void handlesNull() {
    assertNull(TextUtils.stripThinking(null));
  }

  @Test
  void extractsCleanJsonObject() {
    assertEquals(
        "{\"verdict\": \"ACCEPT\"}",
        TextUtils.extractJsonObject("{\"verdict\": \"ACCEPT\"}"));
  }

  @Test
  void extractsJsonObjectAcrossProseAndFences() {
    assertEquals(
        "{\"verdict\": \"ACCEPT\", \"reason\": \"ok\"}",
        TextUtils.extractJsonObject(
            "Sure! Here is my verdict: ```json\n{\"verdict\": \"ACCEPT\", \"reason\": \"ok\"}\n```"));
  }

  @Test
  void extractsJsonObjectWrappedInThinking() {
    assertEquals(
        "{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"missing\"}",
        TextUtils.extractJsonObject(
            "response thinkinghmm{\"verdict\": \"NEEDS_REWRITE\", \"reason\": \"missing\"} trailing"));
  }

  @Test
  void returnsInputWhenNoJsonObject() {
    assertEquals("not json at all", TextUtils.extractJsonObject("not json at all"));
    assertNull(TextUtils.extractJsonObject(null));
  }
}
