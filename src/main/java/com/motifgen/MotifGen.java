package com.motifgen;

import com.motifgen.exporter.MidiExporter;
import com.motifgen.exporter.MusicXMLExporter;
import com.motifgen.generator.SentenceGenerator;
import com.motifgen.guitar.PlayabilityGate;
import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BackingTrackGenerator;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.BassTrackGenerator;
import com.motifgen.guitar.backing.ChordSlot;
import com.motifgen.guitar.backing.DrumGrooveArchetype;
import com.motifgen.guitar.backing.DrumTrack;
import com.motifgen.guitar.backing.DrumTrackGenerator;
import com.motifgen.guitar.backing.HarmonyApproach;
import com.motifgen.theory.KeySignature;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.sentiment.SentimentProfile;
import com.motifgen.theory.KeyDetector;

import java.io.File;
import java.util.List;

/**
 * MotifGen - Musical Sentence Generator
 *
 * Loads a 4-bar motif from a MIDI or MusicXML file, detects its key,
 * and generates two 16-bar sentences using music theory techniques.
 * Produces multiple candidates in related keys and selects the best
 * based on catchiness factors: repetition, contour predictability,
 * pitch range compactness, rhythmic simplicity, internal conventionality,
 * and hook prominence.
 *
 * Usage: java -jar MotifGen.jar <input.mid|input.xml> [output_directory] [tempo_bpm] [format]
 */
public class MotifGen {

    private static final int MOTIF_BARS = 4;
    private static final int NUM_SENTENCES = 2;
    private static final int DEFAULT_TEMPO = 120;

    public enum OutputFormat { MIDI, MUSICXML, BOTH }

