import br.com.karaoketv.*;
public class TestAlgorithms {
 public static void main(String[] args) {
  var chart=UltraStar.parse("#TITLE:Demo\n#ARTIST:Test\n#BPM:120\n#GAP:500\n: 0 4 0 la\n* 4 4 2 sol\nE\n");
  if (chart.notes.size()!=2 || Math.abs(chart.notes.get(0).startMs-500)>0.01 || Math.abs(chart.notes.get(0).endMs-1000)>0.01) throw new AssertionError("UltraStar timings");
  var engine=new ScoreEngine(chart,"P1");
  engine.feed(600,261.6256,40);
  engine.feed(1100,293.6648,40);
  if (engine.score()<95)throw new AssertionError("YIN/score matching notes: "+engine.score());
  short[] voice=new short[4096];for(int i=0;i<voice.length;i++)voice[i]=(short)(20000*Math.sin(2*Math.PI*220*i/22050));
  double hz=Yin.detect(voice,voice.length,22050);
  if (Math.abs(hz-220)>5)throw new AssertionError("YIN 220Hz: "+hz);
  System.out.println("PASS: UltraStar, score, YIN ("+hz+" Hz)");
 }
}
