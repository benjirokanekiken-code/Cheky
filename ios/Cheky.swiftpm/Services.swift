import Foundation
import Security
import UIKit
import Vision

// MARK: - On-device text recognition (Apple Vision)

enum OCR {
    static func rows(from image: UIImage) async throws -> [String] {
        guard let cg = image.cgImage else { return [] }
        return try await Task.detached(priority: .userInitiated) {
            let request = VNRecognizeTextRequest()
            request.recognitionLevel = .accurate
            request.recognitionLanguages = ["de-DE"]
            request.usesLanguageCorrection = true
            try VNImageRequestHandler(cgImage: cg, options: [:]).perform([request])
            let lines: [OcrLine] = (request.results ?? []).compactMap { obs in
                guard let text = obs.topCandidates(1).first?.string else { return nil }
                let b = obs.boundingBox // normalised, origin bottom-left
                return OcrLine(text: text,
                               left: Int(b.minX * 10_000),
                               top: Int((1 - b.maxY) * 10_000),
                               bottom: Int((1 - b.minY) * 10_000))
            }
            return ReceiptParser.groupRows(lines)
        }.value
    }
}

extension UIImage {
    /// Redraws upright and scales the longest side down to `maxSide`.
    func normalized(maxSide: CGFloat = 2000) -> UIImage {
        let scale = min(1, maxSide / max(size.width, size.height))
        let target = CGSize(width: (size.width * scale).rounded(), height: (size.height * scale).rounded())
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: target, format: format).image { _ in
            draw(in: CGRect(origin: .zero, size: target))
        }
    }
}

// MARK: - Optional precise mode: Claude API with the user's own key

enum ClaudeClient {
    private static let model = "claude-sonnet-5-5"

    private static var receiptPrompt: String {
        """
        Ти — помічник, що розбирає німецькі касові чеки (Aldi, Lidl, Rewe, Edeka, Kaufland, Penny, Netto, dm, Rossmann тощо) і перекладає їх українською.
        Витягни всі товари з чека (якщо фото кілька — це частини одного чека по порядку).
        Правила:
        - Розшифровуй скорочення (напр. "H-MILCH" = ультрапастеризоване молоко, "SCHW.KOTEL." = свиняча котлета).
        - "uk" — природна українська назва товару, коротко (2–5 слів), з об'ємом/вагою якщо є.
        - price — підсумкова сума за рядок у євро (число). Якщо є "2 x 1,19", то qty=2, price=2.38.
        - Знижки (Rabatt, Preisvorteil, Coupon) — окремий рядок з від'ємною ціною, категорія "Знижки".
        - Pfand — категорія "Застава (Pfand)"; Leergut/Pfandrückgabe — від'ємна ціна.
        - Не включай Summe, Zwischensumme, MwSt, Bar, EC-Karte, Rückgeld.
        - category — одна з: \(Cat.all.joined(separator: ", ")).
        - date — YYYY-MM-DD або null. store — назва магазину. total — сума "Summe"/"zu zahlen".
        Поверни ЛИШЕ JSON: {"store":string,"date":string|null,"total":number,"items":[{"de":string,"uk":string,"qty":number,"price":number,"category":string}]}
        """
    }

    static func parseReceipt(apiKey: String, images: [UIImage]) async throws -> [String: Any] {
        var content: [[String: Any]] = images.compactMap { img -> [String: Any]? in
            guard let data = img.normalized(maxSide: 1600).jpegData(compressionQuality: 0.85) else { return nil }
            return ["type": "image",
                    "source": ["type": "base64", "media_type": "image/jpeg", "data": data.base64EncodedString()]]
        }
        content.append(["type": "text", "text": receiptPrompt])
        let text = try await call(apiKey: apiKey, content: content, maxTokens: 4096)
        guard let s = text.firstIndex(of: "{"), let e = text.lastIndex(of: "}"), s < e,
              let obj = try JSONSerialization.jsonObject(with: Data(text[s...e].utf8)) as? [String: Any]
        else { throw AppError("Claude не повернув список товарів") }
        return obj
    }

    static func translate(apiKey: String, text: String) async throws -> String {
        let prompt = """
        Переклади з німецької на українську. Якщо це назва товару або склад продукту — дай природний переклад, коротко поясни незрозумілі терміни, а алергени (молоко, глютен, горіхи, соя, яйця тощо) познач окремим рядком "Алергени: …". Відповідай лише українською, без вступу.

        Текст:
        \(text)
        """
        return try await call(apiKey: apiKey, content: [["type": "text", "text": prompt]], maxTokens: 1500)
            .trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func call(apiKey: String, content: [[String: Any]], maxTokens: Int) async throws -> String {
        var req = URLRequest(url: URL(string: "https://api.anthropic.com/v1/messages")!)
        req.httpMethod = "POST"
        req.timeoutInterval = 120
        req.setValue("application/json", forHTTPHeaderField: "content-type")
        req.setValue(apiKey.trimmingCharacters(in: .whitespaces), forHTTPHeaderField: "x-api-key")
        req.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        let body: [String: Any] = [
            "model": model,
            "max_tokens": maxTokens,
            "messages": [["role": "user", "content": content]],
        ]
        req.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: req)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        guard (200..<300).contains(code) else {
            if code == 401 { throw AppError("Невірний API-ключ") }
            let msg = (json?["error"] as? [String: Any])?["message"] as? String
            throw AppError(msg ?? "HTTP \(code)")
        }
        let blocks = json?["content"] as? [[String: Any]] ?? []
        return blocks.filter { $0["type"] as? String == "text" }.compactMap { $0["text"] as? String }.joined()
    }
}

struct AppError: LocalizedError {
    let message: String
    init(_ message: String) { self.message = message }
    var errorDescription: String? { message }
}

// MARK: - Keychain for the API key

enum Keychain {
    private static let account = "anthropic_api_key"

    static func load() -> String {
        let q: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                kSecAttrAccount as String: account,
                                kSecReturnData as String: true]
        var out: AnyObject?
        guard SecItemCopyMatching(q as CFDictionary, &out) == errSecSuccess,
              let data = out as? Data else { return "" }
        return String(decoding: data, as: UTF8.self)
    }

    static func save(_ value: String) {
        let base: [String: Any] = [kSecClass as String: kSecClassGenericPassword,
                                   kSecAttrAccount as String: account]
        SecItemDelete(base as CFDictionary)
        guard !value.isEmpty else { return }
        var add = base
        add[kSecValueData as String] = Data(value.utf8)
        SecItemAdd(add as CFDictionary, nil)
    }
}
