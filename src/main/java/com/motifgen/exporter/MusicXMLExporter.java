package com.motifgen.exporter;

import com.motifgen.guitar.backing.BackingTrack;
import com.motifgen.guitar.backing.BassTrack;
import com.motifgen.guitar.backing.ChanneledNote;
import com.motifgen.guitar.backing.DrumEvent;
import com.motifgen.guitar.backing.DrumPattern;
import com.motifgen.guitar.backing.DrumTrack;
import com.motifgen.model.Motif;
import com.motifgen.model.Note;
import com.motifgen.model.Sentence;

import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.util.Comparator;
import java.util.List;

/**
 * Exports a Sentence to MusicXML format.
 */
public class MusicXMLExporter {

    private static final int DEFAULT_TEMPO_BPM = 120;

    // Maps pitch class (0-11) to MusicXML step and alter
    private static final String[] STEPS =  {"C", "C", "D", "D", "E", "F", "F", "G", "G", "A", "A", "B"};
    private static final int[]    ALTERS = { 0,   1,   0,   1,   0,   0,   1,   0,   1,   0,   1,   0};

    public static void export(Sentence sentence, File outputFile, int tempoBpm) throws Exception {
        List<Note> notes = sentence.getAllNotes();
        Motif firstPhrase = sentence.getPhrases().getFirst();
        int ticksPerBeat = firstPhrase.getTicksPerBeat();
        int beatsPerBar = firstPhrase.getBeatsPerBar();
        int divisions = ticksPerBeat; // MusicXML divisions per quarter note

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element scorePartwise = doc.createElement("score-partwise");
        scorePartwise.setAttribute("version", "4.0");
        doc.appendChild(scorePartwise);

        // Work title
        Element work = appendElement(doc, scorePartwise, "work");
        appendTextElement(doc, work, "work-title",
                "MotifGen: " + sentence.getKeyName() + " (" + sentence.getStructure() + ")");

        // Identification
        Element identification = appendElement(doc, scorePartwise, "identification");
        Element creator = appendTextElement(doc, identification, "creator", "MotifGen");
        creator.setAttribute("type", "software");

        // Part list
        Element partList = appendElement(doc, scorePartwise, "part-list");
        Element scorePart = appendElement(doc, partList, "score-part");
        scorePart.setAttribute("id", "P1");
        appendTextElement(doc, scorePart, "part-name", "Melody");

        // Part with measures
        Element part = appendElement(doc, scorePartwise, "part");
        part.setAttribute("id", "P1");

        int totalBars = sentence.totalBars();
        long ticksPerBar = (long) beatsPerBar * ticksPerBeat;

        for (int bar = 0; bar < totalBars; bar++) {
            Element measure = appendElement(doc, part, "measure");
            measure.setAttribute("number", String.valueOf(bar + 1));

            // Attributes on first measure
            if (bar == 0) {
                Element attributes = appendElement(doc, measure, "attributes");
                appendTextElement(doc, attributes, "divisions", String.valueOf(divisions));

                // Key signature
                Element key = appendElement(doc, attributes, "key");
                KeyInfo keyInfo = parseKeyName(sentence.getKeyName());
                appendTextElement(doc, key, "fifths", String.valueOf(keyInfo.fifths));
                appendTextElement(doc, key, "mode", keyInfo.mode);

                // Time signature
                Element time = appendElement(doc, attributes, "time");
                appendTextElement(doc, time, "beats", String.valueOf(beatsPerBar));
                appendTextElement(doc, time, "beat-type", "4");

                // Clef (treble)
                Element clef = appendElement(doc, attributes, "clef");
                appendTextElement(doc, clef, "sign", "G");
                appendTextElement(doc, clef, "line", "2");

                // Tempo direction
                Element direction = appendElement(doc, measure, "direction");
                direction.setAttribute("placement", "above");
                Element dirType = appendElement(doc, direction, "direction-type");
                Element metronome = appendElement(doc, dirType, "metronome");
                appendTextElement(doc, metronome, "beat-unit", "quarter");
                appendTextElement(doc, metronome, "per-minute", String.valueOf(tempoBpm));
                Element sound = appendElement(doc, direction, "sound");
                sound.setAttribute("tempo", String.valueOf(tempoBpm));
            }

            long barStart = bar * ticksPerBar;
            long barEnd = barStart + ticksPerBar;
            long cursor = barStart;
            long prevNoteStart = -1;

            List<Note> barNotes = notes.stream()
                    .filter(n -> n.endTick() > barStart && n.startTick() < barEnd)
                    .sorted(Comparator.comparingLong(Note::startTick)
                            .thenComparingInt(Note::pitch))
                    .toList();

            for (Note note : barNotes) {
                long noteStart = Math.max(note.startTick(), barStart);
                boolean isChord = (noteStart == prevNoteStart);

                if (!isChord && noteStart > cursor) {
                    addRest(doc, measure, noteStart - cursor, divisions);
                }

                if (note.isRest()) {
                    if (!isChord) {
                        long dur = Math.min(note.endTick(), barEnd) - noteStart;
                        addRest(doc, measure, dur, divisions);
                        cursor = noteStart + dur;
                        prevNoteStart = noteStart;
                    }
                    continue;
                }

                long effectiveEnd = Math.min(note.endTick(), barEnd);
                long duration = effectiveEnd - noteStart;
                if (duration <= 0) continue;

                Element noteElem = appendElement(doc, measure, "note");
                if (isChord) {
                    appendElement(doc, noteElem, "chord");
                }
                Element pitch = appendElement(doc, noteElem, "pitch");
                int pc = note.pitch() % 12;
                appendTextElement(doc, pitch, "step", STEPS[pc]);
                if (ALTERS[pc] != 0) {
                    appendTextElement(doc, pitch, "alter", String.valueOf(ALTERS[pc]));
                }
                int octave = note.pitch() / 12 - 1;
                appendTextElement(doc, pitch, "octave", String.valueOf(octave));
                appendTextElement(doc, noteElem, "duration", String.valueOf(duration));
                String type = ticksToType(duration, divisions);
                if (type != null) {
                    appendTextElement(doc, noteElem, "type", type);
                }

                if (!isChord) {
                    cursor = effectiveEnd;
                    prevNoteStart = noteStart;
                }
            }

            if (cursor < barEnd) {
                addRest(doc, measure, barEnd - cursor, divisions);
            }
        }

        // Write
        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                "-//Recordare//DTD MusicXML 4.0 Partwise//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                "http://www.musicxml.org/dtds/partwise.dtd");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
    }

