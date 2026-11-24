# Quotation Editor — Android + iOS source bundle

The original Android Studio project is preserved at the root. A separate iOS Xcode project has been added under `ios/`.

## iOS build
1. Use a Mac with Xcode 15 or newer.
2. Open `ios/QuotationEditoriOS.xcodeproj` in Xcode.
3. Select the `QuotationEditoriOS` target and a connected iPhone or iOS Simulator.
4. In Signing & Capabilities, select your Apple Developer Team (a free personal Apple ID team is sufficient for personal device testing).
5. Build and run. For an installable IPA, use Product > Archive, then Distribute App > Development or Ad Hoc as appropriate for your signing setup.

## Included iOS behavior
- Loads `https://blueshieldenergy.in/projects/quotation-editor/` in WKWebView.
- JavaScript bridge intercepts `window.print()` and Blob-anchor PDF downloads.
- Uses WKWebView PDF generation and the iOS Files export picker.
- Provides an iOS document picker for HTML file inputs.
- Restricts normal web navigation to the Blueshield Energy domain.

## Important limitations / validation
- This is an iOS implementation alongside the Android source, not a conversion of Android Java bytecode.
- This environment cannot run Xcode or sign an IPA. The project source is provided; it is not a compiled or tested IPA.
- The live website may use JavaScript/download patterns that require device testing and small adjustments.
- No App Store distribution signing or provisioning profile is included.
- Add a proper AppIcon asset in Xcode before distribution.
- Confirm the site allows WKWebView usage and test PDF layout, file uploads, login/session behavior, and external links on a physical iPhone.
