import 'dart:async';
import '../domain/notification_ports.dart';

class ProjectNotificationUseCases {
  ProjectNotificationUseCases(this.platform, this.api);
  final PushPlatformPort platform;
  final PushDeviceApiPort api;
  String? _token, _fid;
  bool enabled = false;
  bool _disposed = false;
  int _generation = 0;
  StreamSubscription<void>? _registrations;
  Stream<ProjectNotification> get messages => platform.messages;
  Stream<ProjectNotification> get opened => platform.opened;
  ProjectNotification? takePending() => platform.takePending();
  Future<bool> bind(String token) async {
    final generation = ++_generation;
    enabled = false;
    _token = token;
    if (!await platform.initialize() || !await platform.permissionGranted()) {
      return false;
    }
    if (_disposed || generation != _generation || _token != token) return false;
    return enable(token, askPermission: false);
  }

  Future<bool> enable(String token, {bool askPermission = true}) async {
    if (_disposed) return false;
    final generation = ++_generation;
    _token = token;
    enabled = false;
    if (!await platform.initialize()) {
      throw const NotificationException(
        'Las notificaciones están disponibles en la app Android.',
      );
    }
    if (!await api.enabled(token)) {
      throw const NotificationException(
        'El servidor todavía no tiene habilitadas las notificaciones.',
      );
    }
    if (askPermission && !await platform.requestPermission()) {
      throw const NotificationException(
        'Puedes habilitar el permiso de notificaciones desde los ajustes del teléfono.',
      );
    }
    if (_disposed || generation != _generation || _token != token) return false;
    final fid = await platform.register();
    if (_disposed || generation != _generation || _token != token) return false;
    if (!await _save(token, fid, generation)) return false;
    await _registrations?.cancel();
    // Process rotations in order: a slow old registration must not remove the newest one.
    _registrations = platform.registrations
        .asyncMap((next) async {
          final active = _token;
          if (!enabled || active == null || _disposed) return;
          try {
            await _save(active, next, generation);
          } catch (_) {
            /* A later login can refresh registration. */
          }
        })
        .listen(
          (_) {},
          onError: (Object _) {
            // A platform stream failure must not become an unhandled application error.
          },
        );
    return true;
  }

  Future<bool> _save(String token, String fid, int generation) async {
    if (_disposed || generation != _generation || _token != token) return false;
    await api.register(token, fid);
    if (_disposed || generation != _generation || _token != token) {
      try {
        await api.remove(token, fid);
      } catch (_) {
        /* Conditional server ownership prevents removing another user's registration. */
      }
      return false;
    }
    final old = _fid;
    _fid = fid;
    enabled = true;
    if (old != null && old != fid) {
      try {
        await api.remove(token, old);
      } catch (_) {
        /* The new installation is already registered. */
      }
    }
    return true;
  }

  Future<void> disable() async {
    final token = _token, fid = _fid;
    _generation++;
    enabled = false;
    _token = null;
    _fid = null;
    await _registrations?.cancel();
    try {
      if (token != null && fid != null) await api.remove(token, fid);
    } finally {
      await platform.unregister();
    }
  }

  void dispose() {
    _disposed = true;
    _generation++;
    _token = null;
    _registrations?.cancel();
    api.close();
  }
}
