import WeScan
import Flutter
import Foundation
import PhotosUI

class HomeViewController: UIViewController, ImageScannerControllerDelegate, UIImagePickerControllerDelegate, UINavigationControllerDelegate, PHPickerViewControllerDelegate {

    var cameraController: ImageScannerController?
    var _result: FlutterResult?

    var saveTo: String = ""
    var canUseGallery: Bool = true
    var startFromGallery: Bool = false

    private var hasStartedSession = false
    private var hasFinished = false
    private var savedPaths: [String] = []
    private var pendingGalleryImages: [UIImage] = []
    private var galleryBatchTotal: Int = 0
    private var previewedPath: String?
    private weak var activePreviewController: UIViewController?

    private let overlayContainer = UIView()
    private let thumbnailScroll = UIScrollView()
    private let thumbnailStack = UIStackView()
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
            presentScanner()
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

    private func presentScanner(with image: UIImage? = nil) {
        let scanner = image == nil ? ImageScannerController() : ImageScannerController(image: image!)
        if let _ = image, galleryBatchTotal > 0 {
            let idx = savedPaths.count + 1
            scanner.title = String(format: "Cropping photo %d of %d", idx, galleryBatchTotal)
        }
        scanner.imageScannerDelegate = self
        applyDarkAppearance(to: scanner)
        cameraController = scanner

        topPresenter().present(scanner, animated: true) {
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

            selectPhotoButton.translatesAutoresizingMaskIntoConstraints = false
            selectPhotoButton.setImage(
                UIImage(named: "gallery", in: Bundle(for: SwiftEdgeDetectionPlugin.self), compatibleWith: nil)?
                    .withRenderingMode(.alwaysTemplate),
                for: .normal
            )
            selectPhotoButton.tintColor = .white
            selectPhotoButton.backgroundColor = UIColor.systemGray.withAlphaComponent(0.65)
            selectPhotoButton.layer.cornerRadius = 22
            selectPhotoButton.addTarget(self, action: #selector(selectPhoto), for: .touchUpInside)

            overlayContainer.addSubview(thumbnailScroll)
            thumbnailScroll.addSubview(thumbnailStack)

            NSLayoutConstraint.activate([
                thumbnailScroll.leadingAnchor.constraint(equalTo: overlayContainer.leadingAnchor, constant: 10),
                thumbnailScroll.topAnchor.constraint(equalTo: overlayContainer.topAnchor, constant: 8),
                thumbnailScroll.bottomAnchor.constraint(equalTo: overlayContainer.bottomAnchor, constant: -8),
                thumbnailScroll.trailingAnchor.constraint(equalTo: overlayContainer.trailingAnchor, constant: -10),
                thumbnailScroll.heightAnchor.constraint(equalToConstant: 52),

                thumbnailStack.leadingAnchor.constraint(equalTo: thumbnailScroll.leadingAnchor),
                thumbnailStack.trailingAnchor.constraint(equalTo: thumbnailScroll.trailingAnchor),
                thumbnailStack.topAnchor.constraint(equalTo: thumbnailScroll.topAnchor),
                thumbnailStack.bottomAnchor.constraint(equalTo: thumbnailScroll.bottomAnchor),
                thumbnailStack.heightAnchor.constraint(equalTo: thumbnailScroll.heightAnchor)
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
            selectPhotoButton.leadingAnchor.constraint(equalTo: guide.leadingAnchor, constant: 24),
            selectPhotoButton.bottomAnchor.constraint(equalTo: guide.bottomAnchor, constant: -72),
            selectPhotoButton.widthAnchor.constraint(equalToConstant: 44),
            selectPhotoButton.heightAnchor.constraint(equalToConstant: 44)
        ]
        NSLayoutConstraint.activate(overlayExternalConstraints)
    }

    private func refreshOverlay() {
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
        if #available(iOS 14, *) {
            var config = PHPickerConfiguration()
            config.filter = .images
            config.selectionLimit = 0
            let picker = PHPickerViewController(configuration: config)
            picker.delegate = self
            picker.modalPresentationStyle = .fullScreen
            applyDarkAppearance(to: picker)
            topPresenter().present(picker, animated: true)
            return
        }

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
                self.presentScanner()
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

        pendingGalleryImages = [image]
        galleryBatchTotal = 1
        picker.dismiss(animated: true) {
            self.processNextPendingGalleryImage()
        }
    }

    @available(iOS 14, *)
    func picker(_ picker: PHPickerViewController, didFinishPicking results: [PHPickerResult]) {
        picker.dismiss(animated: true) {
            if results.isEmpty {
                if self.startFromGallery {
                    self.finishSession()
                } else {
                    self.presentScanner()
                }
                return
            }

            let dispatchGroup = DispatchGroup()
            var loadedImages: [UIImage] = []
            let lock = NSLock()

            for result in results {
                dispatchGroup.enter()
                result.itemProvider.loadObject(ofClass: UIImage.self) { object, _ in
                    if let image = object as? UIImage {
                        lock.lock()
                        loadedImages.append(image)
                        lock.unlock()
                    }
                    dispatchGroup.leave()
                }
            }

            dispatchGroup.notify(queue: .main) {
                self.pendingGalleryImages = loadedImages
                self.galleryBatchTotal = loadedImages.count
                self.processNextPendingGalleryImage()
            }
        }
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
        let closeButton = UIButton(type: .system)
        closeButton.translatesAutoresizingMaskIntoConstraints = false
        closeButton.setTitle("Close", for: .normal)
        closeButton.setTitleColor(.white, for: .normal)
        closeButton.backgroundColor = UIColor.black.withAlphaComponent(0.6)
        closeButton.layer.cornerRadius = 6
        closeButton.contentEdgeInsets = UIEdgeInsets(top: 6, left: 10, bottom: 6, right: 10)
        closeButton.addTarget(self, action: #selector(closePreviewTapped), for: .touchUpInside)
        preview.view.addSubview(closeButton)

        let deleteButton = UIButton(type: .system)
        deleteButton.translatesAutoresizingMaskIntoConstraints = false
        deleteButton.setImage(UIImage(systemName: "trash.fill"), for: .normal)
        deleteButton.tintColor = .white
        deleteButton.backgroundColor = UIColor.systemRed.withAlphaComponent(0.8)
        deleteButton.layer.cornerRadius = 22
        deleteButton.addTarget(self, action: #selector(deletePreviewTapped), for: .touchUpInside)
        preview.view.addSubview(deleteButton)

        NSLayoutConstraint.activate([
            closeButton.leadingAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.leadingAnchor, constant: 12),
            closeButton.topAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.topAnchor, constant: 12),
            deleteButton.trailingAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.trailingAnchor, constant: -12),
            deleteButton.topAnchor.constraint(equalTo: preview.view.safeAreaLayoutGuide.topAnchor, constant: 12),
            deleteButton.widthAnchor.constraint(equalToConstant: 44),
            deleteButton.heightAnchor.constraint(equalToConstant: 44)
        ])

        previewedPath = path
        activePreviewController = preview
        topPresenter().present(preview, animated: true)
    }

    @objc private func closePreviewTapped() {
        activePreviewController?.dismiss(animated: true)
    }

    @objc private func deletePreviewTapped() {
        guard let path = previewedPath else { return }
        let confirm = UIAlertController(
            title: "Delete photo",
            message: "Are you sure you want to delete this photo?",
            preferredStyle: .alert
        )
        confirm.addAction(UIAlertAction(title: "Cancel", style: .cancel))
        confirm.addAction(UIAlertAction(title: "Delete", style: .destructive) { _ in
            self.deleteCapturedPhoto(path: path)
            self.activePreviewController?.dismiss(animated: true)
        })
        activePreviewController?.present(confirm, animated: true)
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
        let shouldFinishSession = scanner.completionMode == .finishSession
        scanner.completionMode = .continueScanning

        scanner.dismiss(animated: true) {
            if !self.pendingGalleryImages.isEmpty {
                self.processNextPendingGalleryImage()
            } else if shouldFinishSession {
                self.finishSession()
            } else {
                self.galleryBatchTotal = 0
                self.presentScanner()
            }
        }
    }

    func imageScannerControllerDidCancel(_ scanner: ImageScannerController) {
        scanner.dismiss(animated: true) {
            if !self.pendingGalleryImages.isEmpty {
                self.processNextPendingGalleryImage()
            } else {
                self.finishSession()
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

    private func processNextPendingGalleryImage() {
        if pendingGalleryImages.isEmpty {
            galleryBatchTotal = 0
            presentScanner()
            return
        }
        let nextImage = pendingGalleryImages.removeFirst()
        presentScanner(with: nextImage)
    }

    private func deleteCapturedPhoto(path: String) {
        savedPaths.removeAll { $0 == path }
        do {
            if FileManager.default.fileExists(atPath: path) {
                try FileManager.default.removeItem(atPath: path)
            }
        } catch {
            print(error.localizedDescription)
        }
        refreshOverlay()
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