    public static void export(Sentence sentence, File outputFile) throws Exception {
        export(sentence, outputFile, DEFAULT_TEMPO_BPM);
    }

    /**
     * Exports a 3-part MusicXML file: melody (P1), rhythm guitar (P2, program 25),
     * and bass guitar (P3, program 34).
     *
     * @param sentence   the melody sentence
     * @param backing    the rhythm guitar backing track
     * @param bass       the bass guitar track
     * @param outputFile destination MusicXML file
     * @param tempoBpm   tempo in beats per minute
     * @throws Exception if XML I/O fails
     */
    public static void export(Sentence sentence, BackingTrack backing, BassTrack bass,
            File outputFile, int tempoBpm) throws Exception {
        Motif firstPhrase = sentence.getPhrases().getFirst();
        int ticksPerBeat = firstPhrase.getTicksPerBeat();
        int beatsPerBar = firstPhrase.getBeatsPerBar();
        int divisions = ticksPerBeat;

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element scorePartwise = doc.createElement("score-partwise");
        scorePartwise.setAttribute("version", "4.0");
        doc.appendChild(scorePartwise);

        Element work = appendElement(doc, scorePartwise, "work");
        appendTextElement(doc, work, "work-title",
                "MotifGen: " + sentence.getKeyName() + " (" + sentence.getStructure() + ")");

        Element identification = appendElement(doc, scorePartwise, "identification");
        Element creator = appendTextElement(doc, identification, "creator", "MotifGen");
        creator.setAttribute("type", "software");

        // Part list — 3 parts
        Element partList = appendElement(doc, scorePartwise, "part-list");

        Element sp1 = appendElement(doc, partList, "score-part");
        sp1.setAttribute("id", "P1");
        appendTextElement(doc, sp1, "part-name", "Melody");

        Element sp2 = appendElement(doc, partList, "score-part");
        sp2.setAttribute("id", "P2");
        appendTextElement(doc, sp2, "part-name", "Rhythm Guitar");
        Element si2 = appendElement(doc, sp2, "score-instrument");
        si2.setAttribute("id", "P2-I1");
        appendTextElement(doc, si2, "instrument-name", "Acoustic Guitar");
        Element mp2 = appendElement(doc, sp2, "midi-instrument");
        mp2.setAttribute("id", "P2-I1");
        appendTextElement(doc, mp2, "midi-program", "25");

        Element sp3 = appendElement(doc, partList, "score-part");
        sp3.setAttribute("id", "P3");
        appendTextElement(doc, sp3, "part-name", "Bass Guitar");
        Element si3 = appendElement(doc, sp3, "score-instrument");
        si3.setAttribute("id", "P3-I1");
        appendTextElement(doc, si3, "instrument-name", "Electric Bass");
        Element mp3 = appendElement(doc, sp3, "midi-instrument");
        mp3.setAttribute("id", "P3-I1");
        appendTextElement(doc, mp3, "midi-program", "34");

        int totalBars = sentence.totalBars();
        long ticksPerBar = (long) beatsPerBar * ticksPerBeat;

        // --- P1: Melody ---
        Element melodyPart = appendElement(doc, scorePartwise, "part");
        melodyPart.setAttribute("id", "P1");
        List<Note> melodyNotes = sentence.getAllNotes();
        writeMeasures(doc, melodyPart, melodyNotes, totalBars, ticksPerBar, ticksPerBeat,
                beatsPerBar, divisions, tempoBpm, sentence.getKeyName(), null);

        // --- P2: Rhythm Guitar ---
        Element guitarPart = appendElement(doc, scorePartwise, "part");
        guitarPart.setAttribute("id", "P2");
        List<Note> guitarNotes = backing.notes().stream()
                .map(ChanneledNote::note).toList();
        writeMeasures(doc, guitarPart, guitarNotes, totalBars, ticksPerBar, ticksPerBeat,
                beatsPerBar, divisions, tempoBpm, sentence.getKeyName(), null);

        // --- P3: Bass Guitar ---
        Element bassPart = appendElement(doc, scorePartwise, "part");
        bassPart.setAttribute("id", "P3");
        List<Note> bassNotes = bass.notes().stream()
                .map(ChanneledNote::note).toList();
        writeMeasures(doc, bassPart, bassNotes, totalBars, ticksPerBar, ticksPerBeat,
                beatsPerBar, divisions, tempoBpm, sentence.getKeyName(), "F");

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                "-//Recordare//DTD MusicXML 4.0 Partwise//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                "http://www.musicxml.org/dtds/partwise.dtd");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
    }

