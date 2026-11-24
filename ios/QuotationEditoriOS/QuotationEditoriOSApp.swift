import SwiftUI
import WebKit
import UniformTypeIdentifiers

@main
struct QuotationEditoriOSApp: App {
    var body: some Scene {
        WindowGroup {
            QuotationWebView()
                .ignoresSafeArea()
                .preferredColorScheme(.light)
        }
    }
}

struct QuotationWebView: UIViewRepresentable {
    func makeCoordinator() -> Coordinator { Coordinator() }

    func makeUIView(context: Context) -> WKWebView {
        let content = WKUserContentController()
        content.add(context.coordinator, name: "quotationPdf")
        let config = WKWebViewConfiguration()
        config.userContentController = content
        config.defaultWebpagePreferences.allowsContentJavaScript = true

        let web = WKWebView(frame: .zero, configuration: config)
        web.navigationDelegate = context.coordinator
        web.uiDelegate = context.coordinator
        web.allowsBackForwardNavigationGestures = true
        web.scrollView.contentInsetAdjustmentBehavior = .never
        context.coordinator.webView = web
        web.load(URLRequest(url: URL(string: "https://blueshieldenergy.in/projects/quotation-editor/")!))
        return web
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate, WKScriptMessageHandler, UIDocumentPickerDelegate {
        weak var webView: WKWebView?
        private var pendingExportURL: URL?

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            guard let body = message.body as? [String: Any],
                  let action = body["action"] as? String else { return }
            if action == "print" {
                createAndExportPDF()
            } else if action == "saveBase64",
                      let encoded = body["data"] as? String,
                      let data = Data(base64Encoded: encoded) {
                let name = safeFilename(body["filename"] as? String ?? "Quotation.pdf")
                export(data: data, filename: name)
            }
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            let script = """
            (function() {
              if (window.__quotationIOSBridgeInstalled) return;
              window.__quotationIOSBridgeInstalled = true;
              function sendBlob(anchor) {
                try {
                  const href = anchor.href || anchor.getAttribute('href');
                  if (!href || !href.startsWith('blob:')) return false;
                  fetch(href).then(r => r.blob()).then(blob => {
                    const reader = new FileReader();
                    reader.onloadend = () => {
                      const data = String(reader.result).split(',')[1];
                      if (data) window.webkit.messageHandlers.quotationPdf.postMessage({
                        action:'saveBase64', data:data, filename:anchor.download || 'Quotation.pdf'
                      });
                    };
                    reader.readAsDataURL(blob);
                  });
                  return true;
                } catch (_) { return false; }
              }
              document.addEventListener('click', function(event) {
                const anchor = event.target.closest && event.target.closest('a');
                if (anchor && sendBlob(anchor)) {
                  event.preventDefault(); event.stopImmediatePropagation();
                }
              }, true);
              const originalClick = HTMLAnchorElement.prototype.click;
              HTMLAnchorElement.prototype.click = function() {
                if (sendBlob(this)) return;
                return originalClick.apply(this, arguments);
              };
              window.print = function() {
                window.webkit.messageHandlers.quotationPdf.postMessage({action:'print'});
              };
            })();
            """
            webView.evaluateJavaScript(script)
        }

        func webView(_ webView: WKWebView, decidePolicyFor navigationAction: WKNavigationAction,
                     decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = navigationAction.request.url,
                  let scheme = url.scheme?.lowercased() else {
                decisionHandler(.cancel); return
            }
            if scheme == "https" || scheme == "http" {
                let host = url.host?.lowercased() ?? ""
                if host == "blueshieldenergy.in" || host.hasSuffix(".blueshieldenergy.in") {
                    decisionHandler(.allow)
                } else {
                    decisionHandler(.cancel)
                }
            } else if scheme == "blob" || scheme == "about" {
                decisionHandler(.allow)
            } else {
                decisionHandler(.cancel)
            }
        }

        private func createAndExportPDF() {
            guard let webView else { return }
            let config = WKPDFConfiguration()
            webView.createPDF(configuration: config) { [weak self] result in
                switch result {
                case .success(let data):
                    self?.export(data: data, filename: "Quotation-\(Int(Date().timeIntervalSince1970)).pdf")
                case .failure:
                    self?.showError("Could not create the quotation PDF.")
                }
            }
        }

        private func export(data: Data, filename: String) {
            let url = FileManager.default.temporaryDirectory.appendingPathComponent(safeFilename(filename))
            do {
                try data.write(to: url, options: .atomic)
                pendingExportURL = url
                DispatchQueue.main.async {
                    let picker = UIDocumentPickerViewController(forExporting: [url], asCopy: true)
                    picker.delegate = self
                    picker.modalPresentationStyle = .formSheet
                    self.topViewController()?.present(picker, animated: true)
                }
            } catch {
                showError("Could not prepare the PDF for saving.")
            }
        }

        func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
            pendingExportURL = nil
        }

        private func safeFilename(_ input: String) -> String {
            let cleaned = input.replacingOccurrences(of: "[^A-Za-z0-9._-]", with: "_", options: .regularExpression)
            return cleaned.lowercased().hasSuffix(".pdf") ? cleaned : cleaned + ".pdf"
        }

        private func topViewController() -> UIViewController? {
            let scenes = UIApplication.shared.connectedScenes.compactMap { $0 as? UIWindowScene }
            let root = scenes.flatMap { $0.windows }.first(where: { $0.isKeyWindow })?.rootViewController
            var current = root
            while let presented = current?.presentedViewController { current = presented }
            return current
        }

        private func showError(_ message: String) {
            DispatchQueue.main.async {
                let alert = UIAlertController(title: "Quotation Editor", message: message, preferredStyle: .alert)
                alert.addAction(UIAlertAction(title: "OK", style: .default))
                self.topViewController()?.present(alert, animated: true)
            }
        }

        // Allows HTML file inputs to open the iOS Files picker.
        func webView(_ webView: WKWebView, runOpenPanelWith parameters: WKOpenPanelParameters,
                     initiatedByFrame frame: WKFrameInfo,
                     completionHandler: @escaping ([URL]?) -> Void) {
            let picker = UIDocumentPickerViewController(forOpeningContentTypes: [.item], asCopy: true)
            let delegate = FilePickerDelegate(completion: completionHandler)
            objc_setAssociatedObject(picker, "filePickerDelegate", delegate, .OBJC_ASSOCIATION_RETAIN_NONATOMIC)
            picker.delegate = delegate
            topViewController()?.present(picker, animated: true)
        }
    }
}

private final class FilePickerDelegate: NSObject, UIDocumentPickerDelegate {
    private let completion: ([URL]?) -> Void
    init(completion: @escaping ([URL]?) -> Void) { self.completion = completion }
    func documentPicker(_ controller: UIDocumentPickerViewController, didPickDocumentsAt urls: [URL]) {
        completion(urls)
    }
    func documentPickerWasCancelled(_ controller: UIDocumentPickerViewController) {
        completion(nil)
    }
}
