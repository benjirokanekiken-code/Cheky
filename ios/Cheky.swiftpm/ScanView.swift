import SwiftUI
import PhotosUI
import VisionKit

struct ScanView: View {
    @Environment(AppStore.self) private var store
    @State private var pickerItems: [PhotosPickerItem] = []
    @State private var showScanner = false
    @State private var editing: EditTarget?

    struct EditTarget: Identifiable {
        let index: Int
        var id: Int { index }
    }

    var body: some View {
        NavigationStack {
            List {
                Section {
                    HStack(spacing: 10) {
                        Button {
                            showScanner = true
                        } label: {
                            Label("Сканувати", systemImage: "doc.viewfinder").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.borderedProminent)
                        .disabled(!VNDocumentCameraViewController.isSupported)

                        PhotosPicker(selection: $pickerItems, maxSelectionCount: 10, matching: .images) {
                            Label("Фото", systemImage: "photo.on.rectangle").frame(maxWidth: .infinity)
                        }
                        .buttonStyle(.bordered)
                    }
                    .controlSize(.large)

                    if !store.images.isEmpty {
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(spacing: 8) {
                                ForEach(store.images.indices, id: \.self) { i in
                                    Image(uiImage: store.images[i])
                                        .resizable().scaledToFill()
                                        .frame(width: 64, height: 86)
                                        .clipShape(RoundedRectangle(cornerRadius: 8))
                                }
                            }
                        }
                        HStack {
                            Button {
                                Task { await store.analyze() }
                            } label: {
                                HStack {
                                    if store.busy { ProgressView().tint(.white) }
                                    Text("Розпізнати і перекласти")
                                }
                            }
                            .buttonStyle(.borderedProminent)
                            .disabled(store.busy)
                            Spacer()
                            Button("Очистити", role: .destructive) { store.clearScan() }
                                .buttonStyle(.borderless)
                                .disabled(store.busy)
                        }
                    }
                    if let msg = store.status {
                        Text(msg).font(.callout)
                            .foregroundStyle(store.statusIsError ? Color.red : Color.secondary)
                    }
                } footer: {
                    if store.images.isEmpty && store.draft == nil {
                        Text("Скануйте чек камерою — iOS сама обріже й вирівняє його. Довгий чек можна зняти кількома сторінками.")
                    }
                }

                if let d = store.draft {
                    draftSections(d)
                }
            }
            .navigationTitle("Новий чек")
            .onChange(of: pickerItems) { _, items in
                guard !items.isEmpty else { return }
                Task {
                    var loaded: [UIImage] = []
                    for item in items {
                        if let data = try? await item.loadTransferable(type: Data.self), let img = UIImage(data: data) {
                            loaded.append(img)
                        }
                    }
                    store.addImages(loaded)
                    pickerItems = []
                }
            }
            .fullScreenCover(isPresented: $showScanner) {
                DocumentScanner { store.addImages($0) }.ignoresSafeArea()
            }
            .sheet(item: $editing) { target in
                if let item = store.draft?.items[safe: target.index] {
                    ItemEditor(item: item) { store.updateItem(target.index, $0) } onDelete: {
                        store.removeItems(at: IndexSet(integer: target.index))
                    }
                }
            }
        }
    }

    @ViewBuilder
    private func draftSections(_ d: Receipt) -> some View {
        Section("Чек") {
            TextField("Магазин", text: Binding(get: { store.draft?.store ?? "" }, set: { store.updateHeader(store: $0) }))
            DatePicker("Дата", selection: Binding(
                get: { store.draft?.dateValue ?? Date() },
                set: { store.updateHeader(date: DateFormat.iso.string(from: $0)) }
            ), displayedComponents: .date)
        }

        Section {
            ForEach(Array(d.items.enumerated()), id: \.element.id) { i, item in
                Button { editing = EditTarget(index: i) } label: { ItemRow(item: item) }
                    .buttonStyle(.plain)
            }
            .onDelete { store.removeItems(at: $0) }
            Button("+ Додати позицію") { editing = EditTarget(index: store.addItem()) }
        } header: {
            Text("Товари (\(d.items.count))")
        } footer: {
            Text("Торкніться рядка, щоб виправити. Ваш переклад запам'ятається для наступних чеків.")
        }

        Section {
            HStack {
                Text("Разом").font(.title3.bold())
                Spacer()
                Text(eur(d.total)).font(.title3.bold().monospacedDigit())
            }
            if abs(d.itemsSum - d.total) > 0.05 {
                Text("⚠️ Сума позицій \(eur(d.itemsSum)) не збігається з підсумком чека.")
                    .font(.footnote).foregroundStyle(.red)
                Button("Взяти суму позицій") { store.updateHeader(total: d.itemsSum) }
            }
        }

        Section("За категоріями") {
            CategoryBars(Dictionary(grouping: d.items, by: \.category).mapValues { $0.reduce(0) { $0 + $1.price } })
        }

        Section {
            if store.draftSaved {
                Label("Збережено", systemImage: "checkmark.circle.fill").foregroundStyle(.green)
                Button("Новий чек") { store.clearScan() }
            } else {
                Button {
                    store.saveDraft()
                } label: {
                    Text("Зберегти чек").frame(maxWidth: .infinity)
                }
                .buttonStyle(.borderedProminent)
                .controlSize(.large)
            }
        }
    }
}

