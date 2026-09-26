using System.Collections.Concurrent;
using System.Diagnostics;
using System.Globalization;
using System.Text;
using NAudio.Wave;

namespace KaraokeApp;

internal static class Program
{
    private static readonly HashSet<string> MediaExtensions = new(StringComparer.OrdinalIgnoreCase)
    { ".mp3", ".wav", ".flac", ".mp4", ".mkv" };
    private static readonly StringComparer Ci = StringComparer.OrdinalIgnoreCase;

    private static int Main(string[] args)
    {
        Console.OutputEncoding = Encoding.UTF8;
        try
        {
            if (!OperatingSystem.IsWindows())
                throw new PlatformNotSupportedException("A captura com NAudio WaveInEvent requer Windows.");
            var options = Options.Parse(args);
            if (options.Help) { PrintHelp(); return 0; }
            if (options.ListMicrophones)
            {
                for (int i = 0; i < WaveInEvent.DeviceCount; i++)
                    Console.WriteLine($"[{i}] {WaveInEvent.GetCapabilities(i).ProductName}");
                return 0;
            }
            var root = options.Root ?? AskRoot();
            if (!Directory.Exists(root)) throw new DirectoryNotFoundException(root);
            var library = MediaScanner.Scan(root);
            if (library.Count == 0) { Console.WriteLine("Nenhuma mídia encontrada."); return 0; }
            while (true)
            {
                var artists = library.GroupBy(x => x.Artist, Ci)
                    .OrderBy(x => x.Key, Ci).ToArray();
                Console.WriteLine("\n===== KARAOKE APP =====");
                for (int i = 0; i < artists.Length; i++)
                    Console.WriteLine($"{i + 1,3}. {artists[i].Key} ({artists[i].Count()} faixas)");
                int artistIndex = AskIndex("Artista (0 para sair): ", artists.Length);
                if (artistIndex < 0) break;
                var albums = artists[artistIndex].GroupBy(x => x.Album, Ci)
                    .OrderBy(x => x.Key, Ci).ToArray();
                for (int i = 0; i < albums.Length; i++)
                    Console.WriteLine($"{i + 1,3}. {albums[i].Key} ({albums[i].Count()} faixas)");
                int albumIndex = AskIndex("Álbum (0 para voltar): ", albums.Length);
                if (albumIndex < 0) continue;
                var songs = albums[albumIndex].OrderBy(x => x.Title, Ci).ToArray();
                for (int i = 0; i < songs.Length; i++)
                    Console.WriteLine($"{i + 1,3}. {songs[i].Title}{(songs[i].Chart is null ? " [sem pontuação]" : " [UltraStar]")}");
                int songIndex = AskIndex("Faixa (0 para voltar): ", songs.Length);
                if (songIndex < 0) continue;
                Play(songs[songIndex], options);
            }
            return 0;
        }
        catch (Exception ex)
        {
            Console.Error.WriteLine($"Erro: {ex.Message}");
            return 1;
        }
    }

