import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:arqnova_mobile/features/project_notifications/application/project_notification_use_cases.dart';
import 'package:arqnova_mobile/features/project_notifications/domain/notification_ports.dart';
import 'package:arqnova_mobile/features/project_notifications/data/http_push_device_adapter.dart';

void main() {
  test(
    'leer proyectos no solicita automáticamente el permiso de notificaciones',
    () async {
      final platform = FakePushPlatform()..granted = false;
      final api = FakePushApi();
      final useCases = ProjectNotificationUseCases(platform, api);
      expect(await useCases.bind('session'), false);
      expect(platform.permissionRequests, 0);
      expect(api.saved, isEmpty);
      useCases.dispose();
    },
  );
  test(
    'activar asocia la instalación a la sesión y cerrar sesión la desvincula',
    () async {
      final platform = FakePushPlatform();
      final api = FakePushApi();
      final useCases = ProjectNotificationUseCases(platform, api);
      expect(await useCases.enable('session'), true);
      expect(api.saved, ['session:fid-initial']);
      await useCases.disable();
      expect(api.removed, ['session:fid-initial']);
      expect(useCases.enabled, false);
      expect(platform.unregisters, 1);
      useCases.dispose();
    },
  );
  test(
    'un permiso rechazado conserva la navegación y no registra dispositivos',
    () async {
      final platform = FakePushPlatform()..granted = false;
      final api = FakePushApi();
      final useCases = ProjectNotificationUseCases(platform, api);
      await expectLater(
        useCases.enable('session'),
        throwsA(isA<NotificationException>()),
      );
      expect(api.saved, isEmpty);
      expect(useCases.enabled, false);
      useCases.dispose();
    },
  );
  test(
    'la rotación de instalación registra la nueva y retira la anterior',
    () async {
      final platform = FakePushPlatform();
      final api = FakePushApi();
      final useCases = ProjectNotificationUseCases(platform, api);
      await useCases.enable('session');
      platform.ids.add('fid-next');
      await Future<void>.delayed(Duration.zero);
      expect(api.saved.last, 'session:fid-next');
      expect(api.removed, ['session:fid-initial']);
      useCases.dispose();
    },
  );
  test(
    'una respuesta tardía no reactiva el dispositivo después de cerrar sesión',
    () async {
      final platform = FakePushPlatform();
      final gate = Completer<void>();
      final api = FakePushApi()..gate = gate;
      final useCases = ProjectNotificationUseCases(platform, api);
      final pending = useCases.enable('session');
      for (var n = 0; n < 20 && api.saved.isEmpty; n++) {
        await Future<void>.delayed(Duration.zero);
      }
      await useCases.disable();
      gate.complete();
      expect(await pending, false);
      expect(useCases.enabled, false);
      expect(api.removed, contains('session:fid-initial'));
      useCases.dispose();
    },
  );
  test(
    'un servidor sin configuración push no solicita permisos ni registra FIDs',
    () async {
      final platform = FakePushPlatform();
      final api = FakePushApi()..configured = false;
      final useCases = ProjectNotificationUseCases(platform, api);
      await expectLater(
        useCases.enable('session'),
        throwsA(isA<NotificationException>()),
      );
      expect(platform.permissionRequests, 0);
      expect(api.saved, isEmpty);
      useCases.dispose();
    },
  );
  test(
    'el adaptador de dispositivo usa FID y la identidad del token autenticado',
    () async {
      final requests = <http.Request>[];
      final api = HttpPushDeviceAdapter(
        client: MockClient((r) async {
          requests.add(r);
          return http.Response(
            jsonEncode({
              'exito': true,
              'datos': {'enabled': true},
            }),
            200,
          );
        }),
      );
      expect(await api.enabled('session'), true);
      await api.register('session', 'fid-initial');
      await api.remove('session', 'fid-initial');
      expect(requests.map((r) => r.url.path), [
        '/api/v1/push/status',
        '/api/v1/push/devices',
        '/api/v1/push/devices/remove',
      ]);
      expect(jsonDecode(requests[1].body), {'fid': 'fid-initial'});
      expect(requests[1].headers['Authorization'], 'Bearer session');
      expect(requests[1].url.host, '10.0.2.2');
      expect(requests[1].url.port, 8080);
      api.close();
    },
  );
}

class FakePushPlatform implements PushPlatformPort {
  bool granted = true;
  int permissionRequests = 0, unregisters = 0;
  final ids = StreamController<String>.broadcast();
  @override
  Future<bool> initialize() async => true;
  @override
  Future<bool> permissionGranted() async => granted;
  @override
  Future<bool> requestPermission() async {
    permissionRequests++;
    return granted;
  }

  @override
  Future<String> register() async => 'fid-initial';
  @override
  Future<void> unregister() async {
    unregisters++;
  }

  @override
  Stream<String> get registrations => ids.stream;
  @override
  Stream<ProjectNotification> get messages => const Stream.empty();
  @override
  Stream<ProjectNotification> get opened => const Stream.empty();
  @override
  ProjectNotification? takePending() => null;
}

class FakePushApi implements PushDeviceApiPort {
  final saved = <String>[], removed = <String>[];
  bool configured = true;
  Completer<void>? gate;
  @override
  Future<bool> enabled(String authToken) async => configured;
  @override
  Future<void> register(String authToken, String fid) async {
    saved.add('$authToken:$fid');
    await gate?.future;
  }

  @override
  Future<void> remove(String authToken, String fid) async {
    removed.add('$authToken:$fid');
  }

  @override
  void close() {}
}
