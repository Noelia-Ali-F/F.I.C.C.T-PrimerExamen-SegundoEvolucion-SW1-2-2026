import 'dart:convert';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:arqnova_mobile/features/project_viewer/domain/project_models.dart';
import 'package:arqnova_mobile/features/project_viewer/domain/project_viewer_port.dart';
import 'package:arqnova_mobile/features/project_viewer/application/project_viewer_use_cases.dart';
import 'package:arqnova_mobile/features/project_viewer/data/http_project_viewer_adapter.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/project_explorer_page.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/read_only_diagram.dart';

const session = ViewerSession('test-token', 'noelia@example.test', 'Noelia');
Map<String, dynamic> model() => {
  'format': 'uml-class-diagram',
  'version': 2,
  'classes': [
    for (final row in [
      ('a', 'Venta', 30, 30),
      ('b', 'Producto', 450, 30),
      ('d', 'Detalle', 240, 300),
    ])
      {
        'id': row.$1,
        'name': row.$2,
        'x': row.$3,
        'y': row.$4,
        'attributes': [
          {'id': 'attr-${row.$1}', 'name': 'id', 'type': 'Long'},
        ],
        'methods': [],
        'color': '#2563eb',
      },
  ],
  'relations': [
    {
      'id': 'many',
      'type': 'association',
      'sourceId': 'a',
      'targetId': 'b',
      'sourceMultiplicity': '*',
      'targetMultiplicity': '1..*',
      'associationClassId': 'd',
    },
    {
      'id': 'comp',
      'type': 'composition',
      'sourceId': 'b',
      'targetId': 'd',
      'sourceMultiplicity': '1',
      'targetMultiplicity': '0..*',
    },
  ],
};
http.Response ok(dynamic data) => http.Response(
  jsonEncode({'exito': true, 'datos': data}),
  200,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

void main() {
  test(
    'el adaptador autentica y consulta el modelo sin enviar operaciones de edición',
    () async {
      final requests = <http.Request>[];
      final graph = model();
      final api = HttpProjectViewerAdapter(
        client: MockClient((r) async {
          requests.add(r);
          if (r.url.path.endsWith('/auth/login')) {
            return ok({
              'token': 'test-token',
              'username': session.username,
              'nombreCompleto': 'Noelia',
            });
          }
          if (r.url.path.endsWith('/diagram')) {
            return ok({'modelVersion': 4, 'model': graph});
          }
          return ok([
            {
              'id': 'p',
              'name': 'Ventas',
              'description': '',
              'memberRole': 'EDITOR',
            },
          ]);
        }),
      );
      final actor = await api.login(session.username, 'test-password');
      expect((await api.projects(actor)).single.name, 'Ventas');
      final diagram = await api.diagram(actor, 'p');
      expect(diagram.version, 4);
      expect(diagram.model['relations'], graph['relations']);
      expect(requests.map((r) => r.method), ['POST', 'GET', 'GET']);
      expect(requests.last.headers['Authorization'], 'Bearer test-token');
    expect(requests.first.url.host, '10.0.2.2');
    expect(requests.first.url.port, 8080);
      expect(jsonDecode(requests.first.body)['username'], session.username);
      api.close();
    },
  );
  test('no oculta un acceso revocado como si fuera una lista vacía', () async {
    final api = HttpProjectViewerAdapter(
      baseUrl: 'https://test',
      client: MockClient((_) async => http.Response('', 403)),
    );
    await expectLater(
      api.projects(session),
      throwsA(isA<ViewerException>().having((e) => e.status, 'status', 403)),
    );
    api.close();
  });
  test(
    'el historial conserva autor, estado de IA y cursores independientes',
    () async {
      final seen = <Uri>[];
      final api = HttpProjectViewerAdapter(
        baseUrl: 'https://test',
        client: MockClient((r) async {
          seen.add(r.url);
          if (r.url.path.endsWith('/activities')) {
            return ok({
              'items': [
                {
                  'id': 'h',
                  'author': 'Nohemi',
                  'kind': 'class:create',
                  'description': 'Creó Libro',
                  'timestamp': 1000,
                },
              ],
              'nextCursor': 'human-next',
            });
          }
          if (r.url.path.endsWith('/status')) return ok({'enabled': true});
          return ok({
            'turns': [
              {
                'requestId': 'ai',
                'authorName': 'Noelia',
                'mode': 'MODEL',
                'instruction': 'Crea Libro',
                'state': 'COMPLETED',
                'answer': {'text': 'Clase creada'},
                'updatedAt': '2026-11-02T10:00:00Z',
              },
            ],
            'nextCursor': 'ai-next',
          });
        }),
      );
      final result = await ProjectViewerUseCases(
        api,
      ).timeline(session, 'p', activityCursor: 'human-old', aiCursor: 'ai-old');
      expect(result.entries.map((e) => e.author), ['Noelia', 'Nohemi']);
      expect(result.entries.first.ai, true);
      expect(result.entries.first.message, contains('Crea Libro'));
      expect(result.activityCursor, 'human-next');
      expect(result.aiCursor, 'ai-next');
      expect(seen.first.queryParameters['cursor'], 'human-old');
      expect(seen.last.queryParameters['cursor'], 'ai-old');
      api.close();
    },
  );
  test(
    'una caída de memoria IA conserva la actividad humana y muestra aviso',
    () async {
      final port =
          FakeViewer()
            ..aiError = const ViewerException(
              'No se pudo recuperar la conversación',
            );
      final result = await ProjectViewerUseCases(port).timeline(session, 'p');
      expect(result.entries.single.author, 'Nohemi');
      expect(result.warnings, hasLength(1));
    },
  );
  test('la fusión no duplica intervenciones y actualiza su respuesta', () {
    final before = TimelineEntry(
      id: 'r',
      author: 'Noelia',
      kind: 'IA',
      message: 'Pendiente',
      at: DateTime.utc(2026),
    );
    final after = TimelineEntry(
      id: 'r',
      author: 'Noelia',
      kind: 'IA',
      message: 'Completa',
      at: DateTime.utc(2026, 2),
    );
    expect(
      ProjectViewerUseCases.merge([before], [after]).single.message,
      'Completa',
    );
  });
  testWidgets(
    'el lienzo admite composición y clase de asociación sin editar los datos',
    (tester) async {
      final graph = model(), original = jsonEncode(model());
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: ReadOnlyDiagram(diagram: ViewerDiagram('p', 4, graph)),
          ),
        ),
      );
      expect(tester.takeException(), isNull);
      expect(find.byType(InteractiveViewer), findsOneWidget);
      expect(find.byType(TextField), findsNothing);
      expect(jsonEncode(graph), original);
    },
  );
  testWidgets(
    'inicia sesión, abre un proyecto de solo lectura y muestra sus autores',
    (tester) async {
      final port = FakeViewer();
      await tester.pumpWidget(
        MaterialApp(
          home: ProjectExplorerPage(useCases: ProjectViewerUseCases(port)),
        ),
      );
      await tester.enterText(
        find.byType(TextField).at(0),
        'noelia@example.test',
      );
      await tester.enterText(find.byType(TextField).at(1), 'test-password');
      await tester.tap(find.text('Iniciar sesión'));
      await tester.pumpAndSettle();
      expect(find.text('Ventas'), findsOneWidget);
      await tester.tap(find.text('Ventas'));
      await tester.pumpAndSettle();
      expect(
        find.text('Solo lectura · Amplía y desplaza el diagrama'),
        findsOneWidget,
      );
      await tester.tap(find.text('Actividad'));
      await tester.pumpAndSettle();
      expect(find.text('Nohemi · Actividad'), findsOneWidget);
      expect(find.text('Creó Libro'), findsOneWidget);
    },
  );
}

class FakeViewer implements ProjectViewerPort {
  ViewerException? aiError;
  @override
  Future<ViewerSession> login(String username, String password) async =>
      session;
  @override
  Future<List<ViewerProject>> projects(ViewerSession actor) async => [
    const ViewerProject('p', 'Ventas', '', 'EDITOR'),
  ];
  @override
  Future<ViewerDiagram> diagram(ViewerSession actor, String project) async =>
      ViewerDiagram(project, 4, model());
  @override
  Future<ViewerPage<TimelineEntry>> activities(
    ViewerSession actor,
    String project,
    String? cursor,
  ) async => ViewerPage([
    TimelineEntry(
      id: 'h',
      author: 'Nohemi',
      kind: 'class:create',
      message: 'Creó Libro',
      at: DateTime.utc(2026, 11, 2),
    ),
  ], null);
  @override
  Future<bool> conversationsEnabled(
    ViewerSession actor,
    String project,
  ) async => true;
  @override
  Future<ViewerPage<TimelineEntry>> conversations(
    ViewerSession actor,
    String project,
    String? cursor,
  ) async {
    if (aiError != null) throw aiError!;
    return const ViewerPage([], null);
  }

  @override
  void close() {}
}
