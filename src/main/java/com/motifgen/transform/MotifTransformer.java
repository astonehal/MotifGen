package com.motifgen.transform;

import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.theory.KeySignature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Applies music-theory-based transformations to motifs to generate sentence phrases.
 * Techniques: repetition, sequence, inversion, retrograde, augmentation,
 * diminution, extension, embellishment, fragmentation.
 */
public class MotifTransformer {

    private final Random random;

    public MotifTransformer(long seed) {
        this.random = new Random(seed);
    }

    public MotifTransformer() {
        this(System.nanoTime());
    }

    /** Exact repetition of the motif. */
    public Motif repeat(Motif motif) {
        return motif;
    }

    /** Tonal sequence: transpose diatonically by a given scale-degree offset. */
    public Motif sequence(Motif motif, int semitones, KeySignature key) {
        List<Note> transposed = new ArrayList<>();
        for (Note n : motif.getNotes()) {
            if (n.isRest()) {
                transposed.add(n);
            } else {
                int newPitch = transposeInKey(n.pitch(), semitones, key);
                transposed.add(new Note(newPitch, n.startTick(), n.durationTicks(), n.velocity()));
            }
        }
        return new Motif(transposed, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Melodic inversion: mirror intervals around a pivot pitch. */
    public Motif invert(Motif motif, int pivotPitch) {
        List<Note> inverted = new ArrayList<>();
        for (Note n : motif.getNotes()) {
            if (n.isRest()) {
                inverted.add(n);
            } else {
                int interval = n.pitch() - pivotPitch;
                int newPitch = Math.max(0, Math.min(127, pivotPitch - interval));
                inverted.add(new Note(newPitch, n.startTick(), n.durationTicks(), n.velocity()));
            }
        }
        return new Motif(inverted, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Retrograde: reverse the order of notes while keeping rhythmic positions. */
    public Motif retrograde(Motif motif) {
        List<Note> notes = motif.getNotes();
        if (notes.isEmpty()) return motif;

        List<Integer> pitches = new ArrayList<>(notes.stream().map(Note::pitch).toList());
        Collections.reverse(pitches);

        List<Note> retro = new ArrayList<>();
        for (int i = 0; i < notes.size(); i++) {
            Note original = notes.get(i);
            retro.add(new Note(pitches.get(i), original.startTick(), original.durationTicks(), original.velocity()));
        }
        return new Motif(retro, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Augmentation: double all note durations (stretches the motif rhythmically). */
    public Motif augment(Motif motif) {
        List<Note> augmented = motif.getNotes().stream()
                .map(n -> new Note(n.pitch(), n.startTick() * 2, n.durationTicks() * 2, n.velocity()))
                .toList();
        return new Motif(augmented, motif.getBars() * 2, motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Diminution: halve all note durations (compresses the motif). */
    public Motif diminish(Motif motif) {
        List<Note> diminished = motif.getNotes().stream()
                .map(n -> new Note(n.pitch(), n.startTick() / 2, Math.max(1, n.durationTicks() / 2), n.velocity()))
                .toList();
        return new Motif(diminished, Math.max(1, motif.getBars() / 2), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Extension: add passing tones and neighbor tones to embellish the motif. */
    public Motif embellish(Motif motif, KeySignature key) {
        List<Note> embellished = new ArrayList<>();
        List<Note> notes = motif.getNotes();

        for (int i = 0; i < notes.size(); i++) {
            Note n = notes.get(i);
            if (n.isRest()) {
                embellished.add(n);
                continue;
            }

            // Occasionally add a neighbor tone before the note
            if (n.durationTicks() > motif.getTicksPerBeat() / 2 && random.nextDouble() < 0.3) {
                long halfDur = n.durationTicks() / 2;
                int neighborPitch = getNeighborTone(n.pitch(), key);
                embellished.add(new Note(neighborPitch, n.startTick(), halfDur, (int)(n.velocity() * 0.8)));
                embellished.add(new Note(n.pitch(), n.startTick() + halfDur, n.durationTicks() - halfDur, n.velocity()));
            } else {
                embellished.add(n);
            }
        }
        return new Motif(embellished, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Fragmentation: take only the first portion of the motif and repeat it with variation. */
    public Motif fragment(Motif motif, KeySignature key) {
        List<Note> notes = motif.getNotes();
        int fragmentSize = Math.max(1, notes.size() / 2);
        List<Note> fragment = new ArrayList<>(notes.subList(0, fragmentSize));

        long fragmentEnd = fragment.isEmpty() ? 0 : fragment.getLast().endTick();
        long totalTicks = motif.totalTicks();

        // Repeat fragment transposed to fill remaining space
        List<Note> result = new ArrayList<>(fragment);
        int step = random.nextBoolean() ? 2 : -2;
        while (fragmentEnd < totalTicks && !fragment.isEmpty()) {
            for (Note n : fragment) {
                long newStart = n.startTick() + fragmentEnd;
                if (newStart >= totalTicks) break;
                int newPitch = transposeInKey(n.pitch(), step, key);
                long dur = Math.min(n.durationTicks(), totalTicks - newStart);
                result.add(new Note(newPitch, newStart, dur, n.velocity()));
            }
            fragmentEnd += fragment.getLast().endTick();
            step += random.nextBoolean() ? 1 : -1;
        }

        return new Motif(result, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Variation: combine small pitch modifications with rhythmic alteration. */
    public Motif vary(Motif motif, KeySignature key) {
        List<Note> varied = new ArrayList<>();
        for (Note n : motif.getNotes()) {
            if (n.isRest()) {
                varied.add(n);
                continue;
            }
            // Occasionally alter pitch by a scale step
            int newPitch = n.pitch();
            if (random.nextDouble() < 0.25) {
                int step = random.nextBoolean() ? 1 : -1;
                newPitch = transposeInKey(n.pitch(), step, key);
            }
            // Occasionally alter velocity for dynamic variation
            int newVel = n.velocity();
            if (random.nextDouble() < 0.2) {
                newVel = Math.max(30, Math.min(127, newVel + random.nextInt(21) - 10));
            }
            varied.add(new Note(newPitch, n.startTick(), n.durationTicks(), newVel));
        }
        return new Motif(varied, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Build a climax version: transpose up and increase velocity. */
    public Motif buildClimax(Motif motif, KeySignature key) {
        List<Note> climax = new ArrayList<>();
        for (Note n : motif.getNotes()) {
            if (n.isRest()) {
                climax.add(n);
                continue;
            }
            int newPitch = transposeInKey(n.pitch(), 4, key); // up a 3rd
            int newVel = Math.min(127, n.velocity() + 15);
            climax.add(new Note(newPitch, n.startTick(), n.durationTicks(), newVel));
        }
        return new Motif(climax, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    /** Create a cadential ending phrase. */
    public Motif createCadence(Motif motif, KeySignature key) {
        List<Note> notes = motif.getNotes();
        if (notes.size() < 2) return motif;

        List<Note> cadence = new ArrayList<>(notes.subList(0, notes.size() - 2));

        // Add penultimate note on dominant
        Note penult = notes.get(notes.size() - 2);
        int dominantPitch = findNearestInKey(penult.pitch(), (key.root() + 7) % 12, key);
        cadence.add(new Note(dominantPitch, penult.startTick(), penult.durationTicks(), penult.velocity()));

        // Add final note on tonic
        Note last = notes.getLast();
        int tonicPitch = findNearestInKey(last.pitch(), key.root(), key);
        cadence.add(new Note(tonicPitch, last.startTick(), last.durationTicks() * 2, last.velocity()));

        return new Motif(cadence, motif.getBars(), motif.getBeatsPerBar(), motif.getTicksPerBeat());
    }

    // --- Helper methods ---

    private int transposeInKey(int pitch, int scaleSteps, KeySignature key) {
        int[] degrees = key.scaleDegrees();
        int pc = pitch % 12;
        int octave = pitch / 12;

        // Find closest scale degree
        int degreeIndex = 0;
        int minDist = 12;
        for (int i = 0; i < degrees.length; i++) {
            int dist = Math.abs(pc - degrees[i]);
            dist = Math.min(dist, 12 - dist);
            if (dist < minDist) {
                minDist = dist;
                degreeIndex = i;
            }
        }

        // Move by scale steps
        int newDegreeIndex = degreeIndex + scaleSteps;
        int octaveShift = 0;
        while (newDegreeIndex < 0) {
            newDegreeIndex += 7;
            octaveShift--;
        }
        while (newDegreeIndex >= 7) {
            newDegreeIndex -= 7;
            octaveShift++;
        }

        int newPitch = (octave + octaveShift) * 12 + degrees[newDegreeIndex];
        return Math.max(0, Math.min(127, newPitch));
    }

    private int getNeighborTone(int pitch, KeySignature key) {
        boolean upper = random.nextBoolean();
        return transposeInKey(pitch, upper ? 1 : -1, key);
    }

    private int findNearestInKey(int pitch, int targetPc, KeySignature key) {
        int octave = pitch / 12;
        int candidate = octave * 12 + targetPc;
        if (Math.abs(candidate - pitch) > 6) {
            candidate = candidate > pitch ? candidate - 12 : candidate + 12;
        }
        return Math.max(0, Math.min(127, candidate));
    }
}
