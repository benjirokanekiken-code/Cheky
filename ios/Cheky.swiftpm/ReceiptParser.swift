import Foundation

/// Small wrapper around NSRegularExpression.
struct Rx {
    struct Match {
        let groups: [String]
        let start: Int // UTF-16 offsets
        let end: Int
    }

    let re: NSRegularExpression

    init(_ pattern: String, ci: Bool = false) {
        re = try! NSRegularExpression(pattern: pattern, options: ci ? [.caseInsensitive] : [])
    }

    func match(_ s: String) -> Match? {
        let ns = s as NSString
        guard let m = re.firstMatch(in: s, range: NSRange(location: 0, length: ns.length)) else { return nil }
        let groups = (0..<m.numberOfRanges).map { i -> String in
            let r = m.range(at: i)
            return r.location == NSNotFound ? "" : ns.substring(with: r)
        }
        return Match(groups: groups, start: m.range.location, end: m.range.location + m.range.length)
    }

    func test(_ s: String) -> Bool { match(s) != nil }
}

struct OcrLine {
    let text: String
    let left: Int
    let top: Int
    let bottom: Int
    var cy: Int { (top + bottom) / 2 }
    var h: Int { bottom - top }
}

struct RawItem {
    var name: String
    var qty: Double
    var price: Double
}

struct ParsedReceipt {
    var store: String?
    var date: String?
    var total: Double?
    var items: [RawItem]
}