    /**
     * Exports a 4-part MusicXML file: melody (P1), rhythm guitar (P2),
     * bass guitar (P3), and a percussion drum part (P4).
     *
     * <p>The drum part uses {@code <unpitched>} note elements with a percussion
     * clef. Cymbals are rendered with an "x" notehead.
     *
     * @param sentence   the melody sentence
     * @param backing    the rhythm guitar backing track
     * @param bass       the bass guitar track
     * @param drums      the drum track
     * @param outputFile destination MusicXML file
     * @param tempoBpm   tempo in beats per minute
     * @throws Exception if XML I/O fails
     */
    public static void export(Sentence sentence, BackingTrack backing, BassTrack bass,
            DrumTrack drums, File outputFile, int tempoBpm) throws Exception {
        Motif firstPhrase = sentence.getPhrases().getFirst();
        int ticksPerBeat = firstPhrase.getTicksPerBeat();
        int beatsPerBar = firstPhrase.getBeatsPerBar();
        int divisions = ticksPerBeat;

        Document doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().newDocument();

        Element scorePartwise = doc.createElement("score-partwise");
        scorePartwise.setAttribute("version", "4.0");
        doc.appendChild(scorePartwise);

        Element work = appendElement(doc, scorePartwise, "work");
        appendTextElement(doc, work, "work-title",
                "MotifGen: " + sentence.getKeyName() + " (" + sentence.getStructure() + ")");

        Element identification = appendElement(doc, scorePartwise, "identification");
        Element creator = appendTextElement(doc, identification, "creator", "MotifGen");
        creator.setAttribute("type", "software");

        // Part list — 4 parts
        Element partList = appendElement(doc, scorePartwise, "part-list");

        Element sp1 = appendElement(doc, partList, "score-part");
        sp1.setAttribute("id", "P1");
        appendTextElement(doc, sp1, "part-name", "Melody");

        Element sp2 = appendElement(doc, partList, "score-part");
        sp2.setAttribute("id", "P2");
        appendTextElement(doc, sp2, "part-name", "Rhythm Guitar");

        Element sp3 = appendElement(doc, partList, "score-part");
        sp3.setAttribute("id", "P3");
        appendTextElement(doc, sp3, "part-name", "Bass Guitar");

        Element sp4 = appendElement(doc, partList, "score-part");
        sp4.setAttribute("id", "P4");
        appendTextElement(doc, sp4, "part-name", "Drums");
        Element si4 = appendElement(doc, sp4, "score-instrument");
        si4.setAttribute("id", "P4-I1");
        appendTextElement(doc, si4, "instrument-name", "Drumset");
        Element mp4 = appendElement(doc, sp4, "midi-instrument");
        mp4.setAttribute("id", "P4-I1");
        appendTextElement(doc, mp4, "midi-channel", "10");
        appendTextElement(doc, mp4, "midi-program", "1");

        int totalBars = sentence.totalBars();
        long ticksPerBar = (long) beatsPerBar * ticksPerBeat;

        // --- P1: Melody ---
        Element melodyPart = appendElement(doc, scorePartwise, "part");
        melodyPart.setAttribute("id", "P1");
        writeMeasures(doc, melodyPart, sentence.getAllNotes(), totalBars, ticksPerBar,
                ticksPerBeat, beatsPerBar, divisions, tempoBpm,
                sentence.getKeyName(), null);

        // --- P2: Rhythm Guitar ---
        Element guitarPart = appendElement(doc, scorePartwise, "part");
        guitarPart.setAttribute("id", "P2");
        writeMeasures(doc, guitarPart,
                backing.notes().stream().map(ChanneledNote::note).toList(),
                totalBars, ticksPerBar, ticksPerBeat, beatsPerBar, divisions,
                tempoBpm, sentence.getKeyName(), null);

        // --- P3: Bass Guitar ---
        Element bassPart = appendElement(doc, scorePartwise, "part");
        bassPart.setAttribute("id", "P3");
        writeMeasures(doc, bassPart,
                bass.notes().stream().map(ChanneledNote::note).toList(),
                totalBars, ticksPerBar, ticksPerBeat, beatsPerBar, divisions,
                tempoBpm, sentence.getKeyName(), "F");

        // --- P4: Drums (percussion) ---
        Element drumPart = appendElement(doc, scorePartwise, "part");
        drumPart.setAttribute("id", "P4");
        writeDrumMeasures(doc, drumPart, drums, totalBars, ticksPerBar, beatsPerBar,
                divisions, tempoBpm);

        Transformer transformer = TransformerFactory.newInstance().newTransformer();
        transformer.setOutputProperty(OutputKeys.INDENT, "yes");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_PUBLIC,
                "-//Recordare//DTD MusicXML 4.0 Partwise//EN");
        transformer.setOutputProperty(OutputKeys.DOCTYPE_SYSTEM,
                "http://www.musicxml.org/dtds/partwise.dtd");
        transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "2");
        transformer.transform(new DOMSource(doc), new StreamResult(outputFile));
    }

    /**
     * Writes percussion measures for the drum part using {@code <unpitched>}
     * note elements. Cymbals use an "x" notehead; kick/snare use normal heads.
     */
    private static void writeDrumMeasures(Document doc, Element partElem, DrumTrack drums,
            int totalBars, long ticksPerBar, int beatsPerBar, int divisions, int tempoBpm) {
        List<DrumEvent> events = drums.events();

        for (int bar = 0; bar < totalBars; bar++) {
            Element measure = appendElement(doc, partElem, "measure");
            measure.setAttribute("number", String.valueOf(bar + 1));

            if (bar == 0) {
                Element attributes = appendElement(doc, measure, "attributes");
                appendTextElement(doc, attributes, "divisions", String.valueOf(divisions));

                Element time = appendElement(doc, attributes, "time");
                appendTextElement(doc, time, "beats", String.valueOf(beatsPerBar));
                appendTextElement(doc, time, "beat-type", "4");

                Element clefElem = appendElement(doc, attributes, "clef");
                appendTextElement(doc, clefElem, "sign", "percussion");

                Element direction = appendElement(doc, measure, "direction");
                direction.setAttribute("placement", "above");
                Element dirType = appendElement(doc, direction, "direction-type");
                Element metronome = appendElement(doc, dirType, "metronome");
                appendTextElement(doc, metronome, "beat-unit", "quarter");
                appendTextElement(doc, metronome, "per-minute", String.valueOf(tempoBpm));
                Element sound = appendElement(doc, direction, "sound");
                sound.setAttribute("tempo", String.valueOf(tempoBpm));
            }

            long barStart = bar * ticksPerBar;
            long barEnd = barStart + ticksPerBar;
            boolean isLastBar = (bar == totalBars - 1);
            boolean anyInBar = false;
            for (DrumEvent ev : events) {
                if (ev.startTick() < barStart) continue;
                // Last bar has no upper bound: captures crash+kick placed on the phrase
                // downbeat at the sentence boundary, including any humanization jitter.
                if (!isLastBar && ev.startTick() >= barEnd) continue;
                appendDrumNote(doc, measure, ev, divisions);
                anyInBar = true;
            }
            if (!anyInBar) {
                addRest(doc, measure, ticksPerBar, divisions);
            }
        }
    }

    private static void appendDrumNote(Document doc, Element measure, DrumEvent ev,
            int divisions) {
        Element noteElem = appendElement(doc, measure, "note");
        Element unpitched = appendElement(doc, noteElem, "unpitched");
        DrumDisplay display = drumDisplay(ev.gmNote());
        appendTextElement(doc, unpitched, "display-step", display.step());
        appendTextElement(doc, unpitched, "display-octave", String.valueOf(display.octave()));
        long duration = Math.max(1, ev.durationTicks());
        appendTextElement(doc, noteElem, "duration", String.valueOf(duration));
        String type = ticksToType(duration, divisions);
        if (type != null) {
            appendTextElement(doc, noteElem, "type", type);
        }
        if (isCymbal(ev.gmNote())) {
            appendTextElement(doc, noteElem, "notehead", "x");
        }
    }

    private static boolean isCymbal(int gmNote) {
        return gmNote == DrumPattern.CLOSED_HIHAT || gmNote == DrumPattern.OPEN_HIHAT
                || gmNote == DrumPattern.RIDE || gmNote == DrumPattern.RIDE_BELL
                || gmNote == DrumPattern.CRASH;
    }

    private record DrumDisplay(String step, int octave) {}

    private static DrumDisplay drumDisplay(int gmNote) {
        // Standard 5-line percussion staff positions.
        if (gmNote == DrumPattern.KICK)          return new DrumDisplay("F", 4);
        if (gmNote == DrumPattern.SNARE)         return new DrumDisplay("C", 5);
        if (gmNote == DrumPattern.CLOSED_HIHAT)  return new DrumDisplay("G", 5);
        if (gmNote == DrumPattern.OPEN_HIHAT)    return new DrumDisplay("G", 5);
        if (gmNote == DrumPattern.RIDE)          return new DrumDisplay("F", 5);
        if (gmNote == DrumPattern.RIDE_BELL)     return new DrumDisplay("F", 5);
        if (gmNote == DrumPattern.CRASH)         return new DrumDisplay("A", 5);
        if (gmNote == DrumPattern.HIGH_TOM)      return new DrumDisplay("E", 5);
        if (gmNote == DrumPattern.MID_TOM)       return new DrumDisplay("D", 5);
        if (gmNote == DrumPattern.LOW_TOM)       return new DrumDisplay("A", 4);
        return new DrumDisplay("C", 5);
    }

    /**
     * Writes measure elements for one part into {@code partElem}.
     *
     * @param clefSign "G" for treble, "F" for bass, or {@code null} to default to "G"
     */
    private static void writeMeasures(Document doc, Element partElem, List<Note> notes,
            int totalBars, long ticksPerBar, int ticksPerBeat, int beatsPerBar,
            int divisions, int tempoBpm, String keyName, String clefSign) {

        String clef = (clefSign != null) ? clefSign : "G";
        String clefLine = "F".equals(clef) ? "4" : "2";

        for (int bar = 0; bar < totalBars; bar++) {
            Element measure = appendElement(doc, partElem, "measure");
            measure.setAttribute("number", String.valueOf(bar + 1));

            if (bar == 0) {
                Element attributes = appendElement(doc, measure, "attributes");
                appendTextElement(doc, attributes, "divisions", String.valueOf(divisions));

                Element key = appendElement(doc, attributes, "key");
                KeyInfo keyInfo = parseKeyName(keyName);
                appendTextElement(doc, key, "fifths", String.valueOf(keyInfo.fifths));
                appendTextElement(doc, key, "mode", keyInfo.mode);

                Element time = appendElement(doc, attributes, "time");
                appendTextElement(doc, time, "beats", String.valueOf(beatsPerBar));
                appendTextElement(doc, time, "beat-type", "4");

                Element clefElem = appendElement(doc, attributes, "clef");
                appendTextElement(doc, clefElem, "sign", clef);
                appendTextElement(doc, clefElem, "line", clefLine);

                Element direction = appendElement(doc, measure, "direction");
                direction.setAttribute("placement", "above");
                Element dirType = appendElement(doc, direction, "direction-type");
                Element metronome = appendElement(doc, dirType, "metronome");
                appendTextElement(doc, metronome, "beat-unit", "quarter");
                appendTextElement(doc, metronome, "per-minute", String.valueOf(tempoBpm));
                Element sound = appendElement(doc, direction, "sound");
                sound.setAttribute("tempo", String.valueOf(tempoBpm));
            }

            long barStart = bar * ticksPerBar;
            long barEnd = barStart + ticksPerBar;
            long cursor = barStart;
            long prevNoteStart = -1;

            List<Note> barNotes = notes.stream()
                    .filter(n -> n.endTick() > barStart && n.startTick() < barEnd)
                    .sorted(Comparator.comparingLong(Note::startTick)
                            .thenComparingInt(Note::pitch))
                    .toList();

            for (Note note : barNotes) {
                long noteStart = Math.max(note.startTick(), barStart);
                boolean isChord = (noteStart == prevNoteStart);

                if (!isChord && noteStart > cursor) {
                    addRest(doc, measure, noteStart - cursor, divisions);
                }

                if (note.isRest()) {
                    if (!isChord) {
                        long dur = Math.min(note.endTick(), barEnd) - noteStart;
                        addRest(doc, measure, dur, divisions);
                        cursor = noteStart + dur;
                        prevNoteStart = noteStart;
                    }
                    continue;
                }

                long effectiveEnd = Math.min(note.endTick(), barEnd);
                long duration = effectiveEnd - noteStart;
                if (duration <= 0) continue;

                Element noteElem = appendElement(doc, measure, "note");
                if (isChord) {
                    appendElement(doc, noteElem, "chord");
                }
                Element pitch = appendElement(doc, noteElem, "pitch");
                int pc = note.pitch() % 12;
                appendTextElement(doc, pitch, "step", STEPS[pc]);
                if (ALTERS[pc] != 0) {
                    appendTextElement(doc, pitch, "alter", String.valueOf(ALTERS[pc]));
                }
                int octave = note.pitch() / 12 - 1;
                appendTextElement(doc, pitch, "octave", String.valueOf(octave));
                appendTextElement(doc, noteElem, "duration", String.valueOf(duration));
                String type = ticksToType(duration, divisions);
                if (type != null) {
                    appendTextElement(doc, noteElem, "type", type);
                }

                if (!isChord) {
                    cursor = effectiveEnd;
                    prevNoteStart = noteStart;
                }
            }

            if (cursor < barEnd) {
                addRest(doc, measure, barEnd - cursor, divisions);
            }
        }
    }

    private static void addRest(Document doc, Element measure, long durationTicks, int divisions) {
        Element note = appendElement(doc, measure, "note");
        appendElement(doc, note, "rest");
        appendTextElement(doc, note, "duration", String.valueOf(durationTicks));
        String type = ticksToType(durationTicks, divisions);
        if (type != null) {
            appendTextElement(doc, note, "type", type);
        }
    }

    private static String ticksToType(long ticks, int divisions) {
        // divisions = ticks per quarter note
        double quarters = (double) ticks / divisions;
        if (quarters >= 3.5) return "whole";
        if (quarters >= 1.5) return "half";
        if (quarters >= 0.75) return "quarter";
        if (quarters >= 0.375) return "eighth";
        if (quarters >= 0.1875) return "16th";
        if (quarters >= 0.09) return "32nd";
        return "64th";
    }

    private record KeyInfo(int fifths, String mode) {}

    private static KeyInfo parseKeyName(String keyName) {
        // Key name format: "C major", "A minor", "F# major", etc.
        String mode = keyName.contains("minor") ? "minor" : "major";
        String rootStr = keyName.replace(" major", "").replace(" minor", "").trim();

        // Map root note to fifths on the circle of fifths
        int fifths = switch (rootStr) {
            case "C"  -> 0;
            case "G"  -> 1;
            case "D"  -> 2;
            case "A"  -> 3;
            case "E"  -> 4;
            case "B"  -> 5;
            case "F#" -> 6;
            case "C#" -> 7;
            case "F"  -> -1;
            case "Bb" -> -2;
            case "Eb" -> -3;
            case "Ab" -> -4;
            case "Db" -> -5;
            case "Gb" -> -6;
            case "Cb" -> -7;
            // enharmonic sharps
            case "G#" -> 8; // not standard but handle gracefully
            case "D#" -> 9;
            case "A#" -> 10;
            default   -> 0;
        };

        // For minor keys, the fifths value is relative to the minor key
        // (e.g., A minor = 0 fifths, E minor = 1 fifth, D minor = -1)
        if (mode.equals("minor")) {
            fifths = fifths - 3; // A minor (root A, fifths=3) → 0
        }

        return new KeyInfo(fifths, mode);
    }

    private static Element appendElement(Document doc, Element parent, String name) {
        Element el = doc.createElement(name);
        parent.appendChild(el);
        return el;
    }

    private static Element appendTextElement(Document doc, Element parent, String name, String text) {
        Element el = doc.createElement(name);
        el.setTextContent(text);
        parent.appendChild(el);
        return el;
    }
}
