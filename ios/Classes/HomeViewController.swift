import WeScan
import Flutter
import Foundation

class HomeViewController: UIViewController, ImageScannerControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate {

    var cameraController: ImageScannerController?
    var _result: FlutterResult?

    var saveTo: String = ""
    var canUseGallery: Bool = true
    var startFromGallery: Bool = false

    private var hasStartedSession = false
    private var hasFinished = false
    private var savedPaths: [String] = []

    private let overlayContainer = UIView()
    private let thumbnailScroll = UIScrollView()
    private let thumbnailStack = UIStackView()
    private let doneButton = UIButton(type: .system)
    private let selectPhotoButton = UIButton(type: .system)
    private var overlayConfigured = false
    private var overlayExternalConstraints: [NSLayoutConstraint] = []

    override func viewDidAppear(_ animated: Bool) {
        super.viewDidAppear(animated)
        guard !hasStartedSession else { return }
        hasStartedSession = true

        configureSystemAppearanceIfNeeded()
        if startFromGallery {
            presentImagePicker()
        } else {
            presentCameraScanner()
        }
    }

    private func configureSystemAppearanceIfNeeded() {
        if #available(iOS 15, *) {
            let appearance = UINavigationBarAppearance()
            let navigationBar = UINavigationBar()
            appearance.configureWithOpaqueBackground()
            appearance.titleTextAttributes = [NSAttributedString.Key.foregroundColor: UIColor.label]
            appearance.backgroundColor = .systemBackground
            navigationBar.standardAppearance = appearance
            UINavigationBar.appearance().scrollEdgeAppearance = appearance

            let appearanceTB = UITabBarAppearance()
            appearanceTB.configureWithOpaqueBackground()
            appearanceTB.backgroundColor = .systemBackground
            UITabBar.appearance().standardAppearance = appearanceTB
            UITabBar.appearance().scrollEdgeAppearance = appearanceTB
        }
    }

    private func presentCameraScanner() {
        let scanner = ImageScannerController()
        scanner.imageScannerDelegate = self
        applyDarkAppearance(to: scanner)
        cameraController = scanner

        present(scanner, animated: true) {
            self.attachOverlay(to: scanner.view)
            self.refreshOverlay()
        }
    }

    private func applyDarkAppearance(to scanner: UIViewController) {
        if #available(iOS 13.0, *) {
            scanner.isModalInPresentation = true
            scanner.overrideUserInterfaceStyle = .dark
            scanner.view.backgroundColor = .black
        }
    }

    private func attachOverlay(to containerView: UIView) {
        overlayContainer.removeFromSuperview()
        selectPhotoButton.removeFromSuperview()
        NSLayoutConstraint.deactivate(overlayExternalConstraints)

        if !overlayConfigured {
            overlayConfigured = true
            overlayContainer.translatesAutoresizingMaskIntoConstraints = false
            overlayContainer.backgroundColor = UIColor.black.withAlphaComponent(0.35)
            overlayContainer.layer.cornerRadius = 10

            thumbnailScroll.translatesAutoresizingMaskIntoConstraints = false
            thumbnailScroll.showsHorizontalScrollIndicator = false

            thumbnailStack.translatesAutoresizingMaskIntoConstraints = false
            thumbnailStack.axis = .horizontal
            thumbnailStack.spacing = 8
            thumbnailStack.alignment = .center

            doneButton.translatesAutoresizingMaskIntoConstraints = false
            doneButton.setTitle("Done", for: .normal)
            doneButton.backgroundColor = UIColor.systemBlue.withAlphaComponent(0.9)
            doneButton.setTitleColor(.white, for: .normal)
            doneButton.layer.cornerRadius = 6
            doneButton.contentEdgeInsets = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
            doneButton.addTarget(self, action: #selector(finishTapped), for: .touchUpInside)

            selectPhotoButton.translatesAutoresizingMaskIntoConstraints = false
            selectPhotoButton.setImage(
                UIImage(named: "gallery", in: Bundle(for: SwiftEdgeDetectionPlugin.self), compatibleWith: nil)?
                    .withRenderingMode(.alwaysTemplate),
                for: .normal
            )
            selectPhotoButton.tintColor = .white
            selectPhotoButton.backgroundColor = UIColor.black.withAlphaComponent(0.35)
            selectPhotoButton.layer.cornerRadius = 22
            selectPhotoButton.addTarget(self, action: #selector(selectPhoto), for: .touchUpInside)

            overlayContainer.addSubview(thumbnailScroll)
            overlayContainer.addSubview(doneButton)
            thumbnailScroll.addSubview(thumbnailStack)

            NSLayoutConstraint.activate([
                thumbnailScroll.leadingAnchor.constraint(equalTo: overlayContainer.leadingAnchor, constant: 10),
                thumbnailScroll.topAnchor.constraint(equalTo: overlayContainer.topAnchor, constant: 8),
                thumbnailScroll.bottomAnchor.constraint(equalTo: overlayContainer.bottomAnchor, constant: -8),
                thumbnailScroll.trailingAnchor.constraint(equalTo: doneButton.leadingAnchor, constant: -10),
                thumbnailScroll.heightAnchor.constraint(equalToConstant: 52),

                thumbnailStack.leadingAnchor.constraint(equalTo: thumbnailScroll.leadingAnchor),
                thumbnailStack.trailingAnchor.constraint(equalTo: thumbnailScroll.trailingAnchor),
                thumbnailStack.topAnchor.constraint(equalTo: thumbnailScroll.topAnchor),
                thumbnailStack.bottomAnchor.constraint(equalTo: thumbnailScroll.bottomAnchor),
                thumbnailStack.heightAnchor.constraint(equalTo: thumbnailScroll.heightAnchor),

                doneButton.centerYAnchor.constraint(equalTo: overlayContainer.centerYAnchor),
                doneButton.trailingAnchor.constraint(equalTo: overlayContainer.trailingAnchor, constant: -10),
                doneButton.widthAnchor.constraint(greaterThanOrEqualToConstant: 62)
            ])
        }
        selectPhotoButton.isHidden = !canUseGallery

        containerView.addSubview(overlayContainer)
        containerView.addSubview(selectPhotoButton)

        let guide = containerView.safeAreaLayoutGuide
        overlayExternalConstraints = [
            overlayContainer.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 12),
            overlayContainer.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -12),
            overlayContainer.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -8),
            selectPhotoButton.trailingAnchor.constraint(equalTo: guide.trailingAnchor, constant: -24),
            selectPhotoButton.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -72),
            selectPhotoButton.widthAnchor.constraint(equalToConstant: 44),
            selectPhotoButton.heightAnchor.constraint(equalToConstant: 44)
        ]
        NSLayoutConstraint.activate(overlayExternalConstraints)
    }

    private func refreshOverlay() {
        doneButton.isEnabled = !savedPaths.isEmpty
        doneButton.alpha = savedPaths.isEmpty ? 0.55 : 1.0

        for view in thumbnailStack.arrangedSubviews {
            thumbnailStack.removeArrangedSubview(view)
            view.removeFromSuperview()
        }

        for (index, path) in savedPaths.enumerated() {
            let button = UIButton(type: .custom)
            button.tag = index
            button.translatesAutoresizingMaskIntoConstraints = false
            button.layer.cornerRadius = 6
            button.clipsToBounds = true
            button.backgroundColor = UIColor.black.withAlphaComponent(0.4)
            button.addTarget(self, action: #selector(thumbnailTapped(_:)), for: .touchUpInside)

            if let image = UIImage(contentsOfFile: path) {
                button.setImage(image, for: .normal)
                button.imageView?.contentMode = .scaleAspectFill
            } else {
                button.setTitle("\(index + 1)", for: .normal)
                button.setTitleColor(.white, for: .normal)
            }

            NSLayoutConstraint.activate([
                button.widthAnchor.constraint(equalToConstant: 52),
                button.heightAnchor.constraint(equalToConstant: 52)
            ])
            thumbnailStack.addArrangedSubview(button)
        }
    }

    @objc private func selectPhoto() {
        if let scanner = cameraController {
            scanner.dismiss(animated: true) {
                self.cameraController = nil
                self.presentImagePicker()
            }
            return
        }
        presentImagePicker()
    }

    private func presentImagePicker() {
        let imagePicker = UIImagePickerController()
        imagePicker.delegate = self
        imagePicker.sourceType = .photoLibrary
        imagePicker.modalPresentationStyle = .fullScreen
        applyDarkAppearance(to: imagePicker)
        topPresenter().present(imagePicker, animated: true)
    }

    public func imagePickerControllerDidCancel(_ picker: UIImagePickerController) {
        picker.dismiss(animated: true) {
            if self.startFromGallery {
                self.finishSession()
            } else {
                self.presentCameraScanner()
            }
        }
    }

    public func imagePickerController(
        _ picker: UIImagePickerController,
        didFinishPickingMediaWithInfo info: [UIImagePickerController.InfoKey: Any]
    ) {
        guard let image = info[.originalImage] as? UIImage else {
            picker.dismiss(animated: true) {
                self.finishSession()
            }
            return
        }

        picker.dismiss(animated: true) {
            let scanner = ImageScannerController(image: image)
            scanner.imageScannerDelegate = self
            self.applyDarkAppearance(to: scanner)
            self.topPresenter().present(scanner, animated: true) {
                self.attachOverlay(to: scanner.view)
                self.refreshOverlay()
            }
        }
    }

    @objc private func finishTapped() {
        finishSession()
    }

    @objc private func thumbnailTapped(_ sender: UIButton) {
        let index = sender.tag
        guard index >= 0 && index < savedPaths.count else { return }
        showPreview(path: savedPaths[index])
    }

    private func showPreview(path: String) {
        let preview = UIViewController()
        preview.view.backgroundColor = .black
        let imageView = UIImageView()
        imageView.translatesAutoresizingMaskIntoConstraints = false
        imageView.contentMode = .scaleAspectFit
        imageView.image = UIImage(contentsOfFile: path)
        preview.view.addSubview(imageView)

        NSLayoutConstraint.activate([
            imageView.leadingAnchor.constraint(equalTo: preview.view.leadingAnchor, constant: 12),
            imageView.trailingAnchor.constraint(equalTo: preview.view.trailingAnchor, constant: -12),
            imageView.topAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.topAnchor, constant: 12),
            imageView.bottomAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.bottomAnchor, constant: -12)
        ])

        topPresenter().present(preview, animated: true)
    }

    func setParams(saveTo: String, canUseGallery: Bool, startFromGallery: Bool) {
        self.saveTo = saveTo
        self.canUseGallery = canUseGallery
        self.startFromGallery = startFromGallery
    }

    func imageScannerController(_ scanner: ImageScannerController, didFailWithError error: Error) {
        print(error)
        scanner.dismiss(animated: true) {
            self.finishSession()
        }
    }

    func imageScannerController(_ scanner: ImageScannerController, didFinishScanningWithResults results: ImageScannerResults) {
        let imageToSave = results.doesUserPreferEnhancedScan ? results.enhancedScan?.image : results.croppedScan.image
        if let imageToSave, let path = saveImage(image: imageToSave) {
            savedPaths.append(path)
        }

        scanner.dismiss(animated: true) {
            if self.startFromGallery {
                self.finishSession()
            } else {
                self.presentCameraScanner()
            }
        }
    }

    func imageScannerControllerDidCancel(_ scanner: ImageScannerController) {
        scanner.dismiss(animated: true) {
            if self.savedPaths.isEmpty {
                self.finishSession()
            } else if self.startFromGallery {
                self.finishSession()
            } else {
                self.presentCameraScanner()
            }
        }
    }

    private func finishSession() {
        guard !hasFinished else { return }
        hasFinished = true
        _result?(savedPaths)
        _result = nil
        overlayContainer.removeFromSuperview()
        selectPhotoButton.removeFromSuperview()

        if let presented = presentedViewController {
            presented.dismiss(animated: true) {
                self.dismiss(animated: true)
            }
        } else {
            dismiss(animated: true)
        }
    }

    private func topPresenter() -> UIViewController {
        var presenter: UIViewController = self
        while let next = presenter.presentedViewController {
            presenter = next
        }
        return presenter
    }

    private func buildSavePath(for index: Int) -> String {
        let fileURL = URL(fileURLWithPath: saveTo)
        if index == 0 {
            return fileURL.path
        }

        let parent = fileURL.deletingLastPathComponent()
        let name = fileURL.deletingPathExtension().lastPathComponent
        let ext = fileURL.pathExtension
        let newName = ext.isEmpty ? "\(name)_\(index + 1)" : "\(name)_\(index + 1).\(ext)"
        return parent.appendingPathComponent(newName).path
    }

    func saveImage(image: UIImage) -> String? {
        guard let data = image.jpegData(compressionQuality: 1) ?? image.pngData() else {
            return nil
        }

        let targetPath = buildSavePath(for: savedPaths.count)
        let filePath = URL(fileURLWithPath: targetPath)
        do {
            let folder = filePath.deletingLastPathComponent()
            try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true, attributes: nil)
            if FileManager.default.fileExists(atPath: filePath.path) {
                try FileManager.default.removeItem(atPath: filePath.path)
            }
            try data.write(to: filePath)
            return filePath.path
        } catch {
            print(error.localizedDescription)
            return nil
        }
    }
}

