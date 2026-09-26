# Karaoke Multiplataforma

Três versões do projeto:

- `01_Console_NET`: console C# .NET 8 com pontuação pelo microfone.
- `02_Instalador_Windows`: scripts de instalação e criação de instalador para Windows.
- `03_Android_TV`: aplicativo Android TV nativo com projeto Gradle.

O workflow em `.github/workflows/build-apk.yml` permite gerar o APK Android pelo GitHub Actions. Os projetos são código-fonte, não EXE/APK previamente compilados. Não inclui músicas comerciais.
