package com.motifgen.generator;

import com.motifgen.model.Motif;
import com.motifgen.model.Sentence;
import com.motifgen.theory.KeyDetector;
import com.motifgen.theory.KeySignature;
import com.motifgen.transform.MotifTransformer;

import java.util.ArrayList;
import java.util.List;

/**
 * Generates 16-bar musical sentences from a 4-bar motif.
 *
 * Sentence structures used:
 *   - "a a' b a''"  (statement, varied repeat, contrast, return with cadence)
 *   - "a b a' c"    (alternating with two contrasts)
 *   - "a a' a'' b"  (progressive development to climax)
 *   - "a b c a'"    (exploration and return)
 *
 * Each structure is generated in multiple related keys to provide variety.
 */
public class SentenceGenerator {

    private final MotifTransformer transformer;

    public SentenceGenerator(long seed) {
        this.transformer = new MotifTransformer(seed);
    }

    public SentenceGenerator() {
        this.transformer = new MotifTransformer();
    }

    /**
     * Generate multiple sentence candidates from a motif.
     * Produces sentences in the detected key and related keys, using various structures.
     */
    public List<Sentence> generate(Motif motif) {
        KeySignature detectedKey = KeyDetector.bestKey(motif);
        List<KeySignature> keys = detectedKey.relatedKeys();

        System.out.println("  Detected key: " + detectedKey.name());
        System.out.println("  Exploring keys: " + keys.stream().map(KeySignature::name).toList());

        List<Sentence> candidates = new ArrayList<>();

        for (KeySignature key : keys) {
            int transposition = key.root() - detectedKey.root();
            Motif baseMotif = transposition == 0 ? motif : motif.transpose(transposition);

            candidates.add(generateAABAStructure(baseMotif, key));
            candidates.add(generateABACStructure(baseMotif, key));
            candidates.add(generateProgressiveStructure(baseMotif, key));
            candidates.add(generateExploratoryStructure(baseMotif, key));
        }

        return candidates;
    }

    /**
     * a a' b a'' — Classic sentence: statement, variation, contrast, cadential return.
     */
    private Sentence generateAABAStructure(Motif motif, KeySignature key) {
        Motif a = motif;                                         // original statement
        Motif aPrime = transformer.vary(motif, key);             // slight variation
        Motif b = transformer.buildClimax(                        // contrasting phrase (climax)
                transformer.sequence(motif, 3, key), key);
        Motif aDoublePrime = transformer.createCadence(motif, key); // return with cadence

        return new Sentence(List.of(a, aPrime, b, aDoublePrime), "a a' b a''", key.name(), 0);
    }

    /**
     * a b a' c — Alternating structure with two different contrasts.
     */
    private Sentence generateABACStructure(Motif motif, KeySignature key) {
        Motif a = motif;
        Motif b = transformer.sequence(motif, 4, key);           // sequence up a 3rd
        Motif aPrime = transformer.embellish(motif, key);         // embellished return
        Motif c = transformer.createCadence(                      // cadential phrase from inversion
                transformer.invert(motif, avgPitch(motif)), key);

        return new Sentence(List.of(a, b, aPrime, c), "a b a' c", key.name(), 0);
    }

    /**
     * a a' a'' b — Progressive development building to a climax then resolution.
     */
    private Sentence generateProgressiveStructure(Motif motif, KeySignature key) {
        Motif a = motif;
        Motif aPrime = transformer.embellish(motif, key);         // first development
        Motif aDoublePrime = transformer.buildClimax(motif, key); // climax
        Motif b = transformer.createCadence(                      // resolution/cadence
                transformer.vary(motif, key), key);

        return new Sentence(List.of(a, aPrime, aDoublePrime, b), "a a' a'' b", key.name(), 0);
    }

    /**
     * a b c a' — Exploration: statement, two contrasts, then return.
     */
    private Sentence generateExploratoryStructure(Motif motif, KeySignature key) {
        Motif a = motif;
        Motif b = transformer.retrograde(motif);                  // retrograde contrast
        Motif c = transformer.fragment(motif, key);               // fragmentation development
        Motif aPrime = transformer.createCadence(motif, key);     // cadential return

        return new Sentence(List.of(a, b, c, aPrime), "a b c a'", key.name(), 0);
    }

    private int avgPitch(Motif motif) {
        return (int) motif.pitches().stream()
                .mapToInt(Integer::intValue)
                .average()
                .orElse(60);
    }
}