struct ItemRow: View {
    let item: Item

    var body: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text(item.uk).fontWeight(.semibold)
                if !item.de.isEmpty {
                    Text(item.de).font(.caption).foregroundStyle(.secondary)
                }
                Text(item.category).font(.caption2).foregroundStyle(Color.accentColor)
            }
            Spacer()
            VStack(alignment: .trailing, spacing: 2) {
                Text(eur(item.price)).fontWeight(.semibold).monospacedDigit()
                    .foregroundStyle(item.price < 0 ? Color.red : Color.primary)
                if item.qty != 1 {
                    Text("× \(fmtQty(item.qty))").font(.caption).foregroundStyle(.secondary)
                }
            }
        }
        .contentShape(Rectangle())
    }
}

struct ItemEditor: View {
    let item: Item
    let onSave: (Item) -> Void
    let onDelete: () -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var uk = ""
    @State private var de = ""
    @State private var price = ""
    @State private var qty = ""
    @State private var category = Cat.other

    var body: some View {
        NavigationStack {
            Form {
                Section("Назва") {
                    TextField("Українською", text: $uk)
                    TextField("З чека (німецькою)", text: $de)
                }
                Section {
                    TextField("Ціна, €", text: $price).keyboardType(.numbersAndPunctuation)
                    TextField("Кількість", text: $qty).keyboardType(.decimalPad)
                } footer: {
                    Text("Знижки вводьте з мінусом, напр. -0,50")
                }
                Picker("Категорія", selection: $category) {
                    ForEach(Cat.all, id: \.self) { Text($0).tag($0) }
                }
                Section {
                    Button("Видалити позицію", role: .destructive) { onDelete(); dismiss() }
                }
            }
            .navigationTitle("Позиція")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) { Button("Скасувати") { dismiss() } }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Зберегти") {
                        var new = item
                        new.de = de.trimmingCharacters(in: .whitespaces)
                        let ukTrim = uk.trimmingCharacters(in: .whitespaces)
                        new.uk = ukTrim.isEmpty ? new.de : ukTrim
                        new.price = round2(ReceiptParser.num(price.trimmingCharacters(in: .whitespaces)))
                        let q = ReceiptParser.num(qty.trimmingCharacters(in: .whitespaces))
                        new.qty = q > 0 ? q : 1
                        new.category = category
                        onSave(new)
                        dismiss()
                    }
                }
            }
            .onAppear {
                uk = item.uk
                de = item.de
                price = item.price == 0 ? "" : String(format: "%.2f", item.price).replacingOccurrences(of: ".", with: ",")
                qty = fmtQty(item.qty)
                category = item.category
            }
        }
        .presentationDetents([.large])
    }
}

/// Apple's document scanner: auto-detects the receipt edges and straightens it.
struct DocumentScanner: UIViewControllerRepresentable {
    var onScan: ([UIImage]) -> Void
    @Environment(\.dismiss) private var dismiss

    func makeCoordinator() -> Coordinator { Coordinator(self) }

    func makeUIViewController(context: Context) -> VNDocumentCameraViewController {
        let vc = VNDocumentCameraViewController()
        vc.delegate = context.coordinator
        return vc
    }

    func updateUIViewController(_ vc: VNDocumentCameraViewController, context: Context) {}

    final class Coordinator: NSObject, VNDocumentCameraViewControllerDelegate {
        let parent: DocumentScanner
        init(_ parent: DocumentScanner) { self.parent = parent }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController,
                                          didFinishWith scan: VNDocumentCameraScan) {
            let images = (0..<scan.pageCount).map { scan.imageOfPage(at: $0) }
            parent.onScan(images)
            parent.dismiss()
        }

        func documentCameraViewControllerDidCancel(_ controller: VNDocumentCameraViewController) {
            parent.dismiss()
        }

        func documentCameraViewController(_ controller: VNDocumentCameraViewController, didFailWithError error: Error) {
            parent.dismiss()
        }
    }
}

extension Array {
    subscript(safe index: Int) -> Element? { indices.contains(index) ? self[index] : nil }
}
