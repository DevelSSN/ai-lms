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
  void handlesNull() {
    assertNull(TextUtils.stripThinking(null));
  }
}