    private static void Play(MediaSong song, Options options)
    {
        if (!File.Exists(song.MediaPath))
        {
            Console.WriteLine("Arquivo de mídia não encontrado: " + song.MediaPath);
            return;
        }
        UltraStarChart? chart = null;
        if (song.Chart != null)
        {
            try { chart = UltraStarParser.Parse(song.Chart); }
            catch (Exception ex) { Console.WriteLine("Partitura inválida: " + ex.Message); }
        }
        string? singer = options.Singer;
        if (chart != null)
        {
            var singers = chart.Notes.Select(x => x.Singer).Distinct(Ci).ToArray();
            if (singers.Length > 1 && singer == null)
            {
                Console.WriteLine("Dueto detectado: " + string.Join(", ", singers));
                Console.Write("Escolha a voz a pontuar [P1]: ");
                singer = Console.ReadLine()?.Trim();
                if (string.IsNullOrWhiteSpace(singer)) singer = "P1";
            }
            singer ??= singers.FirstOrDefault() ?? "P1";
            if (!singers.Contains(singer, Ci))
            {
                Console.WriteLine($"Voz '{singer}' indisponível. Disponíveis: {string.Join(", ", singers)}");
                return;
            }
        }
        Console.WriteLine($"\nReproduzindo: {song.Artist} — {song.Title}");
        Console.WriteLine("Use fones de ouvido para impedir que a música entre no microfone.");
        Console.WriteLine("Pressione Q para parar e voltar ao catálogo.");
        if (chart != null)
            Console.WriteLine($"Referência UltraStar: {chart.Notes.Count} notas; voz {singer}. " +
                "A pontuação começa após calibração das primeiras notas válidas.");
        if (chart != null && (options.Mic < 0 || options.Mic >= WaveInEvent.DeviceCount))
        {
            Console.WriteLine("Microfone inválido. Execute com --list-mics para escolher outro.");
            return;
        }
        ScoreEngine? engine = chart is null ? null : new ScoreEngine(chart, singer!, options.OffsetMs);
        using var capture = engine is null ? null : new MicrophoneCapture(options.Mic, engine);
        using var playback = Ffplay.Start(song.MediaPath, options.Ffplay);
        var playbackClock = Stopwatch.StartNew();
        if (capture is not null) capture.Start(playbackClock);
        try
        {
            while (!playback.HasExited)
            {
                if (Console.KeyAvailable && Console.ReadKey(intercept: true).Key == ConsoleKey.Q)
                { playback.Stop(); break; }
                if (engine != null)
                {
                    var snapshot = engine.Snapshot();
                    string lyric = chart!.ActiveLyric(playbackClock.Elapsed.TotalMilliseconds + options.OffsetMs, singer!);
                    Console.Write($"\r{playbackClock.Elapsed:mm\\:ss} | Nota: {snapshot.Frequency,6:0} Hz | " +
                        $"Afinação: {snapshot.Score,5:0.0}/100 | {Truncate(lyric, 36),-36} ");
                }
                else Console.Write($"\r{playbackClock.Elapsed:mm\\:ss} | Reprodução de mídia...        ");
                Thread.Sleep(120);
            }
        }
        finally { capture?.Stop(); }
        Console.WriteLine();
        if (engine != null)
        {
            var result = engine.Finish();
            Console.WriteLine($"RESULTADO: {result.Score:0.0}/100 | Frames elegíveis: " +
                $"{result.Evaluated} | Frames cantados: {result.Voiced} | " +
                $"Deslocamento tonal: {result.Transposition:+0;-0;0} semitons (módulo 12).");
        }
    }

    private static string Truncate(string value, int max) => value.Length <= max ? value : value[..(max - 1)] + "…";
    private static string AskRoot()
    {
        Console.Write("Pasta raiz das músicas: ");
        return (Console.ReadLine() ?? "").Trim().Trim('"');
    }
    private static int AskIndex(string prompt, int count)
    {
        while (true)
        {
            Console.Write(prompt);
            var value = Console.ReadLine();
            if (int.TryParse(value, out int n) && n >= 0 && n <= count) return n - 1;
            Console.WriteLine("Opção inválida.");
        }
    }
    private static void PrintHelp()
    {
        Console.WriteLine("""
            KaraokeApp (.NET 8, Windows)
            dotnet run -- --root "C:\\Karaoke" [--mic 0] [--offset-ms 0] [--singer P1]
            dotnet run -- --list-mics
            Opções: --root PASTA | --mic ÍNDICE | --offset-ms N | --singer P1/P2
                    --ffplay CAMINHO | --list-mics | --help
            Requer ffplay.exe (FFmpeg) no PATH ou --ffplay; pressione Q para parar.
            """);
    }

    private sealed record Options(string? Root, int Mic, double OffsetMs, string? Singer,
        string Ffplay, bool ListMicrophones, bool Help)
    {
        public static Options Parse(string[] args)
        {
            string? root = null, singer = null;
            string ffplay = "ffplay";
            int mic = 0;
            double offset = 0;
            bool list = false, help = false;
            for (int i = 0; i < args.Length; i++)
            {
                string Next() => ++i < args.Length ? args[i] : throw new ArgumentException("Falta valor para " + args[i - 1]);
                switch (args[i].ToLowerInvariant())
                {
                    case "--root": root = Next(); break;
                    case "--mic": mic = int.Parse(Next(), CultureInfo.InvariantCulture); break;
                    case "--offset-ms": offset = double.Parse(Next(), CultureInfo.InvariantCulture); break;
                    case "--singer": singer = Next().ToUpperInvariant(); break;
                    case "--ffplay": ffplay = Next(); break;
                    case "--list-mics": list = true; break;
                    case "--help": case "-h": help = true; break;
                    default: throw new ArgumentException("Opção desconhecida: " + args[i]);
                }
            }
            return new(root, mic, offset, singer, ffplay, list, help);
        }
    }

