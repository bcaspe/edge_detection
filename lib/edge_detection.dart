import 'dart:async';

import 'package:flutter/services.dart';

class EdgeDetection {
  static const MethodChannel _channel = const MethodChannel('edge_detection');

  static List<String> _normalizePaths(dynamic result, String fallbackSaveTo) {
    if (result is List) {
      return result
          .whereType<String>()
          .where((path) => path.isNotEmpty)
          .toList(growable: false);
    }

    // Backward-compatible fallback for platforms still returning bool.
    if (result is bool && result) {
      return [fallbackSaveTo];
    }

    return const [];
  }

  /// Call this method to scan the object edge in live camera.
  static Future<List<String>> detectEdge(
    String saveTo, {
    bool canUseGallery = true,
    String androidScanTitle = "Scanning",
    String androidCropTitle = "Crop",
    String androidCropBlackWhiteTitle = "Black White",
    String androidCropReset = "Reset",
  }) async {
    final dynamic result = await _channel.invokeMethod('edge_detect', {
      'save_to': saveTo,
      'can_use_gallery': canUseGallery,
      'scan_title': androidScanTitle,
      'crop_title': androidCropTitle,
      'crop_black_white_title': androidCropBlackWhiteTitle,
      'crop_reset_title': androidCropReset,
    });
    return _normalizePaths(result, saveTo);
  }

  /// Call this method to scan the object edge from a gallery image.
  static Future<List<String>> detectEdgeFromGallery(
    String saveTo, {
    String androidCropTitle = "Crop",
    String androidCropBlackWhiteTitle = "Black White",
    String androidCropReset = "Reset",
  }) async {
    final dynamic result = await _channel.invokeMethod('edge_detect_gallery', {
      'save_to': saveTo,
      'crop_title': androidCropTitle,
      'crop_black_white_title': androidCropBlackWhiteTitle,
      'crop_reset_title': androidCropReset,
      'from_gallery': true,
    });
    return _normalizePaths(result, saveTo);
  }
}
