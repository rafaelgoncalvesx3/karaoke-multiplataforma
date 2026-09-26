package br.com.karaoketv;

import android.Manifest;
import android.app.Activity;
import android.content.*;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.*;
import android.net.Uri;
import android.os.*;
import android.provider.DocumentsContract;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/** Android TV remote-first karaoke with Storage Access Framework and wired/USB microphone capture. */
public final class MainActivity extends Activity {
    private static final int CHOOSE_LIBRARY=10, REQUEST_MIC=11;
    private static final String[] MEDIA={"mp3","wav","flac","mp4","mkv","m4a","webm","ogg"};
    private final ExecutorService executor=Executors.newSingleThreadExecutor();
    private final Handler handler=new Handler(Looper.getMainLooper());
    private final List<Song> library=new ArrayList<>();
    private LinearLayout browser, controls;
    private FrameLayout root, playScreen;
    private TextView status, caption, scoreText;
    private ListView list;
    private VideoView video;
    private MediaPlayer audio;
    private Microphone microphone;
    private ScoreEngine score;
    private UltraStar chart;
    private String singer="P1";
    private long startElapsedMs, pausedTotal;
    private double scoreOffsetMs=0;
    private boolean playing=false, isVideo=false;
    private Song activeSong;
    private int browserLevel=0;
    private String selectedArtist, selectedAlbum;
    private List<Song> visibleSongs=Collections.emptyList();
    private final Runnable ticker=new Runnable() {
        @Override public void run() {
            if(playing) {
                long pos=positionMs();
                if(chart!=null) caption.setText(chart.lyricAt(pos+scoreOffsetMs,singer));
                if(score!=null) scoreText.setText(score.snapshot());
                handler.postDelayed(this,120);
            }
        }
    };
    private static final class Song {
        final String artist,album,title;
        final Uri media,txt;
        final String mediaName;
        Song(String artist,String album,String title,String mediaName,Uri media,Uri txt) {
            this.artist=artist;this.album=album;this.title=title;this.mediaName=mediaName;this.media=media;this.txt=txt;
        }
        Song withChart(Uri newTxt) { return new Song(artist,album,title,mediaName,media,newTxt); }
    }
    private static final class Entry {
        final String name;final Uri uri;final boolean folder;
        Entry(String name,Uri uri,boolean folder) {this.name=name;this.uri=uri;this.folder=folder;}
    }

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        createUi();
        String saved=getPreferences(MODE_PRIVATE).getString("tree",null);
        if(saved==null) { status.setText("Selecione a pasta das músicas no armazenamento ou pendrive."); showBrowser(); }
        else scan(Uri.parse(saved));
    }
    private GradientDrawable background(int color,int radius) {
        GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(radius);return d;
    }
    private TextView text(String value,int size) {
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(Color.WHITE);
        t.setGravity(Gravity.CENTER_VERTICAL);t.setPadding(18,12,18,12);return t;
    }
    private Button button(String label,Runnable action) {
        Button b=new Button(this); b.setText(label); b.setTextSize(17);b.setAllCaps(false);
        b.setTextColor(Color.WHITE);b.setBackground(background(0xFFBD2030,12));
        b.setOnClickListener(v->action.run());b.setFocusable(true);return b;
    }
    private void createUi() {
        root=new FrameLayout(this);root.setBackgroundColor(0xFF0B1020);setContentView(root);
        browser=new LinearLayout(this);browser.setOrientation(LinearLayout.VERTICAL);browser.setPadding(28,18,28,18);
        root.addView(browser,new FrameLayout.LayoutParams(-1,-1));
        TextView heading=text("🎤  KARAOKE TV",30);heading.setTypeface(null,Typeface.BOLD);browser.addView(heading);
        LinearLayout toolbar=new LinearLayout(this);toolbar.setOrientation(LinearLayout.HORIZONTAL);
        Button folders=button("📁 Selecionar músicas",this::chooseFolder);toolbar.addView(folders,new LinearLayout.LayoutParams(0,62,1));
        Button refresh=button("↻ Atualizar",()->{
            String saved=getPreferences(MODE_PRIVATE).getString("tree",null);
            if(saved!=null)scan(Uri.parse(saved));else chooseFolder();
        });toolbar.addView(refresh,new LinearLayout.LayoutParams(0,62,1));browser.addView(toolbar);
        status=text("Carregando...",16);browser.addView(status);
        list=new ListView(this);list.setDividerHeight(7);list.setBackgroundColor(Color.TRANSPARENT);
        list.setSelector(android.R.color.holo_red_dark);browser.addView(list,new LinearLayout.LayoutParams(-1,0,1));
        list.setOnItemClickListener((parent,view,position,id)->openRow(position));
        playScreen=new FrameLayout(this);playScreen.setBackgroundColor(Color.BLACK);
        root.addView(playScreen,new FrameLayout.LayoutParams(-1,-1));
        video=new VideoView(this);playScreen.addView(video,new FrameLayout.LayoutParams(-1,-1));
        LinearLayout overlay=new LinearLayout(this);overlay.setOrientation(LinearLayout.VERTICAL);
        FrameLayout.LayoutParams overlayLayout=new FrameLayout.LayoutParams(-1,-2,Gravity.BOTTOM);
        overlay.setBackgroundColor(0xCC070B16);overlay.setPadding(14,8,14,16);
        scoreText=text("Aguardando microfone",19);overlay.addView(scoreText);
        caption=text("",34);caption.setGravity(Gravity.CENTER);caption.setTypeface(null,Typeface.BOLD);
        overlay.addView(caption,new LinearLayout.LayoutParams(-1,86));
        controls=new LinearLayout(this);controls.setOrientation(LinearLayout.HORIZONTAL);
        controls.addView(button("■ Parar",this::stop),new LinearLayout.LayoutParams(0,62,1));
        controls.addView(button("⏯ Pausa",this::togglePause),new LinearLayout.LayoutParams(0,62,1));
        controls.addView(button("− 100 ms",()->{scoreOffsetMs-=100;if(score!=null)score.setOffsetMs(scoreOffsetMs);}),new LinearLayout.LayoutParams(0,62,1));
        controls.addView(button("+ 100 ms",()->{scoreOffsetMs+=100;if(score!=null)score.setOffsetMs(scoreOffsetMs);}),new LinearLayout.LayoutParams(0,62,1));
        overlay.addView(controls);playScreen.addView(overlay,overlayLayout);
        playScreen.setVisibility(View.GONE);folders.requestFocus();
    }
    private void chooseFolder() {
        Intent intent=new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent,CHOOSE_LIBRARY);
    }
    @Override protected void onActivityResult(int request,int result,Intent data) {
        super.onActivityResult(request,result,data);
        if(request==CHOOSE_LIBRARY && result==RESULT_OK && data!=null) {
            Uri uri=data.getData();if(uri==null)return;
            try {getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            getPreferences(MODE_PRIVATE).edit().putString("tree",uri.toString()).apply();scan(uri);
        }
    }
    private static String ext(String name) { int dot=name.lastIndexOf('.');return dot<0?"":name.substring(dot+1).toLowerCase(Locale.ROOT); }
    private static boolean isMedia(String filename) { return Arrays.asList(MEDIA).contains(ext(filename)); }
    private List<Entry> children(Uri tree,String docId) {
        ArrayList<Entry> entries=new ArrayList<>();
        Uri query=DocumentsContract.buildChildDocumentsUriUsingTree(tree,docId);
        try(Cursor c=getContentResolver().query(query,new String[]{DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE},null,null,null)) {
            if(c!=null)while(c.moveToNext()) {
                String id=c.getString(0),name=c.getString(1),mime=c.getString(2);
                Uri document=DocumentsContract.buildDocumentUriUsingTree(tree,id);
                entries.add(new Entry(name,document,DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)));
            }
        }
        return entries;
    }
    private void scan(Uri tree) {
        status.setText("Lendo pendrive / armazenamento...");
        executor.execute(()->{
            List<Song> found=new ArrayList<>();
            String error=null;
            try {
                Deque<Object[]> stack=new ArrayDeque<>();
                stack.push(new Object[]{DocumentsContract.getTreeDocumentId(tree),new ArrayList<String>(),0});
                int traversed=0;
                while(!stack.isEmpty()) {
                    Object[] frame=stack.pop();String id=(String)frame[0];
                    @SuppressWarnings("unchecked") List<String> ancestors=(List<String>)frame[1];int depth=(Integer)frame[2];
                    if(depth>12 || ++traversed>10000)break;
                    List<Entry> entries=children(tree,id);
                    Map<String,Uri> charts=new HashMap<>();
                    for(Entry e:entries)if(!e.folder && ext(e.name).equals("txt"))charts.put(stripExt(e.name).toLowerCase(Locale.ROOT),e.uri);
                    for(Entry e:entries) {
                        if(e.folder) {
                            ArrayList<String> next=new ArrayList<>(ancestors);next.add(e.name);
                            stack.push(new Object[]{DocumentsContract.getDocumentId(e.uri),next,depth+1});
                        } else if(isMedia(e.name)) {
                            String artist=ancestors.size()>0?ancestors.get(0):"Artista desconhecido";
                            String album=ancestors.size()>1?ancestors.get(1):"Álbum desconhecido";
                            String base=stripExt(e.name);
                            Uri txt=charts.get(base.toLowerCase(Locale.ROOT));
                            found.add(new Song(artist,album,base,e.name,e.uri,txt));
                        }
                    }
                }
                found.sort(Comparator.comparing((Song s)->s.artist,String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(s->s.album,String.CASE_INSENSITIVE_ORDER).thenComparing(s->s.title,String.CASE_INSENSITIVE_ORDER));
            } catch(Exception ex) {error=ex.getMessage();}
            String message=error;
            handler.post(()->{
                library.clear();library.addAll(found);browserLevel=0;
                status.setText(message==null ? library.size()+" músicas encontradas. Escolha o artista." : "Leitura parcial: "+message);
                showBrowser();
            });
        });
    }
    private static String stripExt(String name) {int p=name.lastIndexOf('.');return p<=0?name:name.substring(0,p);}
    private final List<String> rows=new ArrayList<>();
    private void showBrowser() {
        playScreen.setVisibility(View.GONE);browser.setVisibility(View.VISIBLE);rows.clear();
        if(browserLevel>0)rows.add("↩ Voltar");
        if(browserLevel==0) {
            TreeSet<String> artists=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(Song s:library)artists.add(s.artist);
            rows.addAll(artists);
        } else if(browserLevel==1) {
            TreeSet<String> albums=new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
            for(Song s:library)if(s.artist.equals(selectedArtist))albums.add(s.album);
            rows.addAll(albums);
        } else {
            ArrayList<Song> songs=new ArrayList<>();
            for(Song s:library)if(s.artist.equals(selectedArtist)&&s.album.equals(selectedAlbum))songs.add(s);
            visibleSongs=songs;
            for(Song s:songs)rows.add(s.title+(s.txt==null?"":"  ★ UltraStar"));
        }
        ArrayAdapter<String> adapter=new ArrayAdapter<String>(this,android.R.layout.simple_list_item_1,rows) {
            @Override public View getView(int position,View convert,android.view.ViewGroup parent) {
                TextView view=(TextView)super.getView(position,convert,parent);
                view.setTextColor(Color.WHITE);view.setTextSize(24);view.setPadding(22,14,22,14);
                view.setBackground(background(position%2==0?0xFF1B2844:0xFF151E36,9));return view;
            }
        };
        list.setAdapter(adapter);if(rows.size()>0)list.requestFocus();
    }
    private void openRow(int index) {
        if(browserLevel>0 && index==0) {browserLevel--;showBrowser();return;}
        int position=index-(browserLevel>0?1:0);
        if(browserLevel==0) {selectedArtist=rows.get(index);browserLevel=1;showBrowser();}
        else if(browserLevel==1) {selectedAlbum=rows.get(index);browserLevel=2;showBrowser();}
        else if(position>=0 && position<visibleSongs.size()) start(visibleSongs.get(position));
    }
    private String loadChart(Uri uri) throws IOException {
        try(InputStream in=getContentResolver().openInputStream(uri)) {
            if(in==null)throw new IOException("Partitura inacessível");
            ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int count;
            while((count=in.read(buf))!=-1) {if(out.size()>1024*1024)throw new IOException("Partitura acima de 1 MB");out.write(buf,0,count);}
            byte[] raw=out.toByteArray();
            if(raw.length>=2 && (raw[0]&0xff)==0xff && (raw[1]&0xff)==0xfe)
                return new String(raw,2,raw.length-2,java.nio.charset.StandardCharsets.UTF_16LE);
            return new String(raw,StandardCharsets.UTF_8);
        }
    }
    private void start(Song song) {
        stopPlaybackOnly();activeSong=song;chart=null;score=null;scoreOffsetMs=0;
        if(song.txt!=null) {
            try {chart=UltraStar.parse(loadChart(song.txt));score=new ScoreEngine(chart,singer);}
            catch(Exception ex) {Toast.makeText(this,"UltraStar inválido: "+ex.getMessage(),Toast.LENGTH_LONG).show();}
        }
        browser.setVisibility(View.GONE);playScreen.setVisibility(View.VISIBLE);
        scoreText.setText(song.artist+" — "+song.title+(chart==null?"  |  sem pontuação":"  |  ative o microfone"));
        caption.setText("");
        isVideo=Arrays.asList("mp4","mkv","webm").contains(ext(song.mediaName));
        try {
            if(isVideo) {
                video.setVisibility(View.VISIBLE);video.setVideoURI(song.media);
                video.setOnCompletionListener(mp->finished());
                video.setOnErrorListener((mp,what,extra)->{Toast.makeText(this,"Codec de vídeo não suportado neste aparelho",Toast.LENGTH_LONG).show();finished();return true;});
                video.start();
            } else {
                video.setVisibility(View.GONE);
                audio=new MediaPlayer();audio.setDataSource(this,song.media);
                audio.setOnCompletionListener(mp->finished());audio.prepare();audio.start();
            }
            playing=true;startElapsedMs=SystemClock.elapsedRealtime();pausedTotal=0;
            handler.removeCallbacks(ticker);handler.post(ticker);
            if(score!=null) {
                if(checkSelfPermission(Manifest.permission.RECORD_AUDIO)==PackageManager.PERMISSION_GRANTED)startMic();
                else requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},REQUEST_MIC);
            }
            controls.getChildAt(0).requestFocus();
        } catch(Exception ex) {Toast.makeText(this,"Não foi possível reproduzir: "+ex.getMessage(),Toast.LENGTH_LONG).show();stop();}
    }
    private void startMic() {
        if(!playing||score==null||microphone!=null)return;
        try {microphone=new Microphone(score,this::positionMs);microphone.start();}
        catch(Exception ex) {scoreText.setText("Microfone indisponível: "+ex.getMessage());}
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results) {
        super.onRequestPermissionsResult(request,permissions,results);
        if(request==REQUEST_MIC && results.length>0 && results[0]==PackageManager.PERMISSION_GRANTED)startMic();
        else if(request==REQUEST_MIC)scoreText.setText("Sem permissão de microfone: reprodução sem pontuação");
    }
    private long positionMs() {
        try {
            if(isVideo && video!=null)return video.getCurrentPosition();
            if(audio!=null)return audio.getCurrentPosition();
        } catch(Exception ignored){}
        return Math.max(0,SystemClock.elapsedRealtime()-startElapsedMs-pausedTotal);
    }
    private void togglePause() {
        if(!playing)return;
        boolean isPlaying=isVideo?video.isPlaying():audio!=null&&audio.isPlaying();
        if(isPlaying){if(isVideo)video.pause();else audio.pause();if(microphone!=null)microphone.setPaused(true);}
        else {if(isVideo)video.start();else audio.start();if(microphone!=null)microphone.setPaused(false);}
    }
    private void finished() {
        if(!playing)return;
        double finalScore=score==null?-1:score.score();stopPlaybackOnly();
        new android.app.AlertDialog.Builder(this).setTitle("Fim da música")
          .setMessage(finalScore<0?"Faixa concluída (sem partitura UltraStar).":String.format(Locale.getDefault(),"Sua pontuação: %.1f / 100",finalScore))
          .setPositiveButton("Voltar",(dialog,which)->showBrowser()).setCancelable(false).show();
    }
    private void stopPlaybackOnly() {
        playing=false;handler.removeCallbacks(ticker);
        if(microphone!=null){microphone.close();microphone=null;}
        if(video!=null){try{video.stopPlayback();}catch(Exception ignored){}}
        if(audio!=null){try{audio.stop();}catch(Exception ignored){}audio.release();audio=null;}
    }
    private void stop() {stopPlaybackOnly();showBrowser();}
    @Override public void onBackPressed() {
        if(playing){stop();return;}
        if(browserLevel>0){browserLevel--;showBrowser();return;}
        super.onBackPressed();
    }
    @Override protected void onDestroy(){stopPlaybackOnly();executor.shutdownNow();super.onDestroy();}
    /** Detect on 2048-sample windows, 50%-overlap; AudioRecord timestamps follow media position. */
    private static final class Microphone implements AutoCloseable {
        interface Clock {long position();}
        private final ScoreEngine scorer;private final Clock clock;
        private AudioRecord recorder;private Thread worker;
        private volatile boolean running=false,paused=false;
        Microphone(ScoreEngine scorer,Clock clock) {this.scorer=scorer;this.clock=clock;}
        void start() {
            int rate=22050, window=2048;
            int min=AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT);
            if(min<=0)throw new IllegalStateException("AudioRecord indisponível");
            recorder=new AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION,rate,AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,Math.max(min,window*4));
            if(recorder.getState()!=AudioRecord.STATE_INITIALIZED){recorder.release();recorder=null;throw new IllegalStateException("Sem entrada de áudio");}
            recorder.startRecording();running=true;
            worker=new Thread(()->{
                short[] sliding=new short[window];short[] half=new short[window/2];
                int filled=0;
                while(running) {
                    int read=recorder.read(half,0,half.length);
                    if(read<=0)continue;
                    if(read==half.length) {
                        if(filled<window/2) {System.arraycopy(half,0,sliding,0,read);filled+=read;continue;}
                        System.arraycopy(sliding,window/2,sliding,0,window/2);
                        System.arraycopy(half,0,sliding,window/2,read);
                        if(!paused)scorer.feed(clock.position(),Yin.detect(sliding,window,rate),1000d*read/rate);
                    }
                }
            },"KaraokeTV-Microfone");worker.setDaemon(true);worker.start();
        }
        void setPaused(boolean paused){this.paused=paused;}
        @Override public void close(){running=false;
            if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}if(worker!=null)try{worker.join(600);}catch(InterruptedException ignored){Thread.currentThread().interrupt();}recorder.release();recorder=null;}
        }
    }
}
