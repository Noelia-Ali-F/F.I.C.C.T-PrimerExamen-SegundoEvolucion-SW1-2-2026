import 'package:flutter_test/flutter_test.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/assistant_command.dart';
import 'package:arqnova_mobile/features/local_assistant/domain/local_intent_engine.dart';

void main() {
  const engine = LocalIntentEngine();

  test('interpreta registro con datos sin conexión ni nube', () {
    final result = engine.interpret(
      'Registrar cliente Juan Pérez con teléfono 70000000',
    );

    expect(result.isReady, isTrue);
    expect(result.command!.intent, AssistantIntent.create);
    expect(result.command!.entity, 'cliente');
    expect(result.command!.data['nombre'], 'Juan Pérez');
    expect(result.command!.data['telefono'], '70000000');
  });

  test('interpreta consulta plural', () {
    final result = engine.interpret('Busca los clientes registrados');

    expect(result.isReady, isTrue);
    expect(result.command!.intent, AssistantIntent.query);
    expect(result.command!.entity, 'cliente');
  });

  test('interpreta actualización por nombre', () {
    final result = engine.interpret('Modifica el teléfono de Pedro a 76543210');

    expect(result.isReady, isTrue);
    expect(result.command!.intent, AssistantIntent.update);
    expect(result.command!.lookupName, 'Pedro');
    expect(result.command!.data['telefono'], '76543210');
  });

  test('marca eliminación como destructiva', () {
    final result = engine.interpret('Eliminar cliente id 12');

    expect(result.isReady, isTrue);
    expect(result.command!.isDestructive, isTrue);
    expect(result.command!.entityId, '12');
  });

  test('solicita reformulación ante comando inválido', () {
    final result = engine.interpret('Que lindo día');

    expect(result.isReady, isFalse);
    expect(result.clarification, isNotEmpty);
  });
}
