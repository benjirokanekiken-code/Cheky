// swift-tools-version: 5.9

// Open this folder in Xcode (Mac) or Swift Playgrounds (iPad).
import PackageDescription
import AppleProductTypes

let package = Package(
    name: "Cheky",
    platforms: [
        .iOS("18.0")
    ],
    products: [
        .iOSApplication(
            name: "Чеки",
            targets: ["AppModule"],
            bundleIdentifier: "ua.cheky.app",
            teamIdentifier: "",
            displayVersion: "1.0",
            bundleVersion: "1",
            accentColor: .presetColor(.green),
            supportedDeviceFamilies: [
                .pad,
                .phone
            ],
            supportedInterfaceOrientations: [
                .portrait,
                .landscapeRight,
                .landscapeLeft,
                .portraitUpsideDown(.when(deviceFamilies: [.pad]))
            ],
            capabilities: [
                .camera(purposeString: "Камера потрібна, щоб сканувати чеки."),
                .photoLibrary(purposeString: "Доступ до фото потрібен, щоб вибрати знімок чека.")
            ],
            appCategory: .finance
        )
    ],
    targets: [
        .executableTarget(
            name: "AppModule",
            path: "."
        )
    ]
)
