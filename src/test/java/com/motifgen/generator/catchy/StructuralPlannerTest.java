package com.motifgen.generator.catchy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class StructuralPlannerTest {

  private static final int TPB = 480;
  private static final int BPB = 4;

  private Motif cMajorMotif() {
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62};
    List<Note> notes = new ArrayList<>();
    long tick = 0;
    for (int p : pitches) {
      notes.add(new Note(p, tick, TPB, 90));
      tick += TPB;
    }
    return new Motif(notes, 4, BPB, TPB);
  }

  @Test
  void planCapturesTemplatePhraseBarsAndTotalBars() {
    StructuralPlanner planner = new StructuralPlanner();
    StructuralPlan plan = planner.plan(cMajorMotif(), "AABA", KeySignature.major(0));

    assertEquals("AABA", plan.template());
    assertEquals(4, plan.phraseBars());
    assertEquals(16, plan.totalBars());
  }

  @Test
  void climaxPositionFallsInsideTotalNotes() {
    StructuralPlanner planner = new StructuralPlanner();
    StructuralPlan plan = planner.plan(cMajorMotif(), "AABA", KeySignature.major(0));

    int totalNotes = plan.totalNotes();
    assertTrue(totalNotes > 0, "plan must expose total notes");
    assertTrue(plan.climaxPosition() >= 0 && plan.climaxPosition() < totalNotes,
        "climax index must be inside [0, totalNotes)");
  }

  @Test
  void tonalCenterMatchesKeyRoot() {
    StructuralPlanner planner = new StructuralPlanner();
    KeySignature key = KeySignature.major(5); // F major, root pc = 5
    StructuralPlan plan = planner.plan(cMajorMotif(), "ABAC", key);

    assertEquals(5, plan.tonalCenterPc());
  }

  @Test
  void supportsAllFourTemplates() {
    StructuralPlanner planner = new StructuralPlanner();
    for (String template : List.of("AABA", "ABAB", "ABAC", "ABCA")) {
      StructuralPlan plan = planner.plan(cMajorMotif(), template, KeySignature.major(0));
      assertEquals(template, plan.template());
      assertEquals(4, plan.sectionCount());
    }
  }

  @Test
  void climaxPlacedInLatterHalfForFrontLoadedMotif() {
    StructuralPlanner planner = new StructuralPlanner();
    StructuralPlan plan = planner.plan(cMajorMotif(), "AABA", KeySignature.major(0));

    // Classic hook-driven form: climax should be roughly 60% of the way through
    int half = plan.totalNotes() / 2;
    assertTrue(plan.climaxPosition() >= half,
        "climax should fall in the back half of the melody for hook prominence");
  }

  @Test
  void rejectsUnknownTemplate() {
    StructuralPlanner planner = new StructuralPlanner();
    assertThrows(IllegalArgumentException.class,
        () -> planner.plan(cMajorMotif(), "XYZW", KeySignature.major(0)));
  }
}
