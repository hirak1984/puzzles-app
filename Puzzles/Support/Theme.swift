import SwiftUI
import UIKit

/// Color tokens ported from the web app's `styles.css`, shared with Convene
/// and Angles. `light`/`dark` mirror the `:root` and `[data-theme="dark"]`
/// blocks exactly. Values resolve against the trait collection, so they
/// track whatever `.preferredColorScheme` the app root applies (light,
/// dark, or system via `nil`).
extension Color {
    init(light: String, dark: String) {
        self = Color(uiColor: UIColor { traits in
            traits.userInterfaceStyle == .dark ? UIColor(hex: dark) : UIColor(hex: light)
        })
    }
}

extension UIColor {
    convenience init(hex: String) {
        var s = hex.trimmingCharacters(in: CharacterSet(charactersIn: "#"))
        if s.count == 6 { s += "FF" }
        var v: UInt64 = 0
        Scanner(string: s).scanHexInt64(&v)
        self.init(
            red: CGFloat((v >> 24) & 0xFF) / 255,
            green: CGFloat((v >> 16) & 0xFF) / 255,
            blue: CGFloat((v >> 8) & 0xFF) / 255,
            alpha: CGFloat(v & 0xFF) / 255
        )
    }
}

enum Theme {
    static let bg = Color(light: "#FAFAF7", dark: "#161614")
    static let bgElevated = Color(light: "#FFFFFF", dark: "#1F1F1C")
    static let bgRaised = Color(light: "#F2F2EE", dark: "#252522")
    static let border = Color(light: "#E5E5E0", dark: "#33332F")
    static let borderStrong = Color(light: "#C8C8C2", dark: "#4D4D48")
    static let text = Color(light: "#1A1A1A", dark: "#EDEDE8")
    static let textMuted = Color(light: "#555550", dark: "#A8A8A2")
    static let textSubtle = Color(light: "#8E8E88", dark: "#7A7A74")
    static let accent = Color(light: "#0F6E56", dark: "#5DCAA5")
    static let accentBg = Color(light: "#E1F5EE", dark: "#1A3A30")
    static let accentContrast = Color(light: "#FFFFFF", dark: "#0B0D10")
    static let danger = Color(light: "#A63D2E", dark: "#E0685B")
    static let dangerBg = Color(light: "#F7E1DD", dark: "#3A1F1B")
    /// The player's own track/bridges, vs. system(-given) ones — same accent
    /// used for the Train Tracks "your track" color.
    static let user = Color(light: "#2B6CB0", dark: "#63A4EF")

    // Google Fonts "Inter" / "Source Serif 4", bundled as variable fonts and
    // registered in Info.plist (UIAppFonts) — same families the web app
    // loads, so headings/body text match.
    static func sans(_ size: CGFloat, weight: Font.Weight = .regular) -> Font {
        .custom("Inter", size: size).weight(weight)
    }
    static func serif(_ size: CGFloat, weight: Font.Weight = .medium) -> Font {
        .custom("Source Serif 4", size: size).weight(weight)
    }
}

/// Three-state light/dark/system toggle, mirroring the web app's header
/// button and `puzzles-theme` localStorage key (here: UserDefaults).
enum ThemeMode: String, CaseIterable {
    case light, dark, system

    var next: ThemeMode {
        switch self {
        case .light: return .dark
        case .dark: return .system
        case .system: return .light
        }
    }

    var colorScheme: ColorScheme? {
        switch self {
        case .light: return .light
        case .dark: return .dark
        case .system: return nil
        }
    }

    var icon: String {
        switch self {
        case .light: return "sun.max"
        case .dark: return "moon.fill"
        case .system: return "circle.lefthalf.filled"
        }
    }
}
