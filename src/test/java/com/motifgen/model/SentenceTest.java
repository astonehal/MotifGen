package com.motifgen.model;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class SentenceTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif barOf(int pitch) {
    return new Motif(
        List.of(new Note(pitch, 0, TPB, 90),
                new Note(pitch + 2, TPB, TPB, 90),
                new Note(pitch + 4, TPB * 2, TPB, 90),
                new Note(pitch + 5, TPB * 3, TPB, 90)),
        1, BPB, TPB);
  }

  @Test
  void constructorExposesFields() {
    Motif a = barOf(60);
    Motif b = barOf(62);
    Sentence s = new Sentence(List.of(a, b), "a a'", "C major", 42.5);

    assertEquals(2, s.getPhrases().size());
    assertEquals("a a'", s.getStructure());
    assertEquals("C major", s.getKeyName());
    assertEquals(42.5, s.getScore());
    assertEquals(2, s.totalBars());
  }

  @Test
  void phrasesListIsImmutable() {
    Sentence s = new Sentence(List.of(barOf(60)), "a", "C major", 0);
    assertThrows(UnsupportedOperationException.class, () -> s.getPhrases().add(barOf(62)));
  }

  @Test
  void getAllNotesOffsetsByCumulativePhraseTicks() {
    Motif a = barOf(60);
    Motif b = barOf(62);
    Sentence s = new Sentence(List.of(a, b), "a a'", "C major", 0);

    List<Note> all = s.getAllNotes();
    assertEquals(8, all.size());
    // First phrase starts at 0
    assertEquals(0, all.get(0).startTick());
    // Second phrase shifted by first's totalTicks = 1 bar = 4 * 480
    assertEquals(1920, all.get(4).startTick());
    assertEquals(1920 + TPB, all.get(5).startTick());
  }

  @Test
  void withScoreReturnsNewSentencePreservingPhrasesAndMetadata() {
    Sentence s = new Sentence(List.of(barOf(60)), "a", "C major", 10.0);
    Sentence rescored = s.withScore(99.0);
    assertEquals(99.0, rescored.getScore());
    assertEquals(s.getStructure(), rescored.getStructure());
    assertEquals(s.getKeyName(), rescored.getKeyName());
    assertEquals(s.getPhrases().size(), rescored.getPhrases().size());
  }

  @Test
  void toStringIncludesKeyStructureBarsAndScore() {
    Sentence s = new Sentence(List.of(barOf(60), barOf(62)), "a a'", "G minor", 77.0);
    String str = s.toString();
    assertTrue(str.contains("G minor"));
    assertTrue(str.contains("a a'"));
    assertTrue(str.contains("bars=2"));
    assertTrue(str.contains("77.0"));
  }
}
