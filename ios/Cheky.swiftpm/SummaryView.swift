import SwiftUI

struct SummaryView: View {
    @Environment(AppStore.self) private var store
    @State private var period = Period.month

    enum Period: String, CaseIterable, Identifiable {
        case month = "Цей місяць", prev = "Минулий", year = "Рік", all = "Усе"
        var id: String { rawValue }
    }

    struct TopItem: Identifiable {
        let name: String
        let count: Double
        let sum: Double
        var id: String { name }
    }

    private var list: [Receipt] {
        store.receipts.filter { inPeriod($0) }
            .sorted { ($0.date, $0.createdAt) > ($1.date, $1.createdAt) }
    }

    var body: some View {
        let list = self.list
        let items = list.flatMap(\.items)
        let total = list.reduce(0) { $0 + $1.total }
        let byCat = Dictionary(grouping: items, by: \.category).mapValues { $0.reduce(0) { $0 + $1.price } }
        let byShop = Dictionary(grouping: list, by: \.store).mapValues { $0.reduce(0) { $0 + $1.total } }
        let top = topItems(items)

        NavigationStack {
            List {
                Section {
                    Picker("Період", selection: $period) {
                        ForEach(Period.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)
                    HStack(spacing: 8) {
                        kpi(eur(total), "витрачено")
                        kpi("\(list.count)", "чеків")
                        kpi(eur(list.isEmpty ? 0 : total / Double(list.count)), "середній чек")
                    }
                }
                .listRowBackground(Color.clear)
                .listRowInsets(EdgeInsets(top: 4, leading: 0, bottom: 4, trailing: 0))

                if list.isEmpty {
                    Section {
                        Text(store.receipts.isEmpty
                             ? "Ще немає збережених чеків. Додайте перший у вкладці «Чек»."
                             : "За цей період чеків немає.")
                            .foregroundStyle(.secondary)
                    }
                } else {
                    Section("За категоріями") { CategoryBars(byCat) }
                    Section("За магазинами") { CategoryBars(byShop) }
                    Section("Найчастіші покупки") {
                        ForEach(top) { t in
                            HStack {
                                Text(t.name)
                                Spacer()
                                Text("\(fmtQty(t.count)) × · \(eur(t.sum))").foregroundStyle(.secondary).monospacedDigit()
                            }
                        }
                    }
                    Section("Чеки") {
                        ForEach(list) { r in
                            NavigationLink {
                                ReceiptDetail(receipt: r)
                            } label: {
                                HStack {
                                    VStack(alignment: .leading) {
                                        Text(r.store).fontWeight(.semibold)
                                        Text("\(DateFormat.pretty(r.date)) · \(r.items.count) позицій")
                                            .font(.caption).foregroundStyle(.secondary)
                                    }
                                    Spacer()
                                    Text(eur(r.total)).fontWeight(.bold).monospacedDigit()
                                }
                            }
                        }
                    }
                    Section {
                        ShareLink(item: shareText(total: total, count: list.count, byCat: byCat, top: top)) {
                            Label("Поділитися підсумком", systemImage: "square.and.arrow.up")
                        }
                    }
                }
            }
            .navigationTitle("Підсумок")
        }
    }

    private func kpi(_ value: String, _ label: String) -> some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(value).font(.headline.monospacedDigit()).lineLimit(1).minimumScaleFactor(0.7)
            Text(label).font(.caption).foregroundStyle(.secondary)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(12)
        .background(Color(.secondarySystemGroupedBackground), in: RoundedRectangle(cornerRadius: 12))
    }

    private func inPeriod(_ r: Receipt) -> Bool {
        if period == .all { return true }
        guard let d = r.dateValue else { return false }
        let cal = Calendar.current
        let now = Date()
        switch period {
        case .year: return cal.isDate(d, equalTo: now, toGranularity: .year)
        case .month: return cal.isDate(d, equalTo: now, toGranularity: .month)
        case .prev:
            guard let prev = cal.date(byAdding: .month, value: -1, to: now) else { return false }
            return cal.isDate(d, equalTo: prev, toGranularity: .month)
        case .all: return true
        }
    }

    private func topItems(_ items: [Item]) -> [TopItem] {
        let real = items.filter { $0.price > 0 && $0.category != Cat.pfand && $0.category != Cat.discount }
        return Dictionary(grouping: real, by: { $0.uk.lowercased().trimmingCharacters(in: .whitespaces) })
            .map { _, g in TopItem(name: g[0].uk, count: g.reduce(0) { $0 + $1.qty }, sum: g.reduce(0) { $0 + $1.price }) }
            .sorted { ($0.count, $0.sum) > ($1.count, $1.sum) }
            .prefix(10)
            .map { $0 }
    }

    private func shareText(total: Double, count: Int, byCat: [String: Double], top: [TopItem]) -> String {
        var s = "🧾 Покупки — \(period.rawValue)\nВитрачено: \(eur(total)) (\(count) чеків)\n\nЗа категоріями:\n"
        for (k, v) in byCat.sorted(by: { $0.value > $1.value }) { s += "• \(k): \(eur(v))\n" }
        if !top.isEmpty {
            s += "\nНайчастіше купуємо:\n"
            for t in top.prefix(5) { s += "• \(t.name) — \(fmtQty(t.count)) раз(и)\n" }
        }
        return s
    }
}

struct ReceiptDetail: View {
    let receipt: Receipt
    @Environment(AppStore.self) private var store
    @Environment(\.dismiss) private var dismiss
    @State private var confirmDelete = false

    var body: some View {
        List {
            Section {
                ForEach(receipt.items) { ItemRow(item: $0) }
            }
            Section {
                HStack {
                    Text("Разом").bold()
                    Spacer()
                    Text(eur(receipt.total)).bold().monospacedDigit()
                }
            }
            Section {
                Button("Видалити чек", role: .destructive) { confirmDelete = true }
            }
        }
        .navigationTitle("\(receipt.store) · \(DateFormat.pretty(receipt.date))")
        .navigationBarTitleDisplayMode(.inline)
        .toolbar {
            Button("Редагувати") { store.openReceipt(receipt) }
        }
        .confirmationDialog("Видалити цей чек?", isPresented: $confirmDelete, titleVisibility: .visible) {
            Button("Видалити", role: .destructive) {
                store.deleteReceipt(receipt.id)
                dismiss()
            }
        }
    }
}
