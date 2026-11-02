import 'dart:async';
import 'package:flutter/foundation.dart';
import 'package:firebase_core/firebase_core.dart';
import 'package:firebase_messaging/firebase_messaging.dart';
import '../../../core/config/firebase_options.dart';
import '../domain/notification_ports.dart';

@pragma('vm:entry-point')
Future<void> arqNovaBackgroundMessage(RemoteMessage message) async {
  // Notification payloads are displayed by Android; project data is read after authentication.
}

class FirebasePushAdapter implements PushPlatformPort {
  Future<bool>? _boot;
  FirebaseMessaging? _messaging;
  final _messages = StreamController<ProjectNotification>.broadcast();
  final _opened = StreamController<ProjectNotification>.broadcast();
  ProjectNotification? _pending;
  @override
  Future<bool> initialize() => _boot ??= _initialize();
  Future<bool> _initialize() async {
    if (kIsWeb || defaultTargetPlatform != TargetPlatform.android) return false;
    try {
      if (Firebase.apps.isEmpty) {
        await Firebase.initializeApp(options: arqNovaAndroidFirebaseOptions);
      }
      _messaging = FirebaseMessaging.instance;
      FirebaseMessaging.onBackgroundMessage(arqNovaBackgroundMessage);
      FirebaseMessaging.onMessage.listen(
        (message) => _messages.add(_map(message)),
      );
      FirebaseMessaging.onMessageOpenedApp.listen((message) {
        _pending = _map(message);
        _opened.add(_pending!);
      });
      final initial = await _messaging!.getInitialMessage();
      if (initial != null) _pending = _map(initial);
      return true;
    } catch (_) {
      return false;
    }
  }

  ProjectNotification _map(RemoteMessage message) => ProjectNotification(
    message.data['projectId']?.toString() ?? '',
    message.notification?.title ?? 'ArqNova',
    message.notification?.body ?? 'Hay novedades en tu proyecto.',
  );
  @override
  Future<bool> permissionGranted() async => _allowed(
    (await _messaging!.getNotificationSettings()).authorizationStatus,
  );
  bool _allowed(AuthorizationStatus status) =>
      status == AuthorizationStatus.authorized ||
      status == AuthorizationStatus.provisional;
  @override
  Future<bool> requestPermission() async =>
      _allowed((await _messaging!.requestPermission()).authorizationStatus);
  @override
  Future<String> register() async {
    final result = _messaging!.onRegistered.first.timeout(
      const Duration(seconds: 25),
    );
    result.ignore();
    await _messaging!.register();
    return result;
  }

  @override
  Future<void> unregister() async {
    if (_messaging != null) await _messaging!.unregister();
  }

  @override
  Stream<String> get registrations => _messaging!.onRegistered;
  @override
  Stream<ProjectNotification> get messages => _messages.stream;
  @override
  Stream<ProjectNotification> get opened => _opened.stream;
  @override
  ProjectNotification? takePending() {
    final value = _pending;
    _pending = null;
    return value;
  }
}
