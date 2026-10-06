import SwiftUI
import Shared

@main
struct iOSApp: App {
    init() {
        KoinIosKt.doInitKoinForIos(model: FoundationModelsBridge())
    }

    var body: some Scene {
        WindowGroup {
            ContentView()
        }
    }
}
