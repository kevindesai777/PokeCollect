// Experiment J, step 2: answers the planner's exported model calls with Apple Foundation Models on the Mac.
// Step 1 and 3 are FoundationModelsEvalTest (export, score). Usage, from the repo root:
//
//   swift scripts/fm_eval.swift [shared/build/fm-eval]
//
// Appends one line per call to responses.jsonl and skips ids already there, so it can be stopped and
// resumed. Calls are made like the iOS app's FoundationModelsBridge: a fresh session per question,
// greedy sampling, the JSON Schema rebuilt as a DynamicGenerationSchema (same conversion, copied).
// "primed" requests answer the warm-up question in that session first, like Android's prepare().
import Foundation
import FoundationModels

struct Request: Decodable {
    let id: String
    let question: String
    let instructions: String
    let schema: String
    let primerInput: String?
    let primerSchema: String?
}

struct Response: Encodable {
    let id: String
    let raw: String?
    let ms: Double
    let error: String?
}

enum RunnerError: Error { case unsupportedSchema(String) }

/// Same conversion as iosApp/iosApp/FoundationModelsBridge.swift.
func generationSchema(fromJSONSchema text: String) throws -> GenerationSchema {
    guard
        let root = try JSONSerialization.jsonObject(with: Data(text.utf8)) as? [String: Any],
        let properties = root["properties"] as? [String: Any],
        let required = root["required"] as? [String]
    else {
        throw RunnerError.unsupportedSchema(text)
    }
    let fields: [DynamicGenerationSchema.Property] = try required.map { name in
        guard
            let field = properties[name] as? [String: Any],
            let choices = field["enum"] as? [String]
        else {
            throw RunnerError.unsupportedSchema(text)
        }
        return DynamicGenerationSchema.Property(name: name, schema: DynamicGenerationSchema(name: name, anyOf: choices))
    }
    return try GenerationSchema(root: DynamicGenerationSchema(name: "Query", properties: fields), dependencies: [])
}

func milliseconds(_ d: Duration) -> Double {
    Double(d.components.seconds) * 1000 + Double(d.components.attoseconds) / 1e15
}

let dir = URL(fileURLWithPath: CommandLine.arguments.dropFirst().first ?? "shared/build/fm-eval")
let requestsURL = dir.appendingPathComponent("requests.jsonl")
let responsesURL = dir.appendingPathComponent("responses.jsonl")

let model = SystemLanguageModel.default
guard case .available = model.availability else {
    print("Foundation Models unavailable: \(model.availability)")
    exit(1)
}

let requests = try String(contentsOf: requestsURL, encoding: .utf8)
    .split(separator: "\n")
    .map { try JSONDecoder().decode(Request.self, from: Data($0.utf8)) }
let done = Set(((try? String(contentsOf: responsesURL, encoding: .utf8)) ?? "")
    .split(separator: "\n")
    .compactMap { (try? JSONSerialization.jsonObject(with: Data($0.utf8))) as? [String: Any] }
    .compactMap { $0["id"] as? String })
let todo = requests.filter { !done.contains($0.id) }
print("\(ProcessInfo.processInfo.operatingSystemVersionString): \(requests.count) requests, \(done.count) done, \(todo.count) to go")

if !FileManager.default.fileExists(atPath: responsesURL.path) {
    FileManager.default.createFile(atPath: responsesURL.path, contents: nil)
}
let out = try FileHandle(forWritingTo: responsesURL)
out.seekToEndOfFile()
let encoder = JSONEncoder()
encoder.outputFormatting = [.sortedKeys, .withoutEscapingSlashes]
let options = GenerationOptions(sampling: .greedy)

for (n, request) in todo.enumerated() {
    var raw: String?
    var failure: String?
    var ms = 0.0
    do {
        let schema = try generationSchema(fromJSONSchema: request.schema)
        let session = LanguageModelSession(model: model, instructions: request.instructions)
        if let primer = request.primerInput, let primerSchema = request.primerSchema {
            _ = try await session.respond(to: primer, schema: try generationSchema(fromJSONSchema: primerSchema), options: options)
        }
        // Timed like the app's wait: the question only, after any warm-up.
        let started = ContinuousClock.now
        let response = try await session.respond(to: request.question, schema: schema, options: options)
        ms = milliseconds(ContinuousClock.now - started)
        raw = response.content.jsonString
    } catch {
        failure = "\(error)"
    }
    let line = try encoder.encode(Response(id: request.id, raw: raw, ms: ms, error: failure))
    out.write(line + Data("\n".utf8))
    print("[\(n + 1)/\(todo.count)] \(request.id) \(Int(ms)) ms \(raw ?? "ERROR \(failure ?? "")")")
}
try out.close()
