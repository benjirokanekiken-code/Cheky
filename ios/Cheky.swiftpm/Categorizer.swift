import Foundation

/// Keyword categories for German product names.
/// "^X" = a word starting with X, "=X" = exactly the word X, otherwise substring. First match wins.
enum Categorizer {
    private static let pfandKeys = ["PFAND", "LEERGUT", "^EINWEG", "^MEHRWEG"]

    private static let rules: [(String, [String])] = [
        (Cat.frozen, ["=TK", "TIEFK", "PIZZA", "POMMES", "=EIS", "EISCREME", "RAHMSPINAT", "FISCHSTÄBCHEN", "KROKETTEN"]),
        (Cat.sweets, ["SCHOKO", "KEKS", "CHIPS", "GUMMI", "BONBON", "RIEGEL", "WAFFEL", "PRALINE", "POPCORN", "SNACK",
                      "KAUGUMMI", "HARIBO", "MILKA", "NUTELLA", "^NÜSSE", "NUSSMISCH", "ERDNUSS", "SALZSTANG", "LAKRITZ",
                      "KUCHEN", "TORTE", "CRACKER"]),
        (Cat.drinks, ["WASSER", "SAFT", "COLA", "LIMO", "BIER", "^WEIN", "ROTW", "WEISSW", "WEIßW", "ROSEW", "SEKT", "SCHORLE",
                      "FANTA", "SPRITE", "MINERAL", "SPRUDEL", "ENERGY", "EISTEE", "NEKTAR", "^PILS", "WODKA", "VODKA",
                      "KÖLSCH", "RADLER", "SMOOTHIE"]),
        (Cat.dairy, ["MILCH", "KÄSE", "KAESE", "JOGHURT", "^JOGH", "QUARK", "BUTTER", "SAHNE", "SKYR", "KEFIR", "MOZZ", "GOUDA",
                     "EDAMER", "FRISCHK", "SCHMAND", "CREME FR", "CRÈME FR", "EIER", "FETA", "PARMESAN", "EMMENTALER",
                     "PUDDING", "GRIESS", "HALLOUMI"]),
        (Cat.meat, ["HÄHN", "HAEHN", "HUHN", "PUTE", "RIND", "SCHWEIN", "^SCHW", "HACK", "WURST", "SALAMI", "SCHINKEN", "SPECK",
                    "LACHS", "THUNFISCH", "FISCH", "GARNELE", "HERING", "FILET", "STEAK", "KOTELETT", "BRATW", "WIENER", "LEBERW",
                    "GEFLÜGEL", "GEFL", "KASSLER", "AUFSCHNITT", "FRIKADELLE", "GYROS", "MAKRELE", "FORELLE", "LEBERKÄSE"]),
        (Cat.bread, ["BROT", "BRÖTCHEN", "BROETCHEN", "TOAST", "BAGUETTE", "CROISSANT", "BREZEL", "BREZN", "LAUGEN", "SEMMEL",
                     "CIABATTA", "^WECK", "BERLINER", "DONUT", "MUFFIN", "PLUNDER", "ZWIEBACK"]),
        (Cat.veg, ["BANANE", "APFEL", "ÄPFEL", "AEPFEL", "BIRNE", "TOMATE", "GURKE", "KARTOFFEL", "ZWIEBEL", "PAPRIKA", "SALAT",
                   "MÖHRE", "MOEHRE", "KAROTTE", "ZITRONE", "LIMETTE", "ORANGE", "TRAUBE", "ERDBEER", "KOHL", "BROKKOLI",
                   "BROCCOLI", "ZUCCHINI", "PILZ", "CHAMPIGNON", "KNOBLAUCH", "AVOCADO", "MANDARINE", "CLEMENTINE", "KIWI",
                   "BEEREN", "SPINAT", "LAUCH", "RADIESCHEN", "MELONE", "OBST", "GEMÜSE", "GEMUESE", "INGWER", "AUBERGINE",
                   "PFIRSICH", "NEKTARINE", "ANANAS", "MANGO", "KÜRBIS", "SPARGEL", "PETERSILIE", "DILL", "SCHNITTLAUCH", "RUCOLA"]),
        (Cat.grocery, ["NUDEL", "SPAGHETTI", "PASTA", "PENNE", "FUSILLI", "=REIS", "^REIS", "MEHL", "ZUCKER", "SALZ", "ÖL", "OEL",
                       "ESSIG", "MÜSLI", "MUESLI", "HAFER", "FLOCKEN", "KAFFEE", "=TEE", "TEE ", "HONIG", "MARMELADE",
                       "KONFITÜRE", "KETCHUP", "SENF", "MAYO", "SOSSE", "SOßE", "SAUCE", "GEWÜRZ", "PFEFFER", "LINSEN", "BOHNEN",
                       "^MAIS", "KONSERVE", "CORNFLAKES", "BRÜHE", "TOMATENMARK", "PASSATA", "BUCHWEIZEN", "HEFE",
                       "BACKPULVER", "KAKAO", "OLIVEN", "ERBSEN", "KICHERERB"]),
        (Cat.household, ["SPÜL", "SPUEL", "WASCHMITTEL", "WASCHPULVER", "REINIGER", "TOILETTENPAP", "KÜCHENROLLE", "KUECHENR",
                         "MÜLLBEUTEL", "MUELLB", "ALUFOLIE", "FRISCHHALTE", "SCHWAMM", "WEICHSPÜL", "ENTKALK", "TABS", "WC-",
                         "BACKPAPIER", "SERVIETTE", "KERZE", "BATTERIE", "GLÜHBIRNE"]),
        (Cat.hygiene, ["SHAMPOO", "DUSCH", "SEIFE", "ZAHNP", "ZAHNB", "ZAHNC", "^DEO", "CREME", "WINDEL", "TAMPON", "BINDEN",
                       "RASIER", "TASCHENT", "WATTE", "PFLASTER", "LOTION", "HAARGEL", "SPÜLUNG", "SONNENSCH", "FEUCHTT", "MUNDSP"]),
    ]

