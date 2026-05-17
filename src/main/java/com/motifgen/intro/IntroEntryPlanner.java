package com.motifgen.intro;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;

/**
 * Maps sentiment and archetype to a per-instrument entry bar for the variable-length intro.
 *
 * <p>Bar numbers are 1-indexed (bar 1 = first bar of the intro). Rules applied in priority order:
 * <ol>
 *   <li>High arousal ({@code > 0.75}): all instruments enter by bar 2.</li>
 *   <li>Low arousal ({@code <=0.45}): lead enters bar 1, others stagger across bars 2 and 3.</li>
 *   <li>Negative valence ({@code < 0.35}): drums assigned entry bar 1.</li>
 *   <li>Folk/ballad archetype: guitar assigned entry bar 1.</li>
 *   <li>Default (mid-range arousal): lead guitar bar 1, bass bar 2, drums bar 2.</li>
 * </ol>
 *
 * <p>After the deterministic rules, an {@link IntroTemplatePool.EntryTemplate} is drawn and
 * applied, with each template bar clamped to {@link IntroContext#barCount()} so that entries
 * never fall outside the intro.
 */
public final class IntroEntryPlanner {

  /** Instrument key for guitar. */
  public static final String GUITAR = "guitar";
  /** Instrument key for bass. */
  public static final String BASS = "bass";
  /** Instrument key for drums. */
  public static final String DRUMS = "drums";

  private static final double HIGH_AROUSAL = 0.75;
  private static final double LOW_AROUSAL = 0.45;
  private static final double NEGATIVE_VALENCE = 0.35;

  private static final Set<String> FOLK_BALLAD = Set.of("folk", "ballad");

  private IntroEntryPlanner() {}

  /**
   * Computes the entry bar for each instrument given the supplied {@link IntroContext}.
   *
   * @param ctx intro context
   * @return map of instrument name → 1-indexed entry bar (1–barCount)
   */
  public static Map<String, Integer> plan(IntroContext ctx) {
    double arousal = ctx.sentiment().arousal();
    double valence = ctx.sentiment().valence();
    String archetype = ctx.archetype();
    int barCount = ctx.barCount();

    Map<String, Integer> plan = new HashMap<>();

    if (arousal > HIGH_AROUSAL) {
      plan.put(GUITAR, 1);
      plan.put(BASS, Math.min(2, barCount));
      plan.put(DRUMS, 1);
    } else if (arousal <= LOW_AROUSAL) {
      String lead = determineLead(valence, archetype);
      plan.put(lead, 1);
      assignNonLead(plan, lead, Math.min(2, barCount), Math.min(3, barCount));
    } else {
      // Mid-range arousal default.
      String lead = determineLead(valence, archetype);
      plan.put(lead, 1);
      assignNonLead(plan, lead, Math.min(2, barCount), Math.min(2, barCount));
    }

    // Apply EntryTemplate override, clamping each bar to [1, barCount].
    IntroTemplatePool.EntryTemplate template =
        IntroTemplatePool.drawEntry(ctx, new Random());
    plan.put(GUITAR, Math.min(template.guitarBar(), barCount));
    plan.put(BASS,   Math.min(template.bassBar(),   barCount));
    plan.put(DRUMS,  Math.min(template.drumsBar(),  barCount));

    return plan;
  }

  // ---------- private helpers ----------

  private static String determineLead(double valence, String archetype) {
    if (valence < NEGATIVE_VALENCE) {
      return DRUMS;
    }
    if (FOLK_BALLAD.contains(archetype)) {
      return GUITAR;
    }
    return GUITAR;
  }

  private static void assignNonLead(Map<String, Integer> plan, String lead,
      int secondBar, int thirdBar) {
    String[] all = {GUITAR, BASS, DRUMS};
    int barIdx = 0;
    int[] bars = {secondBar, thirdBar};
    for (String inst : all) {
      if (!inst.equals(lead)) {
        plan.put(inst, bars[barIdx++]);
      }
    }
  }
}
