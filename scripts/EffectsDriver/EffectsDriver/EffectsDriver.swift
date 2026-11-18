import XCTest
import os

/// Taps PokeCollect's cards so Instruments can time the catch effects on an iPhone.
///
/// Settings arrive as environment variables (xcodebuild passes TEST_RUNNER_X in as X):
/// EFFECT_CARDS "fire:Charmander,water:Squirtle", EFFECT_PLAYS, EFFECT_RELAUNCH ("1": a new app
/// process per type), EFFECT_AFTER_RELAUNCH ("1": then relaunch and play once more, "<type> R1"),
/// EFFECT_WAIT (seconds per play). Each play is a Points of Interest interval named "play" with the
/// message "<type> <n>", so the trace can be cut per play.
final class EffectsDriver: XCTestCase {
    private let app = XCUIApplication(bundleIdentifier: "com.pixel.pokecollect")
    private let signposter = OSSignposter(subsystem: "com.pixel.pokecollect.effectsdriver", category: .pointsOfInterest)
    private let env = ProcessInfo.processInfo.environment

    override func setUp() {
        continueAfterFailure = false
    }

    func testDump() {
        app.activate()
        openList()
        search(env["EFFECT_NAME"] ?? "Charmander")
        print(app.debugDescription)
    }

    func testPlay() throws {
        let cards = (env["EFFECT_CARDS"] ?? "fire:Charmander").split(separator: ",").map {
            let parts = $0.split(separator: ":").map(String.init)
            return (type: parts[0], name: parts[1])
        }
        let plays = Int(env["EFFECT_PLAYS"] ?? "") ?? 3
        let relaunch = env["EFFECT_RELAUNCH"] == "1"
        let afterRelaunch = env["EFFECT_AFTER_RELAUNCH"] == "1"
        if !relaunch { app.activate() }
        for card in cards {
            if relaunch { app.launch() }
            try play(card.type, card.name, times: plays, prefix: "")
            if afterRelaunch {
                // A new process with whatever the system kept on disk: the first play after a relaunch.
                app.terminate()
                app.launch()
                try play(card.type, card.name, times: 1, prefix: "R")
            }
        }
    }

    /// Ticks the card [times] times, each a "play" interval with the message "<type> <prefix><n>",
    /// followed by a "kept" (the card ended up caught) or "lost" event; unticks it after each.
    private func play(_ type: String, _ name: String, times: Int, prefix: String) throws {
        let wait = Double(env["EFFECT_WAIT"] ?? "") ?? 2.2
        openList()
        search(name)
        let row = try findRow(name)
        if isCaught(row) {
            row.tap()
            sleep(1)
        }
        var n = 1
        var attempts = 0
        while n <= times && attempts < times * 2 + 1 {
            attempts += 1
            let label = "\(type) \(prefix)\(n)"
            let state = signposter.beginInterval("play", id: signposter.makeSignpostID(), "\(label)")
            row.tap()
            Thread.sleep(forTimeInterval: wait)
            signposter.endInterval("play", state)
            if isCaught(row) {
                signposter.emitEvent("kept", "\(label)")
                n += 1
                row.tap() // untick, unmeasured
            } else {
                signposter.emitEvent("lost", "\(label)")
                print("\(label): a tap didn't tick the card; measuring again")
            }
            sleep(1)
        }
        print("\(type) \(name): \(n - 1) plays \(prefix)")
    }

    /// An empty collection opens on its welcome card; the list has the search field.
    private func openList() {
        let field = app.textViews.firstMatch
        for _ in 0..<20 {
            if field.exists { return }
            let add = app.buttons["Add a card by name"]
            if add.exists { add.tap() } else { Thread.sleep(forTimeInterval: 0.5) }
        }
        print(app.debugDescription)
        XCTFail("the collection list never showed")
    }

    /// Types the name and presses Search, which takes the focus away from the field: a focused
    /// field's blinking cursor would draw frames of its own.
    private func search(_ name: String) {
        let clear = app.buttons["Clear search"]
        if clear.exists { clear.tap() }
        let field = app.textViews.firstMatch
        field.tap()
        field.typeText(name + "\n")
        Thread.sleep(forTimeInterval: 0.8)
    }

    /// A card's row is a button labelled "Charmander, #004, Gen 1, FIRE".
    private func findRow(_ name: String) throws -> XCUIElement {
        let row = app.buttons.matching(NSPredicate(format: "label BEGINSWITH %@", name + ",")).firstMatch
        guard row.waitForExistence(timeout: 3) else {
            print(app.debugDescription)
            throw XCTSkip("no row for \(name)")
        }
        return row
    }

    /// The tick is a button of its own beside the row, labelled "Caught" once ticked.
    private func isCaught(_ row: XCUIElement) -> Bool {
        let rowFrame = row.frame
        return app.buttons.matching(identifier: "Caught").allElementsBoundByIndex.contains {
            rowFrame.minY <= $0.frame.midY && $0.frame.midY <= rowFrame.maxY
        }
    }
}
