package com.motifgen;

import com.motifgen.generator.SentenceGenerator;
import com.motifgen.loader.MotifLoader;
import com.motifgen.model.Motif;
import com.motifgen.model.Sentence;
import com.motifgen.scoring.MidiExporter;
import com.motifgen.scoring.MusicXMLExporter;
import com.motifgen.scoring.SentenceScorer;
import com.motifgen.theory.KeyDetector;

import java.io.File;
import java.util.List;

/**
 * MotifGen - Musical Sentence Generator
 *
 * Loads a 4-bar motif from a MIDI or MusicXML file, detects its key,
 * and generates two 16-bar sentences using music theory techniques.
 * Produces multiple candidates in related keys and selects the best
 * based on consonance, climax placement, rhythm diversity, contour, range, and cadence.
 *
 * Usage: java -jar MotifGen.jar <input.mid|input.xml> [output_directory] [tempo_bpm] [format]
 */
public class MotifGen {

    private static final int MOTIF_BARS = 4;
    private static final int NUM_SENTENCES = 2;
    private static final int DEFAULT_TEMPO = 120;

    public enum OutputFormat { MIDI, MUSICXML, BOTH }

    public static void main(String[] args) {
        if (args.length < 1) {
            printUsage();
            System.exit(1);
        }

        String inputPath = args[0];
        String outputDir = args.length > 1 ? args[1] : ".";
        int tempo = args.length > 2 ? Integer.parseInt(args[2]) : DEFAULT_TEMPO;
        OutputFormat format = args.length > 3 ? parseFormat(args[3]) : OutputFormat.MIDI;

        try {
            run(inputPath, outputDir, tempo, format);
        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
            System.exit(1);
        }
    }

    private static OutputFormat parseFormat(String value) {
        return switch (value.toLowerCase()) {
            case "musicxml", "xml" -> OutputFormat.MUSICXML;
            case "both" -> OutputFormat.BOTH;
            default -> OutputFormat.MIDI;
        };
    }

    public static void run(String inputPath, String outputDir, int tempo) throws Exception {
        run(inputPath, outputDir, tempo, OutputFormat.MIDI);
    }

    public static void run(String inputPath, String outputDir, int tempo, OutputFormat format) throws Exception {
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

        // 3. Generate sentence candidates
        System.out.println("\nGenerating sentence candidates...");
        SentenceGenerator generator = new SentenceGenerator();
        List<Sentence> candidates = generator.generate(motif);
        System.out.println("  Generated " + candidates.size() + " candidates");

        // 4. Score and rank
        System.out.println("\nScoring candidates...");
        SentenceScorer scorer = new SentenceScorer();
        List<Sentence> ranked = scorer.scoreAndRank(candidates);

        System.out.println("\n  All candidates ranked:");
        for (int i = 0; i < ranked.size(); i++) {
            Sentence s = ranked.get(i);
            SentenceScorer.ScoreBreakdown bd = scorer.breakdown(s);
            System.out.printf("  %2d. %-40s  score: %.4f%n", i + 1, s, bd.total());
            System.out.printf("      consonance=%.3f  climax=%.3f  rhythm=%.3f  contour=%.3f  range=%.3f  cadence=%.3f%n",
                    bd.consonance(), bd.climax(), bd.rhythmDiversity(), bd.contour(), bd.range(), bd.cadence());
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
            System.out.printf("    Consonance:      %.3f%n", bd.consonance());
            System.out.printf("    Climax:          %.3f%n", bd.climax());
            System.out.printf("    Rhythm Diversity: %.3f%n", bd.rhythmDiversity());
            System.out.printf("    Contour:         %.3f%n", bd.contour());
            System.out.printf("    Range:           %.3f%n", bd.range());
            System.out.printf("    Cadence:         %.3f%n", bd.cadence());
            System.out.printf("    TOTAL:           %.4f%n", bd.total());

            if (format == OutputFormat.MIDI || format == OutputFormat.BOTH) {
                File midFile = new File(outDir, baseName + ".mid");
                MidiExporter.export(sentence, midFile, tempo);
                System.out.println("    Exported MIDI to: " + midFile.getAbsolutePath());
            }

            if (format == OutputFormat.MUSICXML || format == OutputFormat.BOTH) {
                File xmlFile = new File(outDir, baseName + ".musicxml");
                MusicXMLExporter.export(sentence, xmlFile, tempo);
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

    private static String sanitize(String s) {
        return s.replaceAll("[^a-zA-Z0-9]", "_").replaceAll("_+", "_");
    }

    private static void printUsage() {
        System.out.println("MotifGen - Musical Sentence Generator");
        System.out.println();
        System.out.println("Usage: java -jar MotifGen.jar <input_file> [output_dir] [tempo_bpm] [format]");
        System.out.println();
        System.out.println("  input_file  - MIDI (.mid/.midi) or MusicXML (.xml/.musicxml) file");
        System.out.println("                containing a 4-bar motif");
        System.out.println("  output_dir  - Directory for output files (default: current dir)");
        System.out.println("  tempo_bpm   - Tempo in BPM for output (default: 120)");
        System.out.println("  format      - Output format: midi, musicxml, or both (default: midi)");
        System.out.println();
        System.out.println("The application will:");
        System.out.println("  1. Load the 4-bar motif from the input file");
        System.out.println("  2. Detect the musical key using Krumhansl-Schmuckler algorithm");
        System.out.println("  3. Generate sentence candidates in related keys using:");
        System.out.println("     - Sequence, inversion, retrograde, augmentation");
        System.out.println("     - Embellishment, fragmentation, variation");
        System.out.println("  4. Score each candidate on consonance, climax, rhythm,");
        System.out.println("     contour, range, and cadential strength");
        System.out.println("  5. Output the two best 16-bar sentences as MIDI and/or MusicXML files");
    }
}