    private sealed record MediaSong(string Artist, string Album, string Title,
        string MediaPath, string? Chart);

    private static class MediaScanner
    {
        public static List<MediaSong> Scan(string root)
        {
            var media = new Dictionary<string, MediaSong>(Ci);
            IEnumerable<string> files = EnumerateSafe(root);
            var paths = files.ToArray();
            foreach (var file in paths.Where(f => MediaExtensions.Contains(Path.GetExtension(f))))
            {
                var folder = Path.GetDirectoryName(file)!;
                var relative = Path.GetRelativePath(root, folder).Split(Path.DirectorySeparatorChar,
                    StringSplitOptions.RemoveEmptyEntries);
                string artist = relative.Length > 0 && relative[0] != "." ? relative[0] : "Artista desconhecido";
                string album = relative.Length > 1 ? relative[1] : "Álbum desconhecido";
                media[file] = new(artist, album, Path.GetFileNameWithoutExtension(file), file, null);
            }
            foreach (var txt in paths.Where(f => Path.GetExtension(f).Equals(".txt", StringComparison.OrdinalIgnoreCase)))
            {
                UltraStarChart chart;
                try { chart = UltraStarParser.Parse(txt); }
                catch { continue; } // outros arquivos .txt não são partituras válidas
                var folder = Path.GetDirectoryName(txt)!;
                string? associated = ResolveSongMedia(folder, chart.Metadata, media.Keys, txt);
                if (associated == null) continue;
                var original = media[associated];
                string artist = chart.Get("ARTIST") ?? original.Artist;
                string title = chart.Get("TITLE") ?? original.Title;
                string album = chart.Get("ALBUM") ?? original.Album;
                media[associated] = original with { Artist = artist, Album = album, Title = title, Chart = txt };
            }
            return media.Values.OrderBy(x => x.Artist, Ci).ThenBy(x => x.Album, Ci)
                .ThenBy(x => x.Title, Ci).ToList();
        }
        private static string? ResolveSongMedia(string folder, Dictionary<string, string> headers,
            ICollection<string> available, string chartPath)
        {
            // Preferência por vídeo: permite exibir clipe com legendas embutidas.
            foreach (string header in new[] { "VIDEO", "MP3", "AUDIO" })
            {
                if (!headers.TryGetValue(header, out var name)) continue;
                string candidate = Path.GetFullPath(Path.Combine(folder, name.Trim().Trim('"')));
                var match = available.FirstOrDefault(x => Ci.Equals(x, candidate));
                if (match != null) return match;
            }
            string stem = Path.GetFileNameWithoutExtension(chartPath);
            var sameName = available.FirstOrDefault(x => Ci.Equals(Path.GetDirectoryName(x), folder)
                && Ci.Equals(Path.GetFileNameWithoutExtension(x), stem));
            return sameName ?? available.FirstOrDefault(x => Ci.Equals(Path.GetDirectoryName(x), folder));
        }
        private static IEnumerable<string> EnumerateSafe(string root)
        {
            var pending = new Stack<string>();
            pending.Push(root);
            while (pending.Count != 0)
            {
                string dir = pending.Pop();
                string[] files, folders;
                try { files = Directory.GetFiles(dir); folders = Directory.GetDirectories(dir); }
                catch (UnauthorizedAccessException) { continue; }
                catch (IOException) { continue; }
                foreach (var f in files) yield return f;
                foreach (var child in folders)
                {
                    try
                    {
                        if ((File.GetAttributes(child) & FileAttributes.ReparsePoint) == 0) pending.Push(child);
                    }
                    catch (IOException) { }
                    catch (UnauthorizedAccessException) { }
                }
            }
        }
    }

    private sealed record SingingNote(double StartMs, double EndMs, int Pitch,
        string Text, char Type, string Singer)
    {
        public bool Scored => Type is ':' or '*';
        public double Weight => Type == '*' ? 2 : 1;
    }

