import SwiftUI

struct TranslateView: View {
    @Environment(AppStore.self) private var store
    @State private var text = ""
    @State private var result = ""
    @State private var busy = false
    @FocusState private var focused: Bool

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    TextEditor(text: $text)
                        .frame(minHeight: 120)
                        .focused($focused)
                } header: {
                    Text("Текст німецькою")
                } footer: {
                    Text("Назва товару, етикетка або склад продукту (Zutaten).")
                }
                Section {
                    Button {
                        focused = false
                        Task { await run() }
                    } label: {
                        HStack {
                            Text("Перекласти")
                            if busy { Spacer(); ProgressView() }
                        }
                    }
                    .disabled(text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty || busy)
                }
                if !result.isEmpty {
                    Section("Переклад") {
                        Text(result).textSelection(.enabled)
                    }
                }
                Section {
                    Text(store.preciseMode
                         ? "Точний режим: перекладає Claude, пояснює терміни і позначає алергени."
                         : "Переклад вбудованим перекладачем iOS (працює офлайн після завантаження мов). Для пояснень і алергенів увімкніть точний режим у налаштуваннях.")
                        .font(.footnote).foregroundStyle(.secondary)
                }
            }
            .navigationTitle("Перекладач")
        }
    }

    private func run() async {
        let input = text.trimmingCharacters(in: .whitespacesAndNewlines)
        busy = true
        result = ""
        defer { busy = false }
        if store.preciseMode {
            do { result = try await ClaudeClient.translate(apiKey: store.apiKey, text: input) }
            catch { result = "Не вдалося перекласти: \(error.localizedDescription)" }
        } else {
            let out = await store.translate([input])
            result = out[input] ?? "Не вдалося перекласти. Перевірте, що українська і німецька завантажені в перекладачі iOS."
        }
    }
}

struct SettingsView: View {
    @Environment(AppStore.self) private var store
    @State private var key = ""
    @State private var saved = false
    @State private var confirmWipe = false

    var body: some View {
        NavigationStack {
            Form {
                Section {
                    Text("Текст чека розпізнається прямо на iPhone, переклад робить вбудований перекладач iOS. Під час першого перекладу iOS запропонує завантажити німецьку та українську мови, після цього все працює без інтернету.")
                } header: {
                    Text("Офлайн-режим (безкоштовно)")
                }

                Section {
                    SecureField("API-ключ (sk-ant-…)", text: $key)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                    Button(saved ? "✓ Збережено" : "Зберегти ключ") {
                        store.saveApiKey(key)
                        saved = true
                    }
                } header: {
                    Text("Точний режим (Claude)")
                } footer: {
                    Text("З власним ключем Anthropic API фото чека надсилається в Claude: краще розшифровує скорочення, знижки й Pfand. Оплачується з вашого API-акаунта (зазвичай кілька центів за чек). Ключ зберігається в Keychain. Залиште поле порожнім, щоб працювати офлайн.\n\nЗараз: \(store.preciseMode ? "точний режим" : "офлайн-режим").")
                }

                Section {
                    Text("Збережено чеків: \(store.receipts.count)")
                    Button("Видалити всі чеки", role: .destructive) { confirmWipe = true }
                } header: {
                    Text("Дані")
                } footer: {
                    Text("Дані зберігаються лише на цьому пристрої.")
                }
            }
            .navigationTitle("Налаштування")
            .onAppear { key = store.apiKey; saved = false }
            .onChange(of: key) { _, _ in saved = false }
            .confirmationDialog("Видалити всі чеки?", isPresented: $confirmWipe, titleVisibility: .visible) {
                Button("Видалити всі", role: .destructive) { store.deleteAll() }
            }
        }
    }
}
