import 'package:arqnova_mobile/features/project_viewer/application/project_viewer_use_cases.dart';
import 'package:arqnova_mobile/features/project_viewer/data/http_project_viewer_adapter.dart';
import 'package:arqnova_mobile/features/project_viewer/domain/project_models.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/project_detail_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'project_viewer_test.dart' show ok, session;

Map<String, Object> row(int n) => {
  'id': 'event-$n',
  'author': 'Exintegrante',
  'kind': 'class:create',
  'description': 'Creó una clase',
  'timestamp': 1000 + n,
  if (n == 40) ...{
    'elementId': 'sale',
    'elementType': 'class',
    'elementName': 'Venta',
    'executor': 'AI',
    'requesterId': 'former',
    'provider': 'GROQ',
    'requestId': 'matched',
  },
};

void main() {
  test(
    'conserva un cursor con 503 y reintenta solo la fuente fallida sin duplicar 30+11',
    () async {
      var attempts = 0;
      var aiCalls = 0;
      final api = HttpProjectViewerAdapter(
        client: MockClient((r) async {
          expect(r.method, 'GET');
          if (r.url.path.endsWith('/status')) return ok({'enabled': true});
          if (r.url.path.endsWith('/messages')) {
            aiCalls++;
            return ok({'turns': [], 'nextCursor': 'ai-kept'});
          }
          final cursor = r.url.queryParameters['cursor'];
          if (cursor == null) {
            return ok({
              'items': [for (var n = 40; n >= 11; n--) row(n)],
              'nextCursor': 'sql-next',
            });
          }
          expect(cursor, 'sql-next');
          if (attempts++ == 0) return http.Response('{}', 503);
          return ok({
            'items': [for (var n = 10; n >= 0; n--) row(n)],
            'nextCursor': null,
          });
        }),
      );
      final cases = ProjectViewerUseCases(api);
      final first = await cases.timeline(session, 'p');
      final failed = await cases.timeline(
        session,
        'p',
        activityCursor: first.activityCursor,
        aiCursor: first.aiCursor,
        loadAi: false,
      );
      expect(failed.failures.keys, [TimelineSource.activity]);
      expect(failed.activityCursor, 'sql-next');
      expect(failed.aiCursor, 'ai-kept');
      final retried = await cases.timeline(
        session,
        'p',
        activityCursor: failed.activityCursor,
        aiCursor: failed.aiCursor,
        loadAi: false,
      );
      final all = ProjectViewerUseCases.merge(first.entries, [
        ...first.entries,
        ...retried.entries,
      ]);
      expect(all.length, 41);
      expect(all.map((e) => e.at.millisecondsSinceEpoch), [
        for (var n = 40; n >= 0; n--) 1000 + n,
      ]);
      expect(retried.aiCursor, 'ai-kept');
      expect(retried.activityCursor, isNull);
      expect(aiCalls, 1);
      expect(all.first.confirmedAiChange, isTrue);
      expect(all.first.author, 'Exintegrante');
      expect(all.first.elementName, 'Venta');
      expect(all.last.elementName, isNull);
      api.close();
    },
  );

  testWidgets(
    'solo enlaza cambios SQL confirmados con la conversacion coincidente',
    (tester) async {
      final verbs = <String>[];
      final api = HttpProjectViewerAdapter(
        client: MockClient((r) async {
          verbs.add(r.method);
          if (r.url.path.endsWith('/diagram')) {
            return ok({
              'modelVersion': 1,
              'model': {
                'format': 'uml-class-diagram',
                'classes': [],
                'relations': [],
              },
            });
          }
          if (r.url.path.endsWith('/activities')) {
            return ok({
              'items': [row(40), row(0)],
              'nextCursor': null,
            });
          }
          if (r.url.path.endsWith('/status')) return ok({'enabled': true});
          return ok({
            'turns': [
              {
                'requestId': 'matched',
                'authorName': 'Exintegrante',
                'mode': 'MODEL',
                'instruction': 'Renombra Venta',
                'state': 'COMPLETED',
                'answer': {
                  'text': 'Confirmada',
                  'operations': [{}],
                },
                'updatedAt': '1970-01-01T00:00:01Z',
              },
              {
                'requestId': 'unmatched',
                'authorName': 'Ana',
                'mode': 'MODEL',
                'instruction': 'Cambia algo',
                'state': 'COMPLETED',
                'answer': {'text': 'Aclara', 'operations': []},
                'updatedAt': '1970-01-01T00:00:01Z',
              },
            ],
            'nextCursor': null,
          });
        }),
      );
      await tester.pumpWidget(
        MaterialApp(
          home: ProjectDetailPage(
            useCases: ProjectViewerUseCases(api),
            session: session,
            project: const ViewerProject('p', 'Ventas', '', 'VIEWER'),
          ),
        ),
      );
      await tester.pumpAndSettle();
      await tester.tap(find.text('Actividad'));
      await tester.pumpAndSettle();
      expect(
        find.text('Exintegrante · Modificación UML por IA'),
        findsOneWidget,
      );
      expect(find.text('Elemento: Venta'), findsOneWidget);
      expect(find.text('Proveedor: GROQ'), findsOneWidget);
      await tester.tap(find.text('Exintegrante · Modificación UML por IA'));
      await tester.pumpAndSettle();
      expect(find.text('Conversación vinculada'), findsOneWidget);
      await tester.drag(find.byType(ListView).last, const Offset(0, -500));
      await tester.pumpAndSettle();
      expect(find.text('Ana · Modelado con IA · sin cambios'), findsOneWidget);
      expect(verbs.every((v) => v == 'GET'), isTrue);
    },
  );
}