    private sealed class UltraStarChart
    {
        public required Dictionary<string, string> Metadata { get; init; }
        public required List<SingingNote> Notes { get; init; }
        public string? Get(string name) => Metadata.GetValueOrDefault(name);
        public string ActiveLyric(double atMs, string singer)
        {
            var n = Notes.FirstOrDefault(n => Ci.Equals(n.Singer, singer)
                && atMs >= n.StartMs - 100 && atMs < n.EndMs + 200);
            return n?.Text.Trim() ?? "";
        }
    }

    private static class UltraStarParser
    {
        private sealed record BeatTempo(double Beat, double Bpm);
        public static UltraStarChart Parse(string path)
        {
            var lines = File.ReadAllLines(path);
            var headers = new Dictionary<string, string>(Ci);
            foreach (var raw in lines)
            {
                var line = raw.Trim();
                if (line.StartsWith('#'))
                {
                    int pos = line.IndexOf(':');
                    if (pos > 1) headers[line[1..pos].Trim()] = line[(pos + 1)..].Trim();
                }
            }
            if (!headers.TryGetValue("BPM", out var bpmText) || !Number(bpmText, out var initialBpm) || initialBpm <= 0)
                throw new FormatException("Cabeçalho #BPM ausente ou inválido.");
            double gap = headers.TryGetValue("GAP", out var gapText) && Number(gapText, out var g) ? g : 0;
            bool relative = headers.GetValueOrDefault("RELATIVE")?.Equals("YES", StringComparison.OrdinalIgnoreCase) == true;
            var notes = new List<SingingNote>();
            var tempos = new List<BeatTempo> { new(0, initialBpm) };
            // Mudanças de BPM referem-se a beats ABSOLUTOS da faixa.
            foreach (var raw in lines)
            {
                var line = raw.TrimStart();
                if (!line.StartsWith("B ", StringComparison.OrdinalIgnoreCase)) continue;
                string[] p = line.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries);
                if (p.Length >= 3 && Number(p[1], out var beat) && Number(p[2], out var bpm) && bpm > 0)
                    tempos.Add(new(beat, bpm));
            }
            tempos = tempos.OrderBy(t => t.Beat).ToList();
            string singer = "P1";
            var relOffsets = new Dictionary<string, double>(Ci) { ["P1"] = 0, ["P2"] = 0 };
            foreach (var raw in lines)
            {
                var line = raw.TrimStart();
                if (line.Length == 0 || line.StartsWith('#')) continue;
                if (line.Equals("E", StringComparison.OrdinalIgnoreCase)) break;
                if (line.StartsWith("P1", StringComparison.OrdinalIgnoreCase) ||
                    line.StartsWith("P2", StringComparison.OrdinalIgnoreCase))
                {
                    if (line.Length == 2 || char.IsWhiteSpace(line[2]))
                        singer = line[..2].ToUpperInvariant();
                    continue;
                }
                char type = char.ToUpperInvariant(line[0]);
                if (type == 'B') continue;
                if (type == '-')
                {
                    // RELATIVE: "- beat offset" atualiza o deslocamento da linha seguinte.
                    var fields = line.Split((char[]?)null, StringSplitOptions.RemoveEmptyEntries);
                    if (relative && fields.Length >= 3 && Number(fields[2], out var delta))
                        relOffsets[singer] = relOffsets.GetValueOrDefault(singer) + delta;
                    continue;
                }
                if (type is not (':' or '*' or 'F' or 'R' or 'G')) continue;
                var columns = line.Split((char[]?)null, 5, StringSplitOptions.RemoveEmptyEntries);
                if (columns.Length < 4 || !Number(columns[1], out var beat)
                    || !Number(columns[2], out var duration) || !int.TryParse(columns[3],
                        NumberStyles.Integer, CultureInfo.InvariantCulture, out var pitch)
                    || duration <= 0) continue;
                if (relative) beat += relOffsets.GetValueOrDefault(singer);
                var start = gap + BeatToMilliseconds(beat, tempos);
                var end = gap + BeatToMilliseconds(beat + duration, tempos);
                if (end > start) notes.Add(new(start, end, pitch,
                    columns.Length == 5 ? columns[4] : "", type, singer));
            }
            if (notes.Count == 0) throw new FormatException("Arquivo UltraStar sem notas válidas.");
            return new UltraStarChart { Metadata = headers,
                Notes = notes.OrderBy(n => n.StartMs).ToList() };
        }
        private static bool Number(string s, out double result) => double.TryParse(
            s.Replace(',', '.'), NumberStyles.Float, CultureInfo.InvariantCulture, out result);
        private static double BeatToMilliseconds(double beat, List<BeatTempo> tempos)
        {
            // O BPM UltraStar já representa unidades de grade (1 beat = 60/BPM segundos).
            // Para beats negativos, extrapola o primeiro tempo.
            var current = tempos[0];
            double ms = beat < current.Beat ? (beat - current.Beat) * 60_000 / current.Bpm : 0;
            if (beat < current.Beat) return ms;
            for (int i = 1; i < tempos.Count; i++)
            {
                var next = tempos[i];
                if (next.Beat <= current.Beat) { current = next; continue; }
                double stop = Math.Min(beat, next.Beat);
                if (stop > current.Beat) ms += (stop - current.Beat) * 60_000 / current.Bpm;
                if (beat < next.Beat) return ms;
                current = next;
            }
            return ms + Math.Max(0, beat - current.Beat) * 60_000 / current.Bpm;
        }
    }

    private sealed class Ffplay : IDisposable
    {
        private readonly Process _process;
        private Ffplay(Process process) => _process = process;
        public bool HasExited => _process.HasExited;
        public static Ffplay Start(string file, string executable)
        {
            var info = new ProcessStartInfo(executable)
            {
                UseShellExecute = false, CreateNoWindow = false,
                RedirectStandardInput = false
            };
            foreach (string arg in new[] { "-hide_banner", "-loglevel", "error", "-autoexit",
                "-window_title", "KaraokeApp — Q no console para parar", file })
                info.ArgumentList.Add(arg);
            try { return new Ffplay(Process.Start(info) ?? throw new IOException("ffplay não iniciou.")); }
            catch (System.ComponentModel.Win32Exception ex)
            {
                throw new IOException("Não foi possível iniciar ffplay. Instale o FFmpeg e adicione ffplay.exe ao PATH " +
                    "ou informe --ffplay CAMINHO. " + ex.Message, ex);
            }
        }
        public void Stop()
        {
            if (!_process.HasExited) _process.Kill(entireProcessTree: true);
        }
        public void Dispose() { Stop(); _process.Dispose(); }
    }

    private sealed class MicrophoneCapture : IDisposable
    {
        private const int SampleRate = 44100;
        private const int Downsample = 4;
        private const int Window = 2048;
        private const int Hop = 256;
        private readonly WaveInEvent _input;
        private readonly ScoreEngine _score;
        private readonly ConcurrentQueue<(float[] Samples, double TimeMs)> _queue = new();
        private readonly CancellationTokenSource _stop = new();
        private readonly float[] _ring = new float[Window];
        private readonly List<float> _pending = new();
        private readonly object _clockLock = new();
        private Stopwatch? _clock;
        private Task? _worker;
        private bool _started;
        private int _written, _sinceLast;
        private readonly Yin _yin = new(SampleRate / Downsample, Window, 75, 1000);
        public MicrophoneCapture(int mic, ScoreEngine score)
        {
            _score = score;
            _input = new WaveInEvent { DeviceNumber = mic,
                WaveFormat = new WaveFormat(SampleRate, 16, 1), BufferMilliseconds = 40 };
            _input.DataAvailable += OnData;
            _input.RecordingStopped += (_, args) =>
            {
                if (args.Exception != null) Console.Error.WriteLine("Microfone: " + args.Exception.Message);
            };
        }
        public void Start(Stopwatch clock)
        {
            lock (_clockLock) _clock = clock;
            _worker = Task.Run(Analyze);
            _input.StartRecording();
            _started = true;
        }
        private void OnData(object? sender, WaveInEventArgs e)
        {
            if (_stop.IsCancellationRequested) return;
            // A duração gravada é estimada pelo relógio de áudio, não pelo jitter do callback.
            // Compensa o atraso inicial do dispositivo via hora real do pacote recebido.
            double receivedAtMs;
            lock (_clockLock) receivedAtMs = _clock?.Elapsed.TotalMilliseconds ?? 0;
            int samples = e.BytesRecorded / 2;
            var mono = new float[samples];
            for (int i = 0; i < samples; i++)
            {
                short value = (short)(e.Buffer[2 * i] | e.Buffer[2 * i + 1] << 8);
                mono[i] = value / 32768f;
            }
            // O primeiro frame é ancorado ao instante do callback com buffer de ~40 ms.
            _queue.Enqueue((mono, receivedAtMs - 20));
        }
        private async Task Analyze()
        {
            while (!_stop.IsCancellationRequested || !_queue.IsEmpty)
            {
                if (!_queue.TryDequeue(out var packet))
                {
                    try { await Task.Delay(5, _stop.Token); }
                    catch (OperationCanceledException) { }
                    continue;
                }
                for (int i = 0; i < packet.Samples.Length; i++) _pending.Add(packet.Samples[i]);
                int consumed = 0;
                while (_pending.Count - consumed >= Downsample)
                {
                    float averaged = 0;
                    for (int j = 0; j < Downsample; j++) averaged += _pending[consumed + j];
                    consumed += Downsample;
                    _ring[_written % Window] = averaged / Downsample;
                    _written++;
                    _sinceLast++;
                    if (_written < Window || _sinceLast < Hop) continue;
                    _sinceLast = 0;
                    var window = new float[Window];
                    for (int k = 0; k < Window; k++) window[k] = _ring[(_written + k) % Window];
                    var pitch = _yin.Detect(window);
                    // Janela centrada no passado: 2048/11025 ~= 186 ms.
                    // Índice final relativo ao fim do pacote atual.
                    double remainingMs = (_pending.Count - consumed) * 1000.0 / SampleRate;
                    double centerMs = packet.TimeMs - remainingMs - Window * 500.0 / (SampleRate / Downsample);
                    _score.AddFrame(centerMs, pitch.Frequency, pitch.Confidence);
                }
                if (consumed > 0) _pending.RemoveRange(0, consumed);
            }
        }
        public void Stop()
        {
            if (_started) { _input.StopRecording(); _started = false; }
            if (!_stop.IsCancellationRequested) _stop.Cancel();
            try { _worker?.Wait(TimeSpan.FromSeconds(3)); }
            catch (AggregateException) { }
        }
        public void Dispose()
        {
            Stop(); _input.Dispose(); _stop.Dispose();
        }
    }

    private readonly record struct PitchResult(double Frequency, double Confidence);
    private sealed class Yin
    {
        private readonly double _rate;
        private readonly int _size, _minimum, _maximum;
        private readonly double[] _difference, _normalized;
        public Yin(double sampleRate, int window, double minHz, double maxHz)
        {
            _rate = sampleRate; _size = window;
            _minimum = Math.Max(2, (int)Math.Floor(sampleRate / maxHz));
            _maximum = Math.Min(window / 2 - 1, (int)Math.Ceiling(sampleRate / minHz));
            _difference = new double[_maximum + 2];
            _normalized = new double[_maximum + 2];
        }
        public PitchResult Detect(float[] input)
        {
            double energy = 0, mean = 0;
            foreach (float x in input) { energy += x * x; mean += x; }
            mean /= _size;
            double rms = Math.Sqrt(Math.Max(0, energy / _size - mean * mean));
            if (rms < .012) return default;
            for (int tau = 1; tau <= _maximum; tau++)
            {
                double sum = 0;
                for (int j = 0; j < _size - _maximum; j++)
                {
                    double d = (input[j] - mean) - (input[j + tau] - mean);
                    sum += d * d;
                }
                _difference[tau] = sum;
            }
            _normalized[0] = 1;
            double cumulative = 0;
            for (int tau = 1; tau <= _maximum; tau++)
            {
                cumulative += _difference[tau];
                _normalized[tau] = cumulative > 1e-12 ? _difference[tau] * tau / cumulative : 1;
            }
            int best = -1;
            for (int tau = _minimum; tau <= _maximum; tau++)
            {
                if (_normalized[tau] >= .13) continue;
                while (tau + 1 <= _maximum && _normalized[tau + 1] < _normalized[tau]) tau++;
                best = tau;
                break;
            }
            if (best < 0) return default; // Evita inventar nota em fala/ruído sem periodicidade.
            double peak = best;
            if (best > 1 && best < _maximum)
            {
                double a = _normalized[best - 1], b = _normalized[best], c = _normalized[best + 1];
                double den = 2 * (2 * b - a - c);
                if (Math.Abs(den) > 1e-9) peak += (c - a) / den;
            }
            double frequency = _rate / peak;
            return frequency is >= 75 and <= 1000
                ? new PitchResult(frequency, Math.Clamp(1 - _normalized[best], 0, 1)) : default;
        }
    }

    private sealed class ScoreEngine
    {
        private readonly object _sync = new();
        private readonly SingingNote[] _notes;
        private readonly double _offsetMs;
        private readonly double[] _calibration = new double[12];
        private readonly List<(int Expected, double Midi, double Confidence, double Weight)> _buffer = new();
        private int _calibrationSamples, _transpose;
        private bool _locked;
        private double _earned, _possible, _lastHz;
        private int _evaluated, _voiced;
        private const double ToleranceSemitones = .35;
        public ScoreEngine(UltraStarChart chart, string singer, double offsetMs)
        {
            _notes = chart.Notes.Where(n => Ci.Equals(n.Singer, singer) && n.Scored)
                .OrderBy(n => n.StartMs).ToArray();
            _offsetMs = offsetMs;
        }
        public void AddFrame(double timestampMs, double frequency, double confidence)
        {
            lock (_sync)
            {
                double at = timestampMs + _offsetMs;
                if (at < 0) return;
                var note = FindNote(at);
                if (note == null) return;
                _lastHz = frequency;
                double midi = frequency > 0 && confidence >= .7
                    ? 69 + 12 * Math.Log2(frequency / 440) : double.NaN;
                // UltraStar pitch 0 == C4 (MIDI 60); offset é calibrado uma vez.
                int expected = note.Pitch + 60;
                if (!_locked)
                {
                    _buffer.Add((expected, midi, confidence, note.Weight));
                    if (!double.IsNaN(midi))
                    {
                        _calibrationSamples++;
                        for (int shift = 0; shift < 12; shift++)
                        {
                            double difference = CircularDistance(midi, expected + shift);
                            _calibration[shift] += Math.Max(0, 1 - difference / 1.5) * confidence;
                        }
                    }
                    if (_calibrationSamples >= 18) LockCalibration();
                }
                else Evaluate(expected, midi, note.Weight);
            }
        }
        private SingingNote? FindNote(double ms)
        {
            int lo = 0, hi = _notes.Length - 1;
            while (lo <= hi)
            {
                int mid = lo + (hi - lo) / 2;
                var n = _notes[mid];
                if (ms < n.StartMs) hi = mid - 1;
                else if (ms >= n.EndMs) lo = mid + 1;
                else return n;
            }
            return null;
        }
        private static double CircularDistance(double a, double b)
        {
            double d = Math.Abs((a - b) % 12);
            return Math.Min(d, 12 - d);
        }
        private void LockCalibration()
        {
            _transpose = Array.IndexOf(_calibration, _calibration.Max());
            _locked = true;
            foreach (var f in _buffer) Evaluate(f.Expected, f.Midi, f.Weight);
            _buffer.Clear();
        }
        private void Evaluate(int expected, double midi, double weight)
        {
            _evaluated++;
            _possible += weight;
            if (double.IsNaN(midi)) return; // silêncio / baixa confiança = zero
            _voiced++;
            double error = CircularDistance(midi, expected + _transpose);
            // Até ±0,35 semitons é acerto cheio; queda linear até ±2 semitons.
            double fraction = error <= ToleranceSemitones ? 1
                : Math.Max(0, 1 - (error - ToleranceSemitones) / (2 - ToleranceSemitones));
            _earned += weight * fraction;
        }
        public (double Score, double Frequency) Snapshot()
        {
            lock (_sync) return (_possible > 0 ? 100 * _earned / _possible : 0, _lastHz);
        }
        public (double Score, int Evaluated, int Voiced, int Transposition) Finish()
        {
            lock (_sync)
            {
                if (!_locked && _buffer.Count > 0) LockCalibration();
                return (_possible > 0 ? Math.Clamp(100 * _earned / _possible, 0, 100) : 0,
                    _evaluated, _voiced, _transpose);
            }
        }
    }
}
