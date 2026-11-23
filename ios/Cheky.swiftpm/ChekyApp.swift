import SwiftUI

@main
struct ChekyApp: App {
    @State private var store = AppStore()

    var body: some Scene {
        WindowGroup {
            ContentView()
                .environment(store)
                .tint(Color(red: 0.12, green: 0.44, blue: 0.36))
        }
    }
}

struct ContentView: View {
    @Environment(AppStore.self) private var store

    var body: some View {
        @Bindable var store = store
        TabView(selection: $store.selectedTab) {
            ScanView()
                .tabItem { Label("Чек", systemImage: "doc.text.viewfinder") }
                .tag(0)
            SummaryView()
                .tabItem { Label("Підсумок", systemImage: "chart.bar.xaxis") }
                .tag(1)
            TranslateView()
                .tabItem { Label("Переклад", systemImage: "character.bubble") }
                .tag(2)
            SettingsView()
                .tabItem { Label("Налаштування", systemImage: "gearshape") }
                .tag(3)
        }
        // Runs Apple's Translation session whenever the store asks for a translation.
        .translationTask(store.translationConfig) { session in
            await store.performTranslation(session)
        }
    }
}

/// Horizontal bar chart used on several screens.
struct CategoryBars: View {
    struct Row: Identifiable {
        let name: String
        let value: Double
        var id: String { name }
    }

    let rows: [Row]

    init(_ data: [String: Double]) {
        rows = data.filter { abs($0.value) > 0.001 }
            .map { Row(name: $0.key, value: $0.value) }
            .sorted { $0.value > $1.value }
    }

    var body: some View {
        if rows.isEmpty {
            Text("—").foregroundStyle(.secondary)
        } else {
            VStack(spacing: 8) {
                let maxV = rows.map { abs($0.value) }.max() ?? 1
                ForEach(rows) { r in
                    HStack(spacing: 8) {
                        Text(r.name).font(.caption).lineLimit(1).frame(width: 120, alignment: .leading)
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Color(.systemGray5))
                                Capsule().fill(r.value < 0 ? Color.red : Color.accentColor)
                                    .frame(width: max(4, g.size.width * abs(r.value) / maxV))
                            }
                        }
                        .frame(height: 10)
                        Text(eur(r.value)).font(.caption.monospacedDigit()).frame(width: 72, alignment: .trailing)
                    }
                }
            }
            .padding(.vertical, 4)
        }
    }
}
