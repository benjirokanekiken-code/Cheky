import SwiftUI
import Observation
import Translation

@MainActor
@Observable
final class AppStore {
    // saved data
    var receipts: [Receipt] = []
    var apiKey: String = ""
    var selectedTab = 0
    @ObservationIgnored private var learned: [String: String] = [:]

    // current scan
    var images: [UIImage] = []
    var busy = false
    var status: String?
    var statusIsError = false
    var draft: Receipt?
    var draftSaved = false
    @ObservationIgnored private var editedNames = Set<String>()

    // bridge to Apple's Translation framework (driven by .translationTask in ContentView)
    var translationConfig: TranslationSession.Configuration?
    @ObservationIgnored private var pendingTexts: [String] = []
    @ObservationIgnored private var translationContinuation: CheckedContinuation<[String: String], Never>?

    var preciseMode: Bool { !apiKey.isEmpty }

    init() {
        receipts = Self.read([Receipt].self, from: "receipts.json") ?? []
        learned = Self.read([String: String].self, from: "learned.json") ?? [:]
        apiKey = Keychain.load()
    }

    // MARK: - Scanning

    func addImages(_ new: [UIImage]) {
        guard !new.isEmpty else { return }
        if draftSaved { draft = nil; draftSaved = false }
        images += new
        setStatus(nil)
    }

    func clearScan() {
        images = []; draft = nil; draftSaved = false; setStatus(nil)
    }

    func analyze() async {
        guard !images.isEmpty, !busy else { return }
        busy = true
        setStatus(nil)
        defer { busy = false }
        do {
            let r: Receipt
            if preciseMode {
                r = try await analyzeWithClaude()
            } else {
                r = try await analyzeOffline()
            }
            draft = r
            draftSaved = false
            editedNames.removeAll()
            if r.items.isEmpty {
                setStatus("Не знайшов товарів. Спробуйте рівніше і світліше фото.", error: true)
            } else if status != nil && !statusIsError {
                setStatus(nil)
            }
        } catch {
            setStatus("Помилка: \(error.localizedDescription)", error: true)
        }
    }

    private func analyzeOffline() async throws -> Receipt {
        setStatus("Розпізнаю текст…")
        var parts: [ParsedReceipt] = []
        for img in images {
            parts.append(ReceiptParser.parse(try await OCR.rows(from: img.normalized())))
        }
        let raw = parts.flatMap(\.items)

        setStatus("Перекладаю…")
        let needed = raw.filter { learned[Self.key($0.name)] == nil }.map { ReceiptText.expand($0.name) }
        let translated = await translate(needed)
        let items = raw.map { r -> Item in
            let expanded = ReceiptText.expand(r.name)
            let uk = learned[Self.key(r.name)]
                ?? translated[expanded].map { $0.prefix(1).uppercased() + $0.dropFirst() }
                ?? expanded
            return Item(de: r.name, uk: uk, qty: r.qty, price: r.price, category: Categorizer.categorize(r.name))
        }
        if !needed.isEmpty && translated.isEmpty {
            setStatus("Переклад недоступний — назви залишено німецькою. Перевірте, що мови завантажені (Налаштування iOS → Програми → Переклад).", error: true)
        } else {
            setStatus(nil)
        }
        let total = parts.compactMap(\.total).last ?? items.reduce(0) { $0 + $1.price }
        return Receipt(
            store: parts.compactMap(\.store).first ?? "Магазин",
            date: parts.compactMap(\.date).first ?? DateFormat.today,
            total: round2(total),
            items: items
        )
    }

    private func analyzeWithClaude() async throws -> Receipt {
        setStatus("Надсилаю фото в Claude…")
        let o = try await ClaudeClient.parseReceipt(apiKey: apiKey, images: images)
        let rawItems = o["items"] as? [[String: Any]] ?? []
        let items = rawItems.map { i -> Item in
            let de = jsonString(i["de"])
            let cat = jsonString(i["category"])
            let qty = jsonNumber(i["qty"], default: 1)
            var uk = jsonString(i["uk"])
            if uk.isEmpty { uk = de }
            if let fixed = learned[Self.key(de)] { uk = fixed }
            return Item(de: de, uk: uk, qty: qty > 0 ? qty : 1, price: jsonNumber(i["price"]),
                        category: Cat.all.contains(cat) ? cat : Cat.other)
        }
        let date = jsonString(o["date"])
        let total = jsonNumber(o["total"])
        setStatus(nil)
        return Receipt(
            store: jsonString(o["store"]).isEmpty ? "Магазин" : jsonString(o["store"]),
            date: DateFormat.iso.date(from: date) != nil ? date : DateFormat.today,
            total: total != 0 ? total : round2(items.reduce(0) { $0 + $1.price }),
            items: items
        )
    }

