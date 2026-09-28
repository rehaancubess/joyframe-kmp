// SPDX-License-Identifier: Apache-2.0
// Minimal UIKit host for the Lake Lab framework. Built by run.sh; no Xcode project needed.
import UIKit
import LakeLab

@main
final class LakeLabApp: UIResponder, UIApplicationDelegate {
    var window: UIWindow?

    func application(_ application: UIApplication,
                     didFinishLaunchingWithOptions options: [UIApplication.LaunchOptionsKey: Any]?) -> Bool {
        window = UIWindow(frame: UIScreen.main.bounds)
        window?.rootViewController = MainKt.MainViewController()
        window?.makeKeyAndVisible()
        return true
    }
}
