package com.ailms.orchestrator.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class YouTubeLinkValidatorTest {

  static class StubValidator extends YouTubeLinkValidator {
    final String validId;

    StubValidator(String validId) {
      this.validId = validId;
    }

    @Override
    boolean isValidVideoId(String videoId) {
      return validId.equals(videoId);
    }
  }

  YouTubeLinkValidator validator(String validId) {
    return new StubValidator(validId);
  }

  @Test
  void keepsValidYoutubeLink() {
    String text = "Watch it here: https://www.youtube.com/watch?v=aircAruvnKk thanks!";
    assertEquals(text, validator("aircAruvnKk").sanitize(text));
  }

  @Test
  void keepsValidYoutuBeShortLink() {
    String text = "Here: https://youtu.be/aircAruvnKk\n";
    assertEquals(text, validator("aircAruvnKk").sanitize(text));
  }

  @Test
  void removesInvalidYoutubeLink() {
    String text = "Here: https://www.youtube.com/watch?v=your_video_ more text";
    String out = validator("aircAruvnKk").sanitize(text);
    assertFalse(out.contains("your_video_"));
    assertEquals("Here:  more text", out);
  }

  @Test
  void keepsValidAndRemovesInvalidInSameText() {
    String text =
        "A: https://www.youtube.com/watch?v=aircAruvnKk and B: "
            + "https://www.youtube.com/watch?v=your_video_ done";
    String out = validator("aircAruvnKk").sanitize(text);
    assertTrue(out.contains("aircAruvnKk"));
    assertFalse(out.contains("your_video_"));
  }

  @Test
  void ignoresTextWithoutYoutubeUrls() {
    String text = "No links here, just a http://example.com/page mention.";
    assertEquals(text, validator("aircAruvnKk").sanitize(text));
  }

  @Test
  void leavesNonYoutubeLinksUntouched() {
    String text = "See https://www.example.com/watch?v=notyoutube for details";
    assertEquals(text, validator("aircAruvnKk").sanitize(text));
  }

  @Test
  void returnsNullForNullInput() {
    assertNull(validator("aircAruvnKk").sanitize(null));
  }

  @Test
  void returnsBlankForBlankInput() {
    assertEquals("   ", validator("aircAruvnKk").sanitize("   "));
  }
}