/// Turns OCR text of a German supermarket receipt into item rows.
/// Works with the usual layout "NAME ..... 1,99 A" (Aldi, Lidl, Rewe, Edeka, Penny, Netto, dm…).
enum ReceiptParser {
    private static let priceRe = Rx(#"(-?\d{1,4}[.,]\d{2})\s*(-)?\s*(?:[A-Z*]{1,2}\d?)?\s*$"#)
    private static let qtyRe = Rx(#"^\s*(\d+(?:[.,]\d+)?)\s*(?:stk\.?|st\.?|x|×|\*)\s*(?:x\s*)?(?:€|eur)?\s*(\d+[.,]\d{2})"#, ci: true)
    private static let weightRe = Rx(#"^\s*(\d+[.,]\d{1,3})\s*kg\s*[x×*]\s*(?:€|eur)?\s*(\d+[.,]\d{2})"#, ci: true)
    private static let totalRe = Rx(#"\b(summe|zu zahlen|gesamtbetrag|gesamt|total|zahlbetrag|endbetrag)\b"#, ci: true)
    private static let skipRe = Rx(#"\b(mwst|ust|steuer|netto|brutto|bar|ec|karte|girocard|kartenzahlung|rückgeld|rueckgeld|gegeben|visa|mastercard|tse|beleg|kasse|bon|uhr|filiale|tel|www|danke|zwischensumme|posten|artikel|kundenbeleg|terminal|betrag|eur|euro|payback|punkte|seriennummer|transaktion|signatur)\b"#, ci: true)
    private static let dateRe = Rx(#"\b(\d{1,2})\.(\d{1,2})\.(\d{2,4})\b"#)
    private static let discountRe = Rx("rabatt|preisvorteil|coupon|nachlass|gutschein|leergut|pfandrück|pfandrueck|pfandbon", ci: true)

    private static let stores: [(String, String)] = [
        ("ALDI", "Aldi"), ("LIDL", "Lidl"), ("REWE", "Rewe"), ("EDEKA", "Edeka"),
        ("KAUFLAND", "Kaufland"), ("PENNY", "Penny"), ("NETTO", "Netto"), ("NORMA", "Norma"),
        ("ROSSMANN", "Rossmann"), ("DM-DROGERIE", "dm"), ("DM DROGERIE", "dm"), ("GLOBUS", "Globus"),
        ("TEGUT", "tegut"), ("MARKTKAUF", "Marktkauf"), ("MÜLLER", "Müller"), ("ACTION", "Action"),
        ("DENNS", "denn's"), ("ALNATURA", "Alnatura"), ("FAMILA", "Famila"), ("BUDNI", "Budni"),
        ("NAH & GUT", "Nah & Gut"), ("E CENTER", "E center"), ("HIT ", "HIT"),
    ]

    static func isDiscount(_ name: String) -> Bool { discountRe.test(name) }

    /// Groups OCR lines that sit on the same horizontal row, left to right.
    static func groupRows(_ lines: [OcrLine]) -> [String] {
        guard !lines.isEmpty else { return [] }
        let medH = max(lines.map(\.h).sorted()[lines.count / 2], 1)
        var rows: [[OcrLine]] = []
        for l in lines.sorted(by: { $0.cy < $1.cy }) {
            if let last = rows.last {
                let avg = Double(last.map(\.cy).reduce(0, +)) / Double(last.count)
                if abs(avg - Double(l.cy)) < Double(medH) * 0.55 {
                    rows[rows.count - 1].append(l)
                    continue
                }
            }
            rows.append([l])
        }
        return rows.map { $0.sorted { $0.left < $1.left }.map(\.text).joined(separator: "  ") }
    }

    static func parse(_ rows: [String]) -> ParsedReceipt {
        var out = ParsedReceipt(store: nil, date: nil, total: nil, items: [])
        var pendingQty: Double?

        for raw in rows {
            let row = raw.trimmingCharacters(in: .whitespaces)
            if row.isEmpty { continue }
            let up = row.uppercased()

            if out.store == nil { out.store = stores.first { up.contains($0.0) }?.1 }
            if out.date == nil, let m = dateRe.match(row),
               let d = Int(m.groups[1]), let mo = Int(m.groups[2]), (1...12).contains(mo), (1...31).contains(d) {
                let y = m.groups[3].count == 2 ? "20" + m.groups[3] : m.groups[3]
                out.date = String(format: "%@-%02d-%02d", y, mo, d)
            }
            if out.total != nil { continue } // after the total comes payment info

            if totalRe.test(row) && !up.contains("ZWISCHEN") {
                if let m = priceRe.match(row) { out.total = num(m.groups[1]) }
                continue
            }

            if let qm = qtyRe.match(row) ?? weightRe.match(row) {
                let q = num(qm.groups[1])
                let unit = num(qm.groups[2])
                if let last = out.items.last, last.qty == 1, abs(last.price - q * unit) < 0.06 {
                    // "2 Stk x 1,19" printed under the item line (Rewe, Edeka)
                    out.items[out.items.count - 1].qty = q
                    continue
                }
                let tail = (row as NSString).substring(from: qm.end)
                if let pm = priceRe.match(tail), let last = out.items.last, last.price == 0 {
                    out.items[out.items.count - 1].qty = q
                    out.items[out.items.count - 1].price = num(pm.groups[1])
                } else {
                    pendingQty = q
                }
                continue
            }

            if skipRe.test(row) && !isDiscount(row) && !up.contains("PFAND") { continue }

            guard let pm = priceRe.match(row) else {
                // name without price: the price may follow on a weight line
                let name = clean(row)
                if name.filter(\.isLetter).count >= 3, name == name.uppercased(), (3...40).contains(name.count) {
                    out.items.append(RawItem(name: name, qty: pendingQty ?? 1, price: 0))
                    pendingQty = nil
                }
                continue
            }
            let name = clean((row as NSString).substring(to: pm.start))
            if name.filter(\.isLetter).count < 2 { continue }

            var price = num(pm.groups[1])
            if pm.groups[2] == "-" && price > 0 { price = -price }
            if isDiscount(name) && price > 0 { price = -price }
            out.items.append(RawItem(name: name, qty: pendingQty ?? 1, price: price))
            pendingQty = nil
        }
        out.items.removeAll { $0.price == 0 }
        return out
    }

    private static func clean(_ s: String) -> String {
        s.replacingOccurrences(of: #"^\d{4,}\s*"#, with: "", options: .regularExpression)
            .replacingOccurrences(of: #"\s{2,}"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: CharacterSet(charactersIn: " *.:-"))
    }

    static func num(_ s: String) -> Double {
        Double(s.replacingOccurrences(of: ",", with: ".")) ?? 0
    }
}
