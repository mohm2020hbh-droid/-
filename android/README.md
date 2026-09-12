# Voice Duel — Android client

Kotlin + Jetpack Compose. The module is `:android` in this repository's
existing Gradle build, so it shares the root Gradle wrapper and version
catalog with the `:app` module that was already here.

## Build and install

```bash
./gradlew :android:assembleDebug
./gradlew :android:installDebug     # with a device or emulator attached
```

Unit tests (Robolectric, covering the protocol codec and event mapping):

```bash
./gradlew :android:testDebugUnitTest
```

## Pointing the app at a server

The server address is editable on the home screen and remembered between
launches, so normally you just type it once on each device.

The pre-filled default comes from `BuildConfig.DEFAULT_SERVER_URL`, which is
`ws://10.0.2.2:8000/ws` — the host machine as seen from an emulator. To bake in
a different default, add this to `local.properties` (it is git-ignored):

```properties
voiceduel.serverUrl=ws://192.168.1.10:8000/ws
```

Addresses must start with `ws://` or `wss://` and end with the `/ws` path.

> Cleartext `ws://` is permitted only for loopback and local addresses (see
> `res/xml/network_security_config.xml`). A server reached over the internet
> must be `wss://`, which is allowed everywhere — and is what the two phones
> will use once the backend is deployed behind TLS.

## Permissions

`RECORD_AUDIO` is requested on the play screen, and only from the player whose
turn it is to perform. Refusing it does not crash or block anything: the app
says the round will go unrecorded, offers a button to grant it, and the server
closes that round with a score of 0.

`INTERNET` is the only other permission.

## Layout

```
com.voiceduel/
  MainActivity.kt              the single activity
  audio/
    AudioRecorder.kt           MediaRecorder → AAC/MP4 bytes
    AudioPlayer.kt             MediaPlayer, fed from the received bytes
  data/
    GameProtocol.kt            message envelope + binary audio framing
    GameEvent.kt               parsed server events, and their mapper
    GameSocket.kt              OkHttp WebSocket transport
    GameRepository.kt          one call per protocol message
    ServerConfig.kt            the remembered server address
  ui/
    GameUiState.kt             one immutable snapshot for all five screens
    GameViewModel.kt           the whole match state machine
    VoiceDuelApp.kt            NavHost; navigation follows the state
    screens/                   Home, Waiting, Play, Rating, Result
    theme/                     colours, typography, RTL
```

The match is **server-driven**: the ViewModel never decides to change screen on
its own, it only reacts to events. That is why a disconnect or a late frame can
never leave the two phones showing different rounds.