    public static void main(String[] args) {
        if (args.length < 1 || args[0].equalsIgnoreCase("--help") || args[0].equalsIgnoreCase("-h")) {
            printUsage();
            System.exit(args.length < 1 ? 1 : 0);
        }

        String inputPath = args[0];
        String outputDir = args.length > 1 ? args[1] : ".";
        int tempo = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TEMPO;
        OutputFormat format = args.length > 3 ? parseFormat(args[3]) : OutputFormat.MIDI;

        // Parse optional sentiment flags: --sentiment LABEL | --valence V --arousal A
        SentimentProfile sentiment = parseSentimentArgs(args);

        try {
            run(inputPath, outputDir, tempo, format, sentiment);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    /**
     * Scans {@code args} for {@code --sentiment LABEL}, {@code --valence V}, and
     * {@code --arousal A} flags (case-insensitive label). Returns {@code null} if
     * none are present (random profile will be used by the generator).
     */
    static SentimentProfile parseSentimentArgs(String[] args) {
        String label = null;
        Double valence = null;
        Double arousal = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i].toLowerCase()) {
                case "--sentiment" -> label   = args[i + 1];
                case "--valence"   -> valence = Double.parseDouble(args[i + 1]);
                case "--arousal"   -> arousal = Double.parseDouble(args[i + 1]);
            }
        }
        if (label != null) {
            return SentimentProfile.fromLabel(label);
        }
        if (valence != null || arousal != null) {
            double v = valence != null ? valence : 0.5;
            double a = arousal != null ? arousal : 0.5;
            return SentimentProfile.fromVA(v, a);
        }
        return null;
    }

    private static OutputFormat parseFormat(String value) {
        return switch (value.toLowerCase()) {
            case "musicxml", "xml" -> OutputFormat.MUSICXML;
            case "both" -> OutputFormat.BOTH;
            default -> OutputFormat.MIDI;
        };
    }

    public static void run(String inputPath, String outputDir, int tempo) throws Exception {
        run(inputPath, outputDir, tempo, OutputFormat.MIDI, null);
    }

    public static void run(String inputPath, String outputDir, int tempo, OutputFormat format) throws Exception {
        run(inputPath, outputDir, tempo, format, null);
    }

    public static void run(String inputPath, String outputDir, int tempo,
            OutputFormat format, SentimentProfile sentiment) throws Exception {
        System.out.println("=== MotifGen - Musical Sentence Generator ===\n");

        // 1. Load the motif
        System.out.println("Loading motif from: " + inputPath);
        Motif motif = MotifLoader.load(inputPath, MOTIF_BARS);
        System.out.println("  Loaded: " + motif);
        System.out.println("  Pitch classes: " + motif.pitchClasses().stream()
                .map(pc -> com.motifgen.theory.KeySignature.noteName(pc))
                .toList());

        // 2. Detect key
        System.out.println("\nAnalyzing key...");
        var topKeys = KeyDetector.topKeys(motif, 5);
        System.out.println("  Top key candidates:");
        for (var kr : topKeys) {
            System.out.printf("    %-15s  correlation: %.4f%n", kr.key().name(), kr.correlation());
        }

        // 3. Resolve / print sentiment
        SentimentProfile profile = sentiment;
        if (profile == null) {
            profile = SentimentProfile.random(new java.util.Random());
        }
        System.out.printf("Sentiment: %s (V=%.2f, A=%.2f)%n",
                profile.closestLabel(), profile.valence(), profile.arousal());

        // 4. Generate sentence candidates
        System.out.println("\nGenerating sentence candidates...");
        SentenceGenerator generator = new SentenceGenerator(System.nanoTime(), new PlayabilityGate());
        List<Sentence> candidates = generator.generate(motif, profile);
        System.out.println("  Generated " + candidates.size() + " candidates");

        // 5. Score and rank
        System.out.println("\nScoring candidates...");
        SentenceScorer scorer = new SentenceScorer();
        List<Sentence> ranked = scorer.scoreAndRank(candidates);

        System.out.println("\n  All candidates ranked:");
        for (int i = 0; i < ranked.size(); i++) {
            Sentence s = ranked.get(i);
            SentenceScorer.ScoreBreakdown bd = scorer.breakdown(s);
            System.out.printf("  %2d. %-40s  score: %.1f  [%s]%n", i + 1, s, bd.total(),
                    SentenceScorer.bandLabel(bd.total()));
            System.out.printf("      repetition=%.3f  contour=%.3f  compactness=%.3f  rhythm=%.3f  conventionality=%.3f  hook=%.3f%n",
                    bd.repetition(), bd.contourPredictability(), bd.pitchRangeCompactness(),
                    bd.rhythmicSimplicity(), bd.internalConventionality(), bd.hookProminence());
        }

        // 5. Select best two sentences
        List<Sentence> best = selectBestTwo(ranked);

        System.out.println("\n=== Selected Sentences ===");

        // 6. Export
        File outDir = new File(outputDir);
        if (!outDir.exists()) outDir.mkdirs();

        for (int i = 0; i < best.size(); i++) {
            Sentence sentence = best.get(i);
            String baseName = "sentence_" + (i + 1) + "_" + sanitize(sentence.getKeyName())
                    + "_" + sanitize(sentence.getStructure());

            System.out.printf("%n  Sentence %d: %s%n", i + 1, sentence);
            SentenceScorer.ScoreBreakdown bd = scorer.breakdown(sentence);
            System.out.printf("    Repetition:              %.3f%n", bd.repetition());
            System.out.printf("    Contour Predictability:  %.3f%n", bd.contourPredictability());
            System.out.printf("    Pitch Range Compactness: %.3f%n", bd.pitchRangeCompactness());
            System.out.printf("    Rhythmic Simplicity:     %.3f%n", bd.rhythmicSimplicity());
            System.out.printf("    Internal Conventionality:%.3f%n", bd.internalConventionality());
            System.out.printf("    Hook Prominence:         %.3f%n", bd.hookProminence());
            System.out.printf("    TOTAL:                   %.1f / 100  [%s]%n",
                    bd.total(), SentenceScorer.bandLabel(bd.total()));

            BackingTrack backing = BackingTrackGenerator.generate(sentence, profile, tempo);
            BassTrack bass = BassTrackGenerator.generate(sentence, profile, tempo);
            DrumTrack drums = generateDrums(sentence, profile, bass);

            if (format == OutputFormat.MIDI || format == OutputFormat.BOTH) {
                File midFile = new File(outDir, baseName + ".mid");
                MidiExporter.export(sentence, backing, bass, drums, midFile, tempo);
                System.out.println("    Exported MIDI to: " + midFile.getAbsolutePath());
            }

            if (format == OutputFormat.MUSICXML || format == OutputFormat.BOTH) {
                File xmlFile = new File(outDir, baseName + ".musicxml");
                MusicXMLExporter.export(sentence, backing, bass, drums, xmlFile, tempo);
                System.out.println("    Exported MusicXML to: " + xmlFile.getAbsolutePath());
            }
        }

        System.out.println("\nDone! Generated " + best.size() + " sentences.");
    }

    /**
     * Select the two best sentences, preferring diversity in structure and key.
     */
    private static List<Sentence> selectBestTwo(List<Sentence> ranked) {
        if (ranked.size() <= NUM_SENTENCES) return ranked;

        Sentence first = ranked.getFirst();
        // For second, pick the highest-scoring one with a different structure OR key
        Sentence second = null;
        for (int i = 1; i < ranked.size(); i++) {
            Sentence candidate = ranked.get(i);
            boolean differentStructure = !candidate.getStructure().equals(first.getStructure());
            boolean differentKey = !candidate.getKeyName().equals(first.getKeyName());
            if (differentStructure || differentKey) {
                second = candidate;
                break;
            }
        }
        if (second == null) second = ranked.get(1);

        return List.of(first, second);
    }

    /**
     * Derives chord slots and produces a drum track aligned with the bass track.
     *
     * @param sentence melody sentence
     * @param profile  sentiment profile (drives archetype selection)
     * @param bass     bass track used for kick-locking
     * @return generated drum track
     */
    private static DrumTrack generateDrums(Sentence sentence, SentimentProfile profile,
            BassTrack bass) {
        int ppq = sentence.getPhrases().isEmpty()
                ? 480
                : sentence.getPhrases().getFirst().getTicksPerBeat();
        long totalTicks = (long) ppq * 4 * sentence.totalBars();
        int numSlots = Math.max(4, sentence.totalBars());
        KeySignature key = KeySignature.major(0);
        try {
            String keyName = sentence.getKeyName();
            if (keyName != null && !keyName.isBlank()) {
                key = keyName.toLowerCase().contains("minor")
                        ? KeySignature.minor(0) : KeySignature.major(0);
            }
        } catch (Exception ignored) {
            // Use default key.
        }
        List<ChordSlot> slots = HarmonyApproach.FUNCTIONAL_DIATONIC.generateChords(
                sentence.getAllNotes(), key, profile, totalTicks, numSlots);
        if (profile == null) {
            return DrumTrackGenerator.generate(
                    sentence, slots, bass, DrumGrooveArchetype.DRIVING);
        }
        return DrumTrackGenerator.generate(sentence, slots, bass, profile);
    }

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }

    private static void printUsage() {
        System.out.println("MotifGen - Musical Sentence Generator");
        System.out.println();
        System.out.println("Usage: java -jar MotifGen.jar <input_file> [output_dir] [tempo_bpm] [format]");
        System.out.println("                              [--sentiment LABEL | --valence V --arousal A]");
        System.out.println();
        System.out.println("  input_file        - MIDI (.mid/.midi) or MusicXML (.xml/.musicxml) file");
        System.out.println("                      containing a 4-bar motif");
        System.out.println("  output_dir        - Directory for output files (default: current dir)");
        System.out.println("  tempo_bpm         - Tempo in BPM for output (default: 120)");
        System.out.println("  format            - Output format: midi, musicxml, or both (default: midi)");
        System.out.println();
        System.out.println("Sentiment options (optional, influence key/structure/rhythm of output):");
        System.out.println("  --sentiment LABEL - Named sentiment; one of:");
        System.out.println("                      HAPPY, EXCITED, RELAXED, CONTENT,");
        System.out.println("                      SAD, GLOOMY, TENSE, ANGRY");
        System.out.println("  --valence V       - Valence in [-1.0, 1.0] (negative=sad, positive=happy)");
        System.out.println("  --arousal A       - Arousal in [-1.0, 1.0] (negative=calm, positive=energetic)");
        System.out.println("                      (if omitted, a random sentiment is chosen)");
        System.out.println();
        System.out.println("The application will:");
        System.out.println("  1. Load the 4-bar motif from the input file");
        System.out.println("  2. Detect the musical key using Krumhansl-Schmuckler algorithm");
        System.out.println("  3. Generate sentence candidates in related keys using:");
        System.out.println("     - Sequence, inversion, retrograde, augmentation");
        System.out.println("     - Embellishment, fragmentation, variation");
        System.out.println("  4. Score each candidate on catchiness (repetition, contour,");
        System.out.println("     compactness, rhythm, conventionality, hook prominence)");
        System.out.println("  5. Output the two best 16-bar sentences as MIDI and/or MusicXML files");
    }
}
