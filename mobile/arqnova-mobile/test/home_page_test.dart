import 'package:arqnova_mobile/pages/home_page.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';

void main() {
  testWidgets('abre el Asistente IA Local desde el inicio', (tester) async {
    await tester.pumpWidget(const MaterialApp(home: HomePage()));

    expect(find.text('ArqNova Mobile'), findsWidgets);
    expect(find.text('Abrir Asistente IA Local'), findsOneWidget);

    await tester.tap(find.text('Abrir Asistente IA Local'));
    await tester.pumpAndSettle();

    expect(find.text('Asistente IA local'), findsOneWidget);
    expect(find.text('Ejecutar'), findsOneWidget);
  });
}
