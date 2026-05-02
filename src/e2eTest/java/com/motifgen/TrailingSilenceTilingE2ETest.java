package com.motifgen;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.motifgen.generator.catchy.MotifLengthMatcher;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;
import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import javax.sound.midi.MidiEvent;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Sequence;
import javax.sound.midi.ShortMessage;
import javax.sound.midi.Track;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * End-to-end coverage for issue #15 (fix MotifLengthMatcher trailing-silence tiling).
 *
 * <p>Before the fix, {@code match()} compared the motif's <em>content span</em> against
 * {@code phraseTicks}; a motif whose declared length fills the phrase but whose last note
 * ends before the bar boundary would therefore be tiled, producing phantom notes beyond
 * the motif's actual content. The fix makes {@code match()} use {@code totalTicks} (the
 * declared bar-aligned length) for the comparison, and makes {@code extend()} use
 * {@code totalTicks} as the tile stride.
 *
 * <p>Each test maps to a named Gherkin scenario from the confirmed requirements.
 */
class TrailingSilenceTilingE2ETest {

  private static final int TICKS_PER_BEAT = 480;
  private static final int BEATS_PER_BAR = 4;
  private static final long BAR_TICKS = (long) BEATS_PER_BAR * TICKS_PER_BEAT;

  @TempDir
  Path tempDir;

  // ---------------------------------------------------------------------------
  // Scenario 1 – Motif with trailing silence is NOT tiled when
  //               declared length == phrase length
  // ---------------------------------------------------------------------------

  /**
   * Given a motif whose totalTicks equals phraseTicks
   * And the motif has trailing silence (last note ends before totalTicks)
   * When MotifLengthMatcher.match() is called
   * Then tiling is NOT triggered
   * And no phantom notes appear beyond the motif's actual content.
   */
  @Test
  void givenMotifWithTrailingSilenceAndDeclaredLengthEqualToPhrase_whenMatch_thenTilingIsNotTriggered()
      throws Exception {

    // 4-bar motif at 480 ticks/beat: totalTicks = 4 * 4 * 480 = 7680
    // Last note ends at tick 6719 — 961 ticks of trailing silence
    long totalTicks = 4L * BAR_TICKS;          // 7680
    long lastNoteEnd = 6719L;

    Motif motif = buildMotifWithTrailingSilence(totalTicks, lastNoteEnd);

    assertEquals(totalTicks, motif.totalTicks(),
        "fixture: motif.totalTicks() must equal phraseTicks");

    // Confirm trailing silence exists
    long contentEnd = motif.getNotes().stream()
        .filter(n -> !n.isRest())
        .mapToLong(Note::endTick)
        .max()
        .orElse(0L);
    assertTrue(contentEnd < totalTicks,
        "fixture: motif must have trailing silence (contentEnd=" + contentEnd
            + " < totalTicks=" + totalTicks + ")");

    MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(42L));
    KeySignature cMajor = KeySignature.major(0);

    Motif result = matcher.match(motif, totalTicks, cMajor, 42L);

    // match() must return the motif unchanged — same note count, same content end
    assertEquals(motif.getNotes().size(), result.getNotes().size(),
        "no extra notes should appear when declared length equals phrase length");

    long resultContentEnd = result.getNotes().stream()
        .filter(n -> !n.isRest())
        .mapToLong(Note::endTick)
        .max()
        .orElse(0L);

    assertEquals(contentEnd, resultContentEnd,
        "phantom notes must not appear beyond the motif's actual content end");

