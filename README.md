# StreamCast

Aplicativo Android de código aberto para compartilhar a tela do celular com uma TV compatível com Chromecast na mesma rede Wi-Fi.

## Por que este projeto existe

O StreamCast foi criado para resolver uma necessidade prática: um Xiaomi 13 Pro 5G não reconhecia uma TV TCL pelo método padrão de transmissão de tela do celular. O aplicativo encontra a TV diretamente na rede local e envia a tela usando Google Cast.

O projeto é útil quando o painel de transmissão integrado do Android não encontra uma TV TCL, Google TV ou outro receptor compatível com Chromecast.

## Recursos

- Descoberta automática de TVs e Chromecasts pela rede local.
- Modo HLS, com maior compatibilidade e alguns segundos de atraso.
- Modo WebRTC experimental, com baixa latência.
- Captura de tela usando `MediaProjection` e codificação H.264.
- Captura opcional do áudio reproduzido pelo celular.
- Logs persistentes para diagnóstico de quedas e falhas de conexão.
- Controle de volume pelos botões físicos do celular, inclusive em segundo plano.
- Controle remoto opcional para Android TV e Google TV.

## Como funciona

- **Descoberta:** o `NsdManager` encontra receptores Google Cast via mDNS (`_googlecast._tcp.local`).
- **Canal de controle:** conexão TLS com a porta 8009 usando o protocolo Cast V2 implementado em Kotlin.
- **Captura:** `MediaProjection` e `MediaCodec` capturam e codificam a tela em H.264.
- **HLS:** um servidor HTTP Ktor integrado fornece o stream ao receptor padrão do Chromecast. A latência normalmente fica entre 5 e 10 segundos.
- **WebRTC:** o aplicativo negocia uma conexão `RTCPeerConnection` com um receiver Cast personalizado para reduzir a latência.

## Modo WebRTC

O WebRTC é opcional e experimental. Ele usa o receiver personalizado hospedado pelo projeto e o App ID padrão `9098830C`.

Para melhorar a compatibilidade com TVs TCL e outros receptores Cast, o perfil padrão usa H.264 em 1080p30 e até 8 Mbps. Alguns dispositivos podem não renderizar corretamente 1080p60, mesmo quando a negociação WebRTC é concluída.

O receiver está em [`receiver/`](receiver/). Para hospedar uma versão própria, registre o endereço no [Google Cast Developer Console](https://cast.google.com/publish/) e informe o App ID no aplicativo.

Limitações do WebRTC:

- Um receptor por transmissão.
- Não possui pausa, reprodução ou busca como um player de mídia comum.
- Depende do suporte de codec e WebRTC da TV.

## Requisitos

- Android 8.0 ou superior (API 26).
- Celular e TV conectados à mesma rede Wi-Fi.
- A rede não pode bloquear comunicação entre dispositivos, como algumas redes de convidados.

## Compilação

O Gradle Wrapper está incluído no projeto. É necessário JDK 17 ou 21.

### Linux/macOS

```sh
export JAVA_HOME=/caminho/para/jdk-21
./gradlew assembleDebug
```

### Windows PowerShell

```powershell
.\gradlew.bat assembleDebug
```

O APK será gerado em:

`app/build/outputs/apk/debug/app-debug.apk`

## Estrutura do projeto

```text
app/src/main/java/io/github/ddagunts/screencast/
├── cast/       # Descoberta e protocolo Cast V2
├── media/      # Captura, codificação H.264 e servidor HLS
├── webrtc/     # PeerConnection e sinalização WebRTC
├── androidtv/  # Pareamento e controle remoto Android TV
├── ui/         # Interface Jetpack Compose
└── util/       # Rede e sistema de logs
receiver/       # Receiver WebRTC personalizado
```

## Privacidade e segurança

O stream é transmitido somente pela rede local. O HLS usa um token aleatório por sessão e o servidor fica vinculado à interface Wi-Fi do celular. Os logs ficam no armazenamento privado do aplicativo e devem ser revisados antes de serem compartilhados publicamente.

## Aviso

Este projeto não é afiliado à Xiaomi, TCL, Google ou Chromecast. O nome Chromecast é uma marca registrada da Google LLC.

## Licença

Apache-2.0.
