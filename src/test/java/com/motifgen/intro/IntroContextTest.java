package com.motifgen.intro;

import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeySignature;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for {@link IntroContext} — vamp selection, riffScore computation, offsetTicks.
 */
class IntroContextTest {

  private static final KeySignature C_MAJOR = KeySignature.major(0);

  // -----------------------------------------------------------------------
  // Vamp selection
  // -----------------------------------------------------------------------

  @Test
  void highArousalProducesTonicOnlyVamp() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.8, 0.9);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    assertEquals(1, ctx.vampChords().length, "High-arousal vamp should be a single root");
  }

  @Test
  void lowArousalProducesTwoChordVamp() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.4);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad");
    assertEquals(2, ctx.vampChords().length, "Low/mid-arousal vamp should be I-IV (two chords)");
  }

  // -----------------------------------------------------------------------
  // RiffScore computation
  // -----------------------------------------------------------------------

  @Test
  void riffArchetypeHighArousalGivesRiffScore3() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.8);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    assertEquals(3, ctx.riffScore(), "DRIVING archetype with arousal>0.55 should give riffScore=3");
  }

  @Test
  void nonRiffArchetypeGivesRiffScore1() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.8);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "ballad");
    assertEquals(1, ctx.riffScore(), "BALLAD archetype should give riffScore=1");
  }

  @Test
  void riffArchetypeLowArousalGivesRiffScore1() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.6, 0.3);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving");
    assertEquals(1, ctx.riffScore(), "DRIVING with arousal<=0.55 should give riffScore=1");
  }

  @Test
  void funkArchetypeHighArousalGivesRiffScore3() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.7);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "funk");
    assertEquals(3, ctx.riffScore());
  }

  @Test
  void powerArchetypeHighArousalGivesRiffScore3() {
    SentimentProfile sentiment = SentimentProfile.fromVA(0.7, 0.7);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "power");
    assertEquals(3, ctx.riffScore());
  }

  // -----------------------------------------------------------------------
  // offsetTicks
  // -----------------------------------------------------------------------

  @Test
  void offsetTicksIs4BarsAt480Ppq() {
    // Low arousal → barCount=4; offsetTicks = 4 * 4 * 480
    SentimentProfile sentiment = SentimentProfile.fromVA(0.5, 0.2);
    IntroContext ctx = IntroContext.of(sentiment, C_MAJOR, "driving", 480, 4);
    assertEquals(4, ctx.barCount());
    assertEquals(4L * 4 * 480, ctx.offsetTicks());
  }
}