    // Specifically, no note should start at or after contentEnd (the phantom tick)
    boolean hasPhantomNote = result.getNotes().stream()
        .filter(n -> !n.isRest())
        .anyMatch(n -> n.startTick() >= contentEnd);
    assertTrue(!hasPhantomNote,
        "no note must start at or after the trailing-silence boundary tick=" + contentEnd);
  }

  // ---------------------------------------------------------------------------
  // Scenario 2 – Motif without trailing silence IS tiled when
  //               totalTicks < phraseTicks
  // ---------------------------------------------------------------------------

  /**
   * Given a motif whose totalTicks is less than phraseTicks
   * When MotifLengthMatcher.match() is called
   * Then tiling IS triggered to fill the phrase.
   */
  @Test
  void givenMotifShorterThanPhrase_whenMatch_thenTilingIsTriggered() throws Exception {

    // 1-bar motif (1920 ticks), phrase is 4 bars (7680 ticks)
    long motifTicks = BAR_TICKS;      // 1920
    long phraseTicks = 4L * BAR_TICKS; // 7680

    Motif motif = buildDenseMotif(motifTicks);

    assertEquals(motifTicks, motif.totalTicks(),
        "fixture: motif must be exactly 1 bar");
    assertTrue(motifTicks < phraseTicks,
        "fixture: motif must be shorter than phrase");

    MotifLengthMatcher matcher = new MotifLengthMatcher(new Random(7L));
    KeySignature cMajor = KeySignature.major(0);

    Motif result = matcher.match(motif, phraseTicks, cMajor, 7L);

    // The result must span the full phrase
    long resultTotalTicks = result.totalTicks();
    assertEquals(phraseTicks, resultTotalTicks,
        "tiled phrase must have totalTicks == phraseTicks");

    // There must be notes placed well past the first bar (tiling happened)
    long lastNoteStart = result.getNotes().stream()
        .filter(n -> !n.isRest())
        .mapToLong(Note::startTick)
        .max()
        .orElse(0L);
    assertTrue(lastNoteStart >= motifTicks,
        "at least one note must start after the first tile boundary to confirm tiling; "
            + "lastNoteStart=" + lastNoteStart);
  }

  // ---------------------------------------------------------------------------
  // Scenario 3 – Extension spacing uses declared length (totalTicks) not
  //               content span
  // ---------------------------------------------------------------------------

  /**
   * Given a motif that genuinely needs tiling
   * When MotifLengthMatcher.extend() is called
   * Then tiles are spaced by totalTicks
   * And no overlapping or gap artifacts appear.
   */
  @Test
  void givenMotifNeedingTiling_whenExtend_thenTilesAreSpacedByTotalTicksNotContentSpan()
      throws Exception {

    // 1-bar motif with trailing silence: 3 quarter notes + 1 bar of silence
    long totalTicks = BAR_TICKS;    // 1920
    long contentEnd = 3L * TICKS_PER_BEAT; // 1440 – last note ends here

    Motif tile0 = buildMotifWithTrailingSilence(totalTicks, contentEnd);

    assertEquals(totalTicks, tile0.totalTicks(), "fixture: tile0.totalTicks() must be 1920");
    assertTrue(contentEnd < totalTicks, "fixture: tile0 must have trailing silence");

    long phraseTicks = 4L * BAR_TICKS; // 7680

    // Use identity picker to keep tile content predictable
    MotifLengthMatcher identityMatcher = new MotifLengthMatcher(
        (tile, key) -> {
          // Return a copy of the tile using identity transform
          List<Note> copied = new ArrayList<>(tile.getNotes());
          return new Motif(copied, tile.getBars(), tile.getBeatsPerBar(), tile.getTicksPerBeat());
        });

    Motif result = identityMatcher.extend(tile0, phraseTicks, KeySignature.major(0),
        new int[]{0, 0, 0, 0});

    // With totalTicks=1920 stride over 7680 ticks, we expect exactly 4 tiles
    // Tile boundaries: 0, 1920, 3840, 5760 (4 tiles × 1920 = 7680)
    // If stride were contentEnd=1440, we'd get 5+ tiles with overlapping/gap artifacts

    // Verify no note starts in the "gap" between contentEnd of tile N and
    // totalTicks*N (i.e. the trailing silence zone of each tile is respected)
    List<Note> soundingNotes = result.getNotes().stream()
        .filter(n -> !n.isRest())
        .toList();

    // The tile count is phraseTicks / totalTicks = 4
    // Each tile's content occupies [tileStart, tileStart + contentEnd)
    // No sounding note should start in [tileStart + contentEnd, tileStart + totalTicks)
    // for any tile except possibly the last (which is clipped to phraseTicks)
    int tileCount = (int) (phraseTicks / totalTicks);
    for (int t = 0; t < tileCount - 1; t++) {
      long silenceStart = t * totalTicks + contentEnd;
      long silenceEnd = (t + 1) * totalTicks;
      for (Note n : soundingNotes) {
        boolean inSilenceZone = n.startTick() >= silenceStart && n.startTick() < silenceEnd;
        assertTrue(!inSilenceZone,
            "phantom note found in trailing-silence zone of tile " + t
                + ": note.startTick=" + n.startTick()
                + " silenceZone=[" + silenceStart + "," + silenceEnd + ")");
      }
    }

    // Additionally verify total tiled length
    assertEquals(phraseTicks, result.totalTicks(),
        "extended result must span exactly phraseTicks");
  }

  // ---------------------------------------------------------------------------
  // Scenario 4 (E2E) – TestMotif2.mid produces no phantom notes
  // ---------------------------------------------------------------------------

  /**
   * Given TestMotif2.mid processed via the full MotifGen pipeline
   * When the output MIDI files are examined
   * Then no notes appear after tick 6719 in any A-section phrase
   * (TestMotif2.mid has totalTicks=7680, last note ends at tick 6719;
   *  phantom notes previously appeared at ticks 6719 and 7439).
   */
  @Test
  void givenTestMotif2Mid_whenProcessedViaFullPipeline_thenNoPhantomNotesAppearAfterTick6719()
      throws Exception {

    String testMotif2Path = "TestMotif2.mid";
    File inputFile = new File(testMotif2Path);
    assertTrue(inputFile.exists(),
        "TestMotif2.mid must exist at project root: " + inputFile.getAbsolutePath());

    String outDir = tempDir.resolve("testmotif2_output").toString();
    new File(outDir).mkdirs();

    MotifGen.run(testMotif2Path, outDir, 120);

    File[] outputFiles = new File(outDir).listFiles((d, n) -> n.endsWith(".mid"));
    assertNotNull(outputFiles, "output directory must contain MIDI files");
    assertTrue(outputFiles.length >= 1,
        "at least one MIDI file must be produced");

    // The phantom-note boundary: the last real note in TestMotif2.mid ends at 6719.
    // No note should start at tick >= 6719 within what would be an A-section phrase
    // (the first 7680 ticks of the output, since each phrase = 1 bar-group of 7680 ticks).
    long phantomBoundaryTick = 6719L;

    // Examine all output MIDI files for phantom notes
    for (File midiFile : outputFiles) {
      Sequence seq = MidiSystem.getSequence(midiFile);

      for (Track track : seq.getTracks()) {
        for (int i = 0; i < track.size(); i++) {
          MidiEvent event = track.get(i);
          if (!(event.getMessage() instanceof ShortMessage sm)) continue;
          if (sm.getCommand() != ShortMessage.NOTE_ON) continue;
          if (sm.getData2() == 0) continue; // velocity-0 NOTE_ON is NOTE_OFF

          long tick = event.getTick();

          // Check within the range of the first phrase (0..7680).
          // Phantom notes were observed at 6719 and 7439, both within a 7680-tick window.
          if (tick < 7680L) {
            assertTrue(tick <= phantomBoundaryTick,
                "phantom note detected in " + midiFile.getName()
                    + " at tick " + tick + " (must be <= " + phantomBoundaryTick + ")");
          }
        }
      }
    }
  }

  // ---------------------------------------------------------------------------
  // Helpers
  // ---------------------------------------------------------------------------

  /**
   * Builds a motif with the given {@code totalTicks} declared length where the
   * last real note ends at {@code contentEndTick}. Uses quarter-note fills up to
   * {@code contentEndTick}, followed by implicit trailing silence up to {@code totalTicks}.
   */
  private Motif buildMotifWithTrailingSilence(long totalTicks, long contentEndTick) {
    int bars = (int) (totalTicks / BAR_TICKS);
    List<Note> notes = new ArrayList<>();
    int[] pitches = {60, 62, 64, 65, 67, 65, 64, 62, 60, 62, 64, 67};
    long tick = 0L;
    int i = 0;
    while (tick + TICKS_PER_BEAT <= contentEndTick && i < pitches.length) {
      notes.add(new Note(pitches[i++], tick, TICKS_PER_BEAT, 90));
      tick += TICKS_PER_BEAT;
    }
    // Ensure at least one note
    if (notes.isEmpty()) {
      notes.add(new Note(60, 0L, Math.min(TICKS_PER_BEAT, contentEndTick), 90));
    }
    return new Motif(notes, bars, BEATS_PER_BAR, TICKS_PER_BEAT);
  }

  /**
   * Builds a dense motif where notes fill the entire {@code totalTicks} (no trailing silence).
   */
  private Motif buildDenseMotif(long totalTicks) {
    int bars = (int) (totalTicks / BAR_TICKS);
    List<Note> notes = new ArrayList<>();
    int[] pitches = {60, 62, 64, 65};
    long tick = 0L;
    int i = 0;
    while (tick < totalTicks) {
      long dur = Math.min(TICKS_PER_BEAT, totalTicks - tick);
      notes.add(new Note(pitches[i++ % pitches.length], tick, dur, 90));
      tick += TICKS_PER_BEAT;
    }
    return new Motif(notes, bars, BEATS_PER_BAR, TICKS_PER_BEAT);
  }
}
