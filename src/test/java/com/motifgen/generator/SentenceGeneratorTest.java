package com.motifgen.generator;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class SentenceGeneratorTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajor4Bars() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62,
                     60, 62, 64, 67, 65, 64, 62, 60};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void seededGeneratorProducesCandidatesAcrossAllFourStructuresAndAllRelatedKeys() {
    SentenceGenerator gen = new SentenceGenerator(123L);
    List<Sentence> candidates = gen.generate(cMajor4Bars());

    // Each of 5 related keys produces 4 structural variants
    assertEquals(20, candidates.size());

    Set<String> structures = candidates.stream()
        .map(Sentence::getStructure)
        .collect(Collectors.toSet());
    assertEquals(Set.of("a a' b a''", "a b a' c", "a a' a'' b", "a b c a'"), structures);

    // 5 related keys -> 5 distinct key names in total
    Set<String> keys = candidates.stream()
        .map(Sentence::getKeyName)
        .collect(Collectors.toSet());
    assertEquals(5, keys.size());
  }

  @Test
  void everyCandidateHasFourPhrasesAndUnscoredPlaceholder() {
    SentenceGenerator gen = new SentenceGenerator(7L);
    for (Sentence s : gen.generate(cMajor4Bars())) {
      assertEquals(4, s.getPhrases().size());
      assertEquals(0.0, s.getScore());
    }
  }

  @Test
  void defaultConstructorIsUsable() {
    SentenceGenerator gen = new SentenceGenerator();
    List<Sentence> candidates = gen.generate(cMajor4Bars());
    assertFalse(candidates.isEmpty());
  }
}
