import Foundation
import FoundationModels
import Shared

/// Apple Foundation Models behind the shared `LocalModel` seam.
///
/// Kotlin builds the same per-question JSON Schema it hands to LiteRT-LM on Android. Here it is
/// rebuilt as a runtime `DynamicGenerationSchema`, so Apple's constrained decoder enforces the same
/// narrowed choices. Swift-only API, so this lives in the app target, not in Kotlin.
final class FoundationModelsBridge: NSObject, NativeLanguageModel {
    let displayName = "Apple Foundation Models"

    private let model = SystemLanguageModel.default

    func load(completion: @escaping (String?) -> Void) {
        switch model.availability {
        case .available:
            // Warm up the model so the first question doesn't pay the load cost on stage.
            LanguageModelSession(model: model).prewarm()
            print("PokeCollect.FM available, prewarm requested")
            completion(nil)
        case .unavailable(let reason):
            completion(Self.describe(reason))
        }
    }

    func generate(
        instructions: String,
        input: String,
        jsonSchema: String,
        completion: @escaping (String?, String?) -> Void
    ) {
        Task {
            let started = ContinuousClock.now
            do {
                let schema = try Self.generationSchema(fromJSONSchema: jsonSchema)
                // A fresh session per question: parsing is stateless, history would leak between questions.
                let session = LanguageModelSession(model: model, instructions: instructions)
                let response = try await session.respond(
                    to: input,
                    schema: schema,
                    options: GenerationOptions(sampling: .greedy)
                )
                let ms = (ContinuousClock.now - started).components.attoseconds / 1_000_000_000_000_000
                    + (ContinuousClock.now - started).components.seconds * 1000
                print("PokeCollect.FM generate \(ms) ms: \(input) -> \(response.content.jsonString)")
                completion(response.content.jsonString, nil)
            } catch {
                print("PokeCollect.FM error: \(input) -> \(error)")
                completion(nil, Self.describe(error))
            }
        }
    }

    /// Converts the planner's object-of-string-enums JSON Schema into a `GenerationSchema`.
    private static func generationSchema(fromJSONSchema text: String) throws -> GenerationSchema {
        guard
            let root = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
            let properties = root["properties"] as? [String: Any],
            let required = root["required"] as? [String]
        else {
            throw BridgeError.unsupportedSchema(text)
        }
        // "required" keeps the field order the planner chose; Apple generates properties in order.
        let fields: [DynamicGenerationSchema.Property] = try required.map { name in
            guard
                let field = properties[name] as? [String: Any],
                let choices = field["enum"] as? [String]
            else {
                throw BridgeError.unsupportedSchema(text)
            }
            return DynamicGenerationSchema.Property(
                name: name,
                schema: DynamicGenerationSchema(name: name, anyOf: choices)
            )
        }
        let query = DynamicGenerationSchema(name: "Query", properties: fields)
        return try GenerationSchema(root: query, dependencies: [])
    }

    private static func describe(_ reason: SystemLanguageModel.Availability.UnavailableReason) -> String {
        switch reason {
        case .deviceNotEligible:
            return "This device can't run Apple Intelligence."
        case .appleIntelligenceNotEnabled:
            return "Turn on Apple Intelligence in Settings to use on-device questions."
        case .modelNotReady:
            return "The on-device model is still downloading. Try again in a few minutes."
        @unknown default:
            return "Apple Foundation Models is unavailable (\(reason))."
        }
    }

    /// Short, readable failure text for the "could not understand" card (the raw NSError is unreadable on stage).
    private static func describe(_ error: Error) -> String {
        let ns = error as NSError
        let underlying = (ns.userInfo[NSMultipleUnderlyingErrorsKey] as? [NSError])?.first
        if underlying?.domain.contains("ModelManager") == true {
            return "Apple Intelligence isn't ready to generate yet (model manager \(underlying!.code)). " +
                "In the Simulator this is common; on a device, check Apple Intelligence in Settings."
        }
        if let generation = error as? LanguageModelSession.GenerationError {
            return "Foundation Models couldn't generate: \(generation)"
        }
        return "Foundation Models failed: \(ns.domain) \(ns.code)"
    }

    private enum BridgeError: Error {
        case unsupportedSchema(String)
    }
}
