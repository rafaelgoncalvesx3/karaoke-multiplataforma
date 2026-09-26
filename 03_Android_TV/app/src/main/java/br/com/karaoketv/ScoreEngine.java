package br.com.karaoketv;

import java.util.*;

/** Frame-scoring per expected note: silence penalizes, golden notes weigh x2, octave tolerant. */
public final class ScoreEngine {
    private final List<UltraStar.Note> notes;
    private double total=0, earned=0;
    private int frames=0, voiced=0;
    private double lastFrequency=0;
    private double offsetMs=0;
    private int transpose=0;
    public ScoreEngine(UltraStar chart, String singer) { notes=chart.singerNotes(singer); }
    public synchronized void setOffsetMs(double value) { offsetMs=value; }
    public synchronized void feed(double playbackMs, double hz, double frameDurationMs) {
        lastFrequency=hz;
        double ms=playbackMs+offsetMs;
        UltraStar.Note current=null;
        for (UltraStar.Note n: notes) if (ms>=n.startMs && ms<n.endMs) { current=n; break; }
        if (current==null) return;
        double eligible = Math.min(frameDurationMs, current.endMs - ms);
        if (eligible<=0) return;
        frames++;
        double weight = eligible*current.weight;
        total+=weight;
        if (hz<=0) return;
        voiced++;
        double midi = 69+12*Math.log(hz/440d)/Math.log(2);
        // UltraStar pitch is a relative semitone value. Compare modulo 12, allowing one octave difference.
        double delta = midi - current.pitch - transpose;
        double octaveDistance = Math.abs(delta - 12*Math.rint(delta/12));
        double quality = octaveDistance<=0.35 ? 1 : octaveDistance>=2.0 ? 0 : (2-octaveDistance)/1.65;
        earned+=weight*quality;
    }
    public synchronized double score() { return total==0?0:Math.max(0,Math.min(100,100*earned/total)); }
    public synchronized String snapshot() { return String.format(Locale.getDefault(), "%.0f/100  |  %.0f Hz  |  %d/%d quadros", score(),lastFrequency,voiced,frames); }
}
