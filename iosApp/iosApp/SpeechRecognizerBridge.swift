import AVFoundation
import Foundation
import Shared
import Speech

/// Apple's Speech framework behind the shared `SpeechToText` seam, on-device only.
///
/// `requiresOnDeviceRecognition` keeps the audio on the phone, so this works in airplane mode once
/// the English speech assets are on the device. Card names go in as `contextualStrings`, which
/// biases recognition toward them. Everything here runs on the main queue.
final class SpeechRecognizerBridge: NSObject, NativeSpeechRecognizer {
    let displayName = "Apple on-device speech"

    private let recognizer = SFSpeechRecognizer(locale: Locale(identifier: "en-US"))
    private let audioEngine = AVAudioEngine()
    private var request: SFSpeechAudioBufferRecognitionRequest?
    private var task: SFSpeechRecognitionTask?
    private var onFinish: ((String?) -> Void)?
    private var stopping = false

    func start(phrases: [String], onTranscript: @escaping (String) -> Void, onFinish: @escaping (String?) -> Void) {
        DispatchQueue.main.async {
            self.finish(nil) // one session at a time
            self.onFinish = onFinish
            self.stopping = false
            Self.requestPermissions { problem in
                if let problem {
                    self.finish(problem)
                    return
                }
                guard self.onFinish != nil, !self.stopping else { return } // stopped while the dialog was up
                do {
                    try self.begin(phrases: phrases, onTranscript: onTranscript)
                } catch {
                    self.finish("Could not start listening: \(error.localizedDescription)")
                }
            }
        }
    }

    func stop() {
        DispatchQueue.main.async {
            guard self.onFinish != nil else { return }
            self.stopping = true
            guard self.task != nil else {
                self.finish(nil)
                return
            }
            self.stopAudio()
            // The final result follows endAudio(); finish anyway if it never comes.
            self.request?.endAudio()
            DispatchQueue.main.asyncAfter(deadline: .now() + 3) { if self.stopping { self.finish(nil) } }
        }
    }

    private func begin(phrases: [String], onTranscript: @escaping (String) -> Void) throws {
        guard let recognizer, recognizer.isAvailable else {
            throw BridgeError("speech recognition isn't available right now")
        }
        guard recognizer.supportsOnDeviceRecognition else {
            throw BridgeError("this iPhone can't recognize English speech on the device")
        }
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.record, mode: .measurement, options: .duckOthers)
        try session.setActive(true, options: .notifyOthersOnDeactivation)

        let request = SFSpeechAudioBufferRecognitionRequest()
        request.requiresOnDeviceRecognition = true
        request.shouldReportPartialResults = true
        request.contextualStrings = phrases
        request.taskHint = .dictation
        self.request = request

        let input = audioEngine.inputNode
        input.installTap(onBus: 0, bufferSize: 1024, format: input.outputFormat(forBus: 0)) { buffer, _ in
            request.append(buffer)
        }
        audioEngine.prepare()
        try audioEngine.start()

        // After a long pause the recognizer can start a fresh transcription without the earlier words;
        // keep those, so a list read out slowly isn't lost.
        var earlier = ""
        var current = ""
        task = recognizer.recognitionTask(with: request) { [weak self] result, error in
            DispatchQueue.main.async {
                guard let self, self.onFinish != nil else { return }
                if let result {
                    let text = result.bestTranscription.formattedString
                    #if DEBUG
                    print("PokeCollect.Speech result final=\(result.isFinal) segments=\(result.bestTranscription.segments.count): \(text)")
                    #endif
                    // The final result after endAudio() often comes back empty or with only the last words
                    // (iOS 26, on device), so it never replaces what was already heard.
                    let shrank = Double(text.count) < Double(current.count) * 0.6
                    if shrank, result.isFinal || current.split(separator: " ").count >= 2 {
                        print("PokeCollect.Speech reset detected, keeping: \(current)")
                        earlier = [earlier, current].filter { !$0.isEmpty }.joined(separator: " ")
                    }
                    current = text
                    onTranscript([earlier, current].filter { !$0.isEmpty }.joined(separator: " "))
                    if result.isFinal {
                        print("PokeCollect.Speech final: \([earlier, current].joined(separator: " "))")
                        self.finish(nil)
                        return
                    }
                }
                if let error {
                    print("PokeCollect.Speech error (stopping=\(self.stopping)): \(error)")
                    // After stop(), "no speech" and cancellation errors just mean we're done.
                    self.finish(self.stopping ? nil : "Speech recognition stopped: \(error.localizedDescription)")
                }
            }
        }
    }

    private func stopAudio() {
        if audioEngine.isRunning {
            audioEngine.stop()
            audioEngine.inputNode.removeTap(onBus: 0)
        }
    }

    /// Ends the session once: tears down audio and reports to Kotlin. Main queue only.
    private func finish(_ error: String?) {
        if onFinish != nil { print("PokeCollect.Speech finish error=\(error ?? "nil") stopping=\(stopping)") }
        stopAudio()
        task?.cancel()
        task = nil
        request = nil
        stopping = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        guard let onFinish else { return }
        self.onFinish = nil
        onFinish(error)
    }

    /// Speech recognition and microphone, asked in turn. Calls back on the main queue with nil or a reason.
    private static func requestPermissions(_ completion: @escaping (String?) -> Void) {
        SFSpeechRecognizer.requestAuthorization { status in
            guard status == .authorized else {
                DispatchQueue.main.async { completion("Allow Speech Recognition for PokeCollect in Settings to say your cards.") }
                return
            }
            AVAudioApplication.requestRecordPermission { granted in
                DispatchQueue.main.async {
                    completion(granted ? nil : "Allow the microphone for PokeCollect in Settings to say your cards.")
                }
            }
        }
    }

    private struct BridgeError: LocalizedError {
        let errorDescription: String?
        init(_ message: String) { errorDescription = message }
    }
}
