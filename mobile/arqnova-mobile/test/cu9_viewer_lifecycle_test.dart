import 'dart:async';
import 'package:arqnova_mobile/features/project_viewer/application/project_viewer_use_cases.dart';
import 'package:arqnova_mobile/features/project_viewer/domain/project_models.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/project_detail_page.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/project_explorer_page.dart';
import 'package:arqnova_mobile/features/project_viewer/presentation/read_only_diagram.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'project_viewer_test.dart' show FakeViewer, model, session;

class ControlledViewer extends FakeViewer {
  final requests = <Completer<ViewerDiagram>>[];
  @override
  Future<ViewerDiagram> diagram(ViewerSession actor, String project) {
    final request = Completer<ViewerDiagram>();
    requests.add(request);
    return request.future;
  }
}

const project = ViewerProject('p', 'Ventas', '', 'VIEWER');

Widget detail(
  ProjectViewerUseCases useCases, [
  ViewerProject selected = project,
]) => MaterialApp(
  home: ProjectDetailPage(
    useCases: useCases,
    session: session,
    project: selected,
  ),
);

void main() {
  for (final status in [401, 403, 404]) {
    testWidgets('borra detalles y rechaza respuestas anteriores tras $status', (
      tester,
    ) async {
      final port = ControlledViewer();
      await tester.pumpWidget(detail(ProjectViewerUseCases(port)));
      port.requests[0].complete(ViewerDiagram('p', 1, model()));
      await tester.pumpAndSettle();
      await tester.tap(find.byTooltip('Ver clases y relaciones'));
      await tester.pumpAndSettle();
      expect(find.text('Clases y relaciones'), findsOneWidget);

      await tester.tap(find.byTooltip('Actualizar'));
      await tester.pump();
      expect(find.text('Clases y relaciones'), findsNothing);
      expect(
        tester
            .widget<IconButton>(
              find.byWidgetPredicate(
                (widget) =>
                    widget is IconButton &&
                    widget.tooltip == 'Ver clases y relaciones',
              ),
            )
            .onPressed,
        isNull,
      );
      await tester.tap(find.byTooltip('Actualizar'));
      await tester.pump();
      port.requests[2].completeError(
        ViewerException('Acceso no disponible $status', status),
      );
      await tester.pumpAndSettle();
      port.requests[1].complete(ViewerDiagram('p', 2, model()));
      await tester.pumpAndSettle();
      expect(find.text('Acceso no disponible $status'), findsOneWidget);
      expect(find.byType(ReadOnlyDiagram), findsNothing);
      expect(find.text('Clases y relaciones'), findsNothing);
      expect(
        tester
            .widget<IconButton>(
              find.byWidgetPredicate(
                (widget) =>
                    widget is IconButton &&
                    widget.tooltip == 'Ver clases y relaciones',
              ),
            )
            .onPressed,
        isNull,
      );
      expect(tester.takeException(), isNull);
    });
  }

  testWidgets('un 401 cierra la vista y devuelve al formulario de acceso', (
    tester,
  ) async {
    final port = ControlledViewer();
    await tester.pumpWidget(
      MaterialApp(
        home: ProjectExplorerPage(useCases: ProjectViewerUseCases(port)),
      ),
    );
    await tester.enterText(find.byType(TextField).first, 'viewer@example.test');
    await tester.enterText(find.byType(TextField).last, 'password');
    await tester.tap(find.text('Iniciar sesión'));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Ventas'));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    port.requests.single.completeError(
      const ViewerException('Sesión vencida', 401),
    );
    await tester.pumpAndSettle();
    expect(find.text('Iniciar sesión'), findsOneWidget);
    expect(
      find.text('La sesión venció. Vuelve a iniciar sesión.'),
      findsOneWidget,
    );
    expect(find.byType(ProjectDetailPage), findsNothing);
  });

  testWidgets(
    'cambiar de proyecto descarta una respuesta tardía y muestra el UML vacío',
    (tester) async {
      final port = ControlledViewer();
      final useCases = ProjectViewerUseCases(port);
      await tester.pumpWidget(detail(useCases));
      await tester.pumpWidget(
        detail(
          useCases,
          const ViewerProject('q', 'Otro proyecto', '', 'VIEWER'),
        ),
      );
      port.requests[1].complete(
        const ViewerDiagram('q', 0, {'classes': [], 'relations': []}),
      );
      await tester.pumpAndSettle();
      port.requests[0].complete(ViewerDiagram('p', 1, model()));
      await tester.pumpAndSettle();
      expect(find.text('Otro proyecto'), findsOneWidget);
      expect(find.text('El diagrama está vacío.'), findsOneWidget);
      expect(find.byType(InteractiveViewer), findsNothing);
    },
  );

  testWidgets(
    'pinta 150 clases, 150 relaciones, una reflexiva y texto largo con zoom',
    (tester) async {
      final graph = <String, dynamic>{
        'classes': [
          for (var i = 0; i < 150; i++)
            {
              'id': 'c$i',
              'name': i == 0 ? 'Nombre extenso ' * 20 : 'Clase $i',
              'x': (i % 15) * 320,
              'y': (i ~/ 15) * 230,
              'attributes': [
                {'name': 'identificadorLargo' * 10, 'type': 'BigDecimal'},
              ],
            },
        ],
        'relations': [
          for (var i = 1; i < 150; i++)
            {
              'sourceId': 'c${i - 1}',
              'targetId': 'c$i',
              'type': 'composition',
              'sourceMultiplicity': '1',
              'targetMultiplicity': '0..*',
            },
          {
            'sourceId': 'c0',
            'targetId': 'c0',
            'type': 'association',
            'sourceMultiplicity': '1',
            'targetMultiplicity': '0..*',
          },
        ],
      };
      final painter = UmlViewerPainter(graph);
      final box = painter.boxes['c0']!;
      expect(box.height, greaterThan(painter.boxes['c1']!.height));
      expect(
        UmlViewerPainter.reflexivePath(box).getBounds().right,
        greaterThan(box.right),
      );
      final timer = Stopwatch()..start();
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: SizedBox(
              width: 360,
              height: 540,
              child: ReadOnlyDiagram(diagram: ViewerDiagram('p', 1, graph)),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      timer.stop();
      debugPrint(
        'CU9 150 clases/150 relaciones, montaje y paint en widget: ${timer.elapsedMilliseconds} ms',
      );
      final viewer = find.byType(InteractiveViewer);
      final transform =
          tester.widget<InteractiveViewer>(viewer).transformationController!;
      expect(transform.value.getMaxScaleOnAxis(), lessThan(.15));
      transform.value = Matrix4.identity()..scale(.5);
      await tester.pump();
      final center = tester.getCenter(viewer);
      final first = await tester.startGesture(
        center - const Offset(40, 0),
        pointer: 1,
      );
      final second = await tester.startGesture(
        center + const Offset(40, 0),
        pointer: 2,
      );
      await first.moveBy(const Offset(-40, 0));
      await second.moveBy(const Offset(40, 0));
      await tester.pump();
      await first.up();
      await second.up();
      await tester.pumpAndSettle();
      expect(transform.value.getMaxScaleOnAxis(), greaterThan(.5));
      final offset = transform.value.getTranslation();
      await tester.drag(viewer, const Offset(-110, -100));
      await tester.pumpAndSettle();
      expect(transform.value.getTranslation(), isNot(offset));
      expect(find.byType(TextField), findsNothing);
      expect(tester.takeException(), isNull);
    },
  );
}
