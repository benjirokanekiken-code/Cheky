import Foundation

struct Item: Codable, Identifiable, Hashable {
    var id = UUID()
    var de: String
    var uk: String
    var qty: Double = 1
    var price: Double
    var category: String
}

struct Receipt: Codable, Identifiable, Hashable {
    var id: String = "r" + String(Int(Date().timeIntervalSince1970 * 1000), radix: 36)
    var store: String
    var date: String // YYYY-MM-DD
    var total: Double
    var items: [Item]
    var createdAt = Date()

    var itemsSum: Double { round2(items.reduce(0) { $0 + $1.price }) }
    var dateValue: Date? { DateFormat.iso.date(from: date) }
}

enum Cat {
    static let dairy = "Молочні продукти"
    static let meat = "М'ясо і риба"
    static let veg = "Овочі і фрукти"
    static let bread = "Хліб і випічка"
    static let grocery = "Бакалія"
    static let drinks = "Напої"
    static let sweets = "Солодощі і снеки"
    static let frozen = "Заморожені продукти"
    static let household = "Побутова хімія"
    static let hygiene = "Гігієна і косметика"
    static let pfand = "Застава (Pfand)"
    static let discount = "Знижки"
    static let other = "Інше"

    static let all = [dairy, meat, veg, bread, grocery, drinks, sweets, frozen,
                      household, hygiene, pfand, discount, other]
}

enum DateFormat {
    static let iso: DateFormatter = {
        let f = DateFormatter()
        f.calendar = Calendar(identifier: .gregorian)
        f.locale = Locale(identifier: "en_US_POSIX")
        f.dateFormat = "yyyy-MM-dd"
        return f
    }()
    static let display: DateFormatter = {
        let f = DateFormatter()
        f.dateFormat = "dd.MM.yyyy"
        return f
    }()
    static func pretty(_ s: String) -> String {
        guard let d = iso.date(from: s) else { return s }
        return display.string(from: d)
    }
    static var today: String { iso.string(from: Date()) }
}

func round2(_ v: Double) -> Double { (v * 100).rounded() / 100 }

func eur(_ v: Double) -> String {
    String(format: "%.2f €", v).replacingOccurrences(of: ".", with: ",")
}

func fmtQty(_ q: Double) -> String {
    if q == q.rounded() { return String(Int(q)) }
    return String(format: "%.3f", q)
        .replacingOccurrences(of: "0+$", with: "", options: .regularExpression)
        .replacingOccurrences(of: ".", with: ",")
}

/// Reads a number that may arrive as a JSON number or as a string like "1,19".
func jsonNumber(_ v: Any?, default def: Double = 0) -> Double {
    if let n = v as? NSNumber { return n.doubleValue }
    if let s = v as? String { return Double(s.replacingOccurrences(of: ",", with: ".").trimmingCharacters(in: .whitespaces)) ?? def }
    return def
}

func jsonString(_ v: Any?) -> String {
    (v as? String) ?? ""
}