    static func categorize(_ de: String) -> String {
        let up = de.uppercased()
        let tokens = up.components(separatedBy: CharacterSet.alphanumerics.inverted).filter { !$0.isEmpty }
        if pfandKeys.contains(where: { hit($0, up, tokens) }) { return Cat.pfand }
        if ReceiptParser.isDiscount(de) { return Cat.discount }
        for (cat, keys) in rules where keys.contains(where: { hit($0, up, tokens) }) {
            return cat
        }
        return Cat.other
    }

    private static func hit(_ k: String, _ up: String, _ tokens: [String]) -> Bool {
        if k.hasPrefix("^") { let p = String(k.dropFirst()); return tokens.contains { $0.hasPrefix(p) } }
        if k.hasPrefix("=") { let p = String(k.dropFirst()); return tokens.contains(p) }
        return up.contains(k)
    }
}

/// Expands typical German receipt abbreviations into words a translator understands.
enum ReceiptText {
    private static let abbr: [String: String] = [
        "KAESE": "Käse", "MOEHREN": "Möhren", "BROETCHEN": "Brötchen", "AEPFEL": "Äpfel",
        "GEMUESE": "Gemüse", "MUESLI": "Müsli", "HAEHNCHEN": "Hähnchen", "HAEHN": "Hähnchen",
        "HAEH": "Hähnchen", "HÄHN": "Hähnchen", "SUESS": "süß", "OEL": "Öl", "SCHW": "Schweine",
        "SCHWEINEF": "Schweinefleisch", "HACKFL": "Hackfleisch", "GEM": "gemischt", "KL": "klein",
        "GR": "groß", "ST": "Stück", "STK": "Stück", "FL": "Flasche", "PCK": "Packung",
        "PKG": "Packung", "VOLLM": "Vollmilch", "H": "haltbare", "H-MILCH": "haltbare Milch",
        "JOGH": "Joghurt", "JOGHU": "Joghurt", "FRISCHK": "Frischkäse", "SCHLAGS": "Schlagsahne",
        "TK": "tiefgekühlt", "GEFL": "Geflügel", "ORIG": "Original", "NAT": "natur",
        "MINERALW": "Mineralwasser", "KART": "Kartoffeln", "ZWIEB": "Zwiebeln", "TOM": "Tomaten",
        "BAN": "Bananen", "CHAMP": "Champignons", "SCHOK": "Schokolade", "SCHOKO": "Schokolade",
        "VOLLK": "Vollkorn", "WEIZENM": "Weizenmehl", "SPUELM": "Spülmittel", "WASCHM": "Waschmittel",
        "TOILETTENP": "Toilettenpapier", "ZAHNC": "Zahncreme", "DUSCHG": "Duschgel", "ROTW": "Rotwein",
        "WEISSW": "Weißwein", "APFELS": "Apfelsaft", "ORANGENS": "Orangensaft", "MOZZ": "Mozzarella",
        "BUTTERM": "Buttermilch", "EINWEG": "Einweg", "MEHRW": "Mehrweg", "BIO": "Bio",
        "FETTARM": "fettarm", "FRISCH": "frisch", "GESCHN": "geschnitten", "GER": "gerieben",
        "GERAEUCH": "geräuchert", "GEKOCHT": "gekocht", "PAPR": "Paprika", "GURK": "Gurken",
        "KUECHENR": "Küchenrolle", "MUELLB": "Müllbeutel", "WEICHSP": "Weichspüler",
    ]
    /// Receipts often write umlauts as AE/OE/UE inside compound words.
    private static let umlauts: [(String, String)] = [
        ("BROETCHEN", "BRÖTCHEN"), ("KAESE", "KÄSE"), ("GEMUESE", "GEMÜSE"), ("MOEHRE", "MÖHRE"),
        ("AEPFEL", "ÄPFEL"), ("HAEHNCHEN", "HÄHNCHEN"), ("MUESLI", "MÜSLI"), ("SUESS", "SÜSS"),
        ("KUECHE", "KÜCHE"), ("SPUEL", "SPÜL"), ("MUELL", "MÜLL"), ("OEL", "ÖL"), ("KUERBIS", "KÜRBIS"),
        ("GEFLUEGEL", "GEFLÜGEL"), ("SOSSE", "SOßE"), ("GETRAENK", "GETRÄNK"), ("KNOEDEL", "KNÖDEL"),
    ]
    private static let unitRe = Rx(#"^(\d+(?:[.,]\d+)?)(G|GR|KG|L|ML|CL|ST|STK|%)$"#)

    static func expand(_ name: String) -> String {
        let spaced = name.replacingOccurrences(of: #"\.(?=\p{L})"#, with: ". ", options: .regularExpression)
        return spaced.split(whereSeparator: { $0.isWhitespace }).map { sub -> String in
            let original = String(sub)
            let trimmed = original.trimmingCharacters(in: CharacterSet(charactersIn: "."))
            var bare = trimmed.uppercased()
            if abbr[bare] == nil {
                for (a, b) in umlauts { bare = bare.replacingOccurrences(of: a, with: b) }
            }
            if let full = abbr[bare] { return full }
            if let m = unitRe.match(bare) {
                if m.groups[2] == "%" { return original }
                let unit: String
                switch m.groups[2] {
                case "GR": unit = "g"
                case "ST", "STK": unit = "Stück"
                default: unit = m.groups[2].lowercased()
                }
                return "\(m.groups[1]) \(unit)"
            }
            let isUpper = original == original.uppercased()
            if isUpper && original.contains(where: \.isLetter) {
                return String(bare.prefix(1)) + bare.dropFirst().lowercased() // German nouns are capitalised
            }
            return trimmed
        }.joined(separator: " ")
    }
}
