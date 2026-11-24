# Quotation Editor — Android WebView app

Web app: https://blueshieldenergy.in/projects/quotation-editor/

## Changes in this build
- Uses the supplied Glossy Golden Solar Power Emblem artwork for launcher icons.
- Adaptive foreground artwork is inset into the Android launcher safe zone so the gold outer rim is less likely to be clipped by circular, squircle, or legacy launcher masks.
- Uses the webpage's responsive viewport settings rather than forcing desktop overview scaling.
- Supports PDF exports triggered by `window.print()` by rendering the current WebView page to PDF and saving it directly to `Internal Storage/Quotations Blue Shield Energy/`.
- Supports JavaScript Blob-anchor PDF downloads and saves those into the same folder.
- Retains regular HTTPS download handling and file chooser support.

## Storage permission behavior
Android does not allow an app to show a runtime storage permission dialog during installation. Android 6–9 requests legacy write permission on first launch. Android 10+ uses scoped storage/MediaStore and does not request broad storage access. PDF output is written to the top-level shared-storage folder `Quotations Blue Shield Energy` on Android 10+ and to the same folder under shared internal storage on Android 6–9.

## PDF behavior and Android limitation
The quotation page's `window.print()` call is handled by the app's WebView print adapter and saved directly as a PDF in the requested folder. This is intentionally a direct-save workflow; it does not show Android's system Print Destination chooser. Android's system "Save as PDF" provider owns its own destination picker and Android does not provide a supported API for forcing that system UI to save into an app-selected folder. If a user invokes a separate system print destination flow outside the page's Download PDF button, they must choose the folder in that system UI.

## Build
1. Open this project folder in Android Studio.
2. Allow Gradle sync to complete (Android SDK Platform 35 required).
3. Run on a physical Android phone to test the actual WebView and PDF output.
4. To make an APK: Build > Build Bundle(s) / APK(s) > Build APK(s).

## Test checklist
- Open the live quotation editor and compare mobile layout with Chrome.
- Tap Download PDF and verify all quotation pages and images appear.
- Verify the PDF appears in the phone's file manager at `Internal Storage/Quotations Blue Shield Energy/`.
- Check the launcher icon on both adaptive-icon and older launcher devices.
- Test file/image uploads using the Android document picker.

## Notes
- A storage permission prompt cannot be displayed during installation; Android controls when runtime permissions can be requested.
- I could not execute a Gradle build or test this on a physical Android device in this environment. Treat this as an updated source project pending Android Studio build and device verification.


## iOS companion project
A separate Swift/WKWebView Xcode project is included in `ios/`. See `ios/README-iOS.md` for build and signing steps. The Android project remains unchanged.
