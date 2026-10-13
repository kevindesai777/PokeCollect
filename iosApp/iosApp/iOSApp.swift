import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        // Line-buffer stdout so PokeCollect.* lines show up live under `devicectl --console`.
        setvbuf(stdout, nil, _IOLBF, 0)
        KoinIosKt.doInitKoinForIos(model: FoundationModelsBridge(), speech: SpeechRecognizerBridge())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
