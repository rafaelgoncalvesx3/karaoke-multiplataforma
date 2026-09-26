# Karaoke TV - Android TV Box

Aplicativo nativo em **Java/Android** adaptado da ideia do KaraokeApp para Windows. Compatível com **Android 8 (API 26) ou mais novo**; projetado para TV Box com controle remoto. O APK ainda precisa ser compilado em um PC com Android SDK ou pelo GitHub Actions.

## Recursos

- Escolha da biblioteca de mídia por seletor de pasta Android (Storage Access Framework), com permissão persistente de leitura, incluindo pastas em pendrive quando disponibilizadas pelo Android.
- Catálogo **Artista → Álbum → Música** ordenado alfabeticamente, segundo pastas `Musicas/Artista/Album/faixa.mp4`. Arquivos suportados no scanner: mp3, wav, flac, mp4, mkv, m4a, webm e ogg.
- Reprodução por MediaPlayer / VideoView com os **codecs realmente suportados pelo aparelho** (MKV/FLAC não são garantidos em todas as TV Boxes).
- Parser UltraStar TXT com BPM, GAP, notas simples/douradas/freestyle, dueto P1/P2, marcadores de linha e tempos em milissegundos.
- Microfone por AudioRecord (permissão solicitada durante uso), estimativa YIN com janela de 2048 amostras a 22.050 Hz; score cumulativo 0–100, tolerância de oitava, penalidade por silêncio e nota dourada x2.
- Botões de ajuste de sincronização de ±100 ms; interface paisagem navegável pelo controle da TV.

## Gerar o APK no Windows (Android Studio)

1. Instale **Android Studio** com **Android SDK 35**, **Build Tools 35.0.0** e JDK 17.
2. Abra esta pasta no Android Studio (`File > Open`). Aguarde a sincronização do Gradle (internet necessária na primeira execução).
3. Execute `Build > Build Bundle(s) / APK(s) > Build APK(s)` ou, com Gradle 8.9 e SDK configurados, `gradle :app:assembleDebug`.
4. O APK será gerado em `app/build/outputs/apk/debug/app-debug.apk`.
5. Copie o APK para um pendrive e instale na TV Box, liberando a instalação de apps externos se solicitado.

## Gerar APK sem configurar Android Studio (GitHub Actions)

1. Crie um repositório GitHub, envie o **conteúdo** desta pasta (incluindo `.github/workflows/build-apk.yml`) à branch `main`.
2. Entre em **Actions → Gerar APK Android TV → Run workflow** (ou aguarde a execução automática do push).
3. Depois do workflow terminar, baixe o artefato `KaraokeTV-Android-APK` na página da execução. Ele conterá `app-debug.apk`.

O APK debug é assinado automaticamente pelas ferramentas de desenvolvimento; para distribuir comercialmente, gere uma versão release assinada com sua chave.

## Exemplo de teste

A pasta `Samples/Exemplo/Album/` contém uma escala WAV e seu arquivo UltraStar TXT sincronizado. Copie a pasta `Exemplo` para sua biblioteca no pendrive para testar catálogo, áudio e pontuação.

## Como usar

1. Conecte um microfone **USB** reconhecido pelo Android (ou Bluetooth com perfil de microfone compatível). O microfone embutido do controle remoto pode não estar disponível como entrada permanente.
2. Copie suas mídias para uma pasta `Musicas/Artista/Album/`, com arquivos `.txt` UltraStar ao lado da mídia e com o mesmo nome-base.
3. Na primeira inicialização escolha a pasta raiz `Musicas`. Selecione artista, álbum, faixa pelo controle remoto.
4. Autorize o microfone para pontuar. Sem `.txt` ou sem microfone, o aplicativo reproduz sem pontuação.
5. Use **Parar** para voltar. O ajuste ±100 ms calibra eventuais diferenças entre áudio, letra e microfone.

### Limitações

- **Não é o mesmo binário C#** do Windows: Android TV exige captura e reprodução nativas. Este projeto usa Java Android SDK e não exige NAudio/FFplay.
- UltraStar em formatos não padronizados (tags incomuns, codificações exóticas, arquivos com tempo variável) pode exigir ajustes; TXT UTF-8 e UTF-16 LE são suportados.
- Codecs, permissões de pendrive e suporte a microfone variam conforme o modelo de TV Box. Bluetooth pode gerar latência; ajuste o offset.
- O placar é heurístico, não uma medição clínica/acústica certificada. Não há remoção de voz ou separação de instrumental.
- **Não foi compilado nem testado em TV Box neste ambiente**, porque não há Android SDK / Gradle nem acesso externo para baixá-los.
