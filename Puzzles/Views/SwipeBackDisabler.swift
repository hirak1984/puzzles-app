import SwiftUI
import UIKit

extension View {
    /// Turns off the navigation stack's swipe-back gestures while this screen
    /// is visible, so a one-finger drag on a game board (e.g. a Hashi bridge
    /// drawn to the right) isn't taken as "go back". iOS 26 made swipe-back
    /// work from anywhere in the content, not just the left edge, which is
    /// what made this easy to trigger. The Back button still works.
    func disablesSwipeBack() -> some View {
        background(SwipeBackDisabler().frame(width: 0, height: 0))
    }
}

private struct SwipeBackDisabler: UIViewControllerRepresentable {
    func makeUIViewController(context: Context) -> Controller { Controller() }
    func updateUIViewController(_ controller: Controller, context: Context) {}

    final class Controller: UIViewController {
        /// Recognizers we turned off, with their previous `isEnabled`.
        private var disabled: [(UIGestureRecognizer, Bool)] = []

        override func viewDidAppear(_ animated: Bool) {
            super.viewDidAppear(animated)
            guard disabled.isEmpty, let nav = navigationController else { return }
            var recognizers = [nav.interactivePopGestureRecognizer]
            if #available(iOS 26.0, *) {
                recognizers.append(nav.interactiveContentPopGestureRecognizer)
            }
            disabled = recognizers.compactMap { $0 }.map { ($0, $0.isEnabled) }
            for (recognizer, _) in disabled { recognizer.isEnabled = false }
        }

        override func viewWillDisappear(_ animated: Bool) {
            super.viewWillDisappear(animated)
            for (recognizer, wasEnabled) in disabled { recognizer.isEnabled = wasEnabled }
            disabled = []
        }
    }
}