    // MARK: - Editing

    func updateItem(_ index: Int, _ item: Item) {
        guard var d = draft, d.items.indices.contains(index) else { return }
        if d.items[index].uk != item.uk && !item.de.isEmpty { editedNames.insert(item.de) }
        d.items[index] = item
        draft = d
        draftSaved = false
    }

    func removeItems(at offsets: IndexSet) {
        guard var d = draft else { return }
        d.items.remove(atOffsets: offsets)
        draft = d
        draftSaved = false
    }

    func addItem() -> Int {
        var d = draft ?? Receipt(store: "Магазин", date: DateFormat.today, total: 0, items: [])
        d.items.append(Item(de: "", uk: "Новий товар", price: 0, category: Cat.other))
        draft = d
        draftSaved = false
        return d.items.count - 1
    }

    func updateHeader(store: String? = nil, date: String? = nil, total: Double? = nil) {
        guard var d = draft else { return }
        if let store { d.store = store }
        if let date { d.date = date }
        if let total { d.total = total }
        draft = d
        draftSaved = false
    }

    func saveDraft() {
        guard let d = draft else { return }
        receipts.removeAll { $0.id == d.id }
        receipts.append(d)
        Self.write(receipts, to: "receipts.json")
        if !editedNames.isEmpty {
            for item in d.items where editedNames.contains(item.de) { learned[Self.key(item.de)] = item.uk }
            Self.write(learned, to: "learned.json")
            editedNames.removeAll()
        }
        draftSaved = true
    }

    func openReceipt(_ r: Receipt) {
        images = []; draft = r; draftSaved = true; setStatus(nil); selectedTab = 0
    }

    func deleteReceipt(_ id: String) {
        receipts.removeAll { $0.id == id }
        Self.write(receipts, to: "receipts.json")
        if draft?.id == id { draft = nil; draftSaved = false }
    }

    func deleteAll() {
        receipts = []
        Self.write(receipts, to: "receipts.json")
        draft = nil
    }

    func saveApiKey(_ key: String) {
        apiKey = key.trimmingCharacters(in: .whitespacesAndNewlines)
        Keychain.save(apiKey)
    }

    // MARK: - Translation

    /// German -> Ukrainian with Apple's on-device Translation framework.
    func translate(_ texts: [String]) async -> [String: String] {
        let unique = Array(Set(texts.filter { !$0.isEmpty }))
        guard !unique.isEmpty else { return [:] }
        let source = Locale.Language(identifier: "de")
        let target = Locale.Language(identifier: "uk")
        if await LanguageAvailability().status(from: source, to: target) == .unsupported { return [:] }

        translationContinuation?.resume(returning: [:]) // cancel an older request
        return await withCheckedContinuation { cont in
            translationContinuation = cont
            pendingTexts = unique
            if translationConfig == nil {
                translationConfig = TranslationSession.Configuration(source: source, target: target)
            } else {
                translationConfig?.invalidate()
            }
        }
    }

    func performTranslation(_ session: TranslationSession) async {
        guard let cont = translationContinuation else { return }
        translationContinuation = nil
        let texts = pendingTexts
        var result: [String: String] = [:]
        do {
            let requests = texts.enumerated().map {
                TranslationSession.Request(sourceText: $0.element, clientIdentifier: String($0.offset))
            }
            for r in try await session.translations(from: requests) {
                if let id = r.clientIdentifier, let i = Int(id), texts.indices.contains(i) {
                    result[texts[i]] = r.targetText
                }
            }
        } catch {
            // user declined the language download or translation failed
        }
        cont.resume(returning: result)
    }

    // MARK: - Helpers

    private func setStatus(_ msg: String?, error: Bool = false) {
        status = msg; statusIsError = error
    }

    static func key(_ de: String) -> String {
        de.uppercased().trimmingCharacters(in: .whitespaces)
            .replacingOccurrences(of: #"\s+"#, with: " ", options: .regularExpression)
    }

    private static func url(_ name: String) -> URL {
        FileManager.default.urls(for: .documentDirectory, in: .userDomainMask)[0].appendingPathComponent(name)
    }

    private static func read<T: Decodable>(_ type: T.Type, from name: String) -> T? {
        guard let data = try? Data(contentsOf: url(name)) else { return nil }
        let dec = JSONDecoder()
        dec.dateDecodingStrategy = .iso8601
        return try? dec.decode(T.self, from: data)
    }

    private static func write<T: Encodable>(_ value: T, to name: String) {
        let enc = JSONEncoder()
        enc.dateEncodingStrategy = .iso8601
        guard let data = try? enc.encode(value) else { return }
        try? data.write(to: url(name), options: .atomic)
    }
}
