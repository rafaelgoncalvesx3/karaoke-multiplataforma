# KaraokeApp — Catálogo, player e pontuação UltraStar

Aplicação de **console .NET 8 para Windows**, com scanner recursivo, navegação Artista → Álbum → Faixa, reprodução por **ffplay** e pontuação de afinação pelo microfone com **NAudio + YIN**. Não inclui músicas comerciais nem as distribui.

## Pré-requisitos

1. Windows 10/11 ou Windows Server com um microfone autorizado em Configurações → Privacidade → Microfone.
2. [.NET 8 SDK](https://dotnet.microsoft.com/download/dotnet/8.0).
3. [FFmpeg](https://ffmpeg.org/download.html) com `ffplay.exe` no PATH; alternativamente passe `--ffplay "C:\\ffmpeg\\bin\\ffplay.exe"`. `ffplay` abre sua própria janela para vídeos; console para menus e pontuação. O FFmpeg não é empacotado neste ZIP.
4. Fones de ouvido (evita que os alto-falantes contaminem o sinal do microfone).

## Compilar e executar no PowerShell

```powershell
cd .\KaraokeApp_Completo
dotnet restore
dotnet build -c Release
dotnet run -- --list-mics
dotnet run -- --root "D:\\Karaoke" --mic 0 --offset-ms 0
```

`--offset-ms 150` adianta a referência em 150 ms em relação ao relógio capturado; `--offset-ms -150` atrasa. Corrija empiricamente a latência áudio/vídeo caso necessário. `--singer P2` seleciona a voz 2 de um dueto, quando presente. Pressione **Q** durante a reprodução para parar; digite **0** nos menus para voltar/sair.

```powershell
dotnet publish -c Release -r win-x64 --self-contained true -p:PublishSingleFile=true
```

Saída: `bin\Release\net8.0\win-x64\publish\KaraokeApp.exe`. Para compilação *framework-dependent*, omita `--self-contained true` e `PublishSingleFile`.

## Organização esperada

```text
D:\\Karaoke\\
  Artista A\\
    Álbum 1\\
      faixa.mp3
      faixa.txt
  Artista B\\
    Álbum 2\\
      clipe.mkv
      clipe.txt
```

Arquivos `.mp3`, `.wav`, `.flac`, `.mp4`, `.mkv` são indexados. Metadados `#ARTIST`, `#ALBUM`, `#TITLE` prevalecem sobre nomes das pastas; quando faltam, o primeiro diretório relativo é o artista e o segundo o álbum. O scanner tenta associar cada `.txt` por `#VIDEO`, `#MP3`/`#AUDIO`, mesmo nome ou primeiro arquivo de mídia no diretório. Se a partitura indicar um vídeo e um áudio separados, esta edição toca **apenas o primeiro arquivo resolvido**, sem mixagem externa de trilhas ou renderização sincronizada de letras; vídeos que já contêm áudio são reproduzidos normalmente.

## Pontuação e formato

O parser lê `#BPM`, `#GAP`, `#RELATIVE`, `#MP3`, `#AUDIO`, `#VIDEO`, mudanças `B`, separadores de frase `-`, marcadores de voz `P1/P2`, notas normais `:`, douradas `*`, freestyle `F` e rap `R/G`. `BPM` é a unidade de grade UltraStar: cada beat dura `60000/BPM` ms; **não** multiplique nem divida arbitrariamente por quatro. Em arquivos `#RELATIVE:YES`, `- beat deslocamento` atualiza o deslocamento da frase seguinte. O parser suporta o formato usual UltraStar TXT, não todas as extensões de todos os forks (por exemplo, duetos com horários sobrepostos na mesma voz ou efeitos proprietários).

Captura mono PCM 16-bit a 44,1 kHz, reduz para 11,025 kHz, calcula YIN a cada 256 amostras com janela de 2.048, limite 75–1000 Hz, confiança mínima 0,7 e limiar YIN de 0,13. Converte a frequência `f` para MIDI contínuo `69 + 12*log2(f/440)`; o pitch UltraStar `0` representa MIDI 60 (Dó central). **Calibração automática de deslocamento cromático** usa as primeiras 18 amostras com voz para compensar alterações de tom da faixa; depois a transposição fica fixa. A diferença é calculada módulo 12, portanto cantar na oitava acima ou abaixo não penaliza. Notas douradas pesam 2×; silêncio durante notas elegíveis vale zero; rap e freestyle não entram na média. A nota final é uma média ponderada entre 0 e 100 **dos frames processados em notas pontuáveis**, não uma medida clínica de qualidade vocal nem certificação de precisão acústica.

**Limitações:** sincronismo inicial do `ffplay` e drivers de microfone varia conforme o computador; ajuste `--offset-ms`. Um único microfone pontua uma voz por vez. O console não desenha letras sobre o vídeo; mostra a sílaba atual e a nota estimada. Se usar mídia musical com voz original audível no ambiente, o placar pode ser influenciado pela gravação. Sem `.txt` válido, toca mídia sem placar. O exemplo acompanha somente partitura; você pode fornecer seu próprio arquivo `escala.wav` para testá-la.

## Diagnóstico

- `ffplay` não encontrado: `ffplay -version` deve funcionar no PowerShell ou use `--ffplay`.
- Nenhum microfone: execute `dotnet run -- --list-mics`, conecte dispositivo e autorize acesso.
- Sem partitura associada: verifique `#MP3`/`#VIDEO`, pasta e `.txt` válido com `#BPM` e notas.
- Placar baixo mesmo com voz correta: use fones, verifique o GAP e ajuste `--offset-ms` em passos de 50 a 100 ms.

Fontes de especificação: [UltraStar File Format](https://github.com/UltraStar-Deluxe/format), [NAudio NuGet](https://www.nuget.org/packages/NAudio/2.2.1).
