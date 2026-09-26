package br.com.karaoketv;

import java.util.*;
import java.util.regex.*;

/** UltraStar TXT parser: BPM, GAP, #RELATIVE, #START, duo P1/P2, regular/golden/freestyle notes. */
public final class UltraStar {
    public static final class Note {
        public final double startMs, endMs;
        public final int pitch, weight;
        public final String lyric, singer;
        public final boolean freestyle;
        Note(double start, double end, int pitch, int weight, String lyric, String singer, boolean freestyle) {
            this.startMs = start; this.endMs = end; this.pitch = pitch; this.weight = weight;
            this.lyric = lyric; this.singer = singer; this.freestyle = freestyle;
        }
    }
    public final String title, artist, audio, video;
    public final List<Note> notes;
    public final double bpm, gapMs;
    private UltraStar(String title, String artist, String audio, String video, double bpm, double gap, List<Note> notes) {
        this.title = title; this.artist = artist; this.audio = audio; this.video = video;
        this.bpm = bpm; this.gapMs = gap; this.notes = Collections.unmodifiableList(notes);
    }
    public static UltraStar parse(String input) {
        Map<String,String> meta = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        List<Note> notes = new ArrayList<>();
        String singer = "P1";
        double bpm = -1, gap = 0, relativeBase = 0;
        boolean relative = false;
        for (String raw : input.replace("\ufeff", "").split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            if (line.startsWith("#")) {
                int colon = line.indexOf(':');
                if (colon <= 1) continue;
                String key = line.substring(1, colon).trim();
                String value = line.substring(colon+1).trim();
                meta.put(key, value);
                if (key.equalsIgnoreCase("BPM")) bpm = number(value);
                if (key.equalsIgnoreCase("GAP")) gap = number(value);
                if (key.equalsIgnoreCase("RELATIVE")) relative = value.equalsIgnoreCase("yes") || value.equalsIgnoreCase("true");
                if (key.equalsIgnoreCase("START")) relativeBase = number(value) * 1000d;
                continue;
            }
            if (line.equals("E")) break;
            if (line.equalsIgnoreCase("P1") || line.equalsIgnoreCase("P2")) { singer = line.toUpperCase(Locale.ROOT); continue; }
            if (line.startsWith("-")) {
                if (relative) {
                    String[] words = line.substring(1).trim().split("\\s+");
                    if (words.length > 0) relativeBase += number(words[0]) * beatMs(bpm);
                }
                continue;
            }
            char kind = line.charAt(0);
            if (kind != ':' && kind != '*' && kind != 'F' && kind != 'R' && kind != 'G') continue;
            // Prefix plus start, duration, pitch and the remaining (possibly spaced) lyric.
            Matcher m = Pattern.compile("^[\\:*FRG]\\s+(-?\\d+)\\s+(\\d+)\\s+(-?\\d+)(?:\\s(.*))?$").matcher(line);
            if (!m.matches()) continue;
            if (bpm <= 0) throw new IllegalArgumentException("#BPM deve preceder as notas");
            double begin = gap + relativeBase + Integer.parseInt(m.group(1)) * beatMs(bpm);
            double end = begin + Integer.parseInt(m.group(2)) * beatMs(bpm);
            if (end <= begin) continue;
            notes.add(new Note(begin, end, Integer.parseInt(m.group(3)), kind == '*' || kind == 'G' ? 2 : 1,
                    m.group(4) == null ? "" : m.group(4), singer, kind == 'F' || kind == 'R'));
        }
        if (bpm <= 0 || notes.isEmpty()) throw new IllegalArgumentException("Partitura UltraStar sem BPM/notas válidas");
        notes.sort(Comparator.comparingDouble(n -> n.startMs));
        return new UltraStar(meta.getOrDefault("TITLE", "Sem título"), meta.getOrDefault("ARTIST", "Desconhecido"),
                meta.getOrDefault("MP3", ""), meta.getOrDefault("VIDEO", ""), bpm, gap, notes);
    }
    // UltraStar uses 4 beats per quarter? Standard UltraStar TXT uses 4 ticks per beat: 60,000/(BPM*4).
    public static double beatMs(double bpm) {
        if (bpm <= 0) throw new IllegalArgumentException("BPM inválido");
        return 60000d / (bpm * 4d);
    }
    private static double number(String s) { return Double.parseDouble(s.replace(',', '.')); }
    public String lyricAt(double ms, String singer) {
        for (Note n : notes) if (n.singer.equals(singer) && ms >= n.startMs && ms < n.endMs) return n.lyric;
        return "";
    }
    public List<Note> singerNotes(String singer) {
        List<Note> result = new ArrayList<>();
        for (Note note : notes) if (note.singer.equals(singer) && !note.freestyle) result.add(note);
        return result;
    }
}
