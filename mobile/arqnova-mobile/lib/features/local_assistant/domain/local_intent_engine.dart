import 'assistant_command.dart';

/// Intérprete local, cerrado y determinista. No envía texto a servicios cloud.
class LocalIntentEngine {
  const LocalIntentEngine();

  AssistantInterpretation interpret(String input) {
    final original = input.trim();
    if (original.isEmpty) {
      return const AssistantInterpretation.clarification(
        'Escribe o dicta una instrucción.',
      );
    }

    final normalized = _normalize(original);
    final intent = _intent(normalized);
    if (intent == null) {
      return const AssistantInterpretation.clarification(
        'No entendí la acción. Intenta usar registrar, buscar, modificar o eliminar.',
      );
    }

    final entity = _entity(normalized);
    if (entity == null) {
      return const AssistantInterpretation.clarification(
        'No pude identificar el recurso. Indica, por ejemplo, cliente, producto o venta.',
      );
    }

    final id = RegExp(
      r'\b(?:id|numero)\s*#?\s*([\w-]+)\b',
    ).firstMatch(normalized)?.group(1);
    final data = _extractData(original, intent, entity);
    final lookupName = _lookupName(original, normalized, intent, entity, id);

    if ((intent == AssistantIntent.update ||
            intent == AssistantIntent.delete) &&
        id == null &&
        lookupName == null) {
      return AssistantInterpretation.clarification(
        'Indica el id o el nombre del ${_displayEntity(entity)} que deseas ${intent == AssistantIntent.update ? 'modificar' : 'eliminar'}.',
      );
    }
    if (intent == AssistantIntent.create && data.isEmpty) {
      return AssistantInterpretation.clarification(
        'Indica los datos del nuevo ${_displayEntity(entity)}.',
      );
    }
    if (intent == AssistantIntent.update && data.isEmpty) {
      return const AssistantInterpretation.clarification(
        'Indica qué campo deseas modificar y su nuevo valor.',
      );
    }

    return AssistantInterpretation.ready(
      AssistantCommand(
        intent: intent,
        entity: entity,
        entityId: id,
        lookupName: lookupName,
        data: data,
        originalText: original,
      ),
    );
  }

  AssistantIntent? _intent(String value) {
    if (RegExp(
      r'\b(registra|registrar|crea|crear|nuevo|nueva|agrega|agregar)\b',
    ).hasMatch(value)) {
      return AssistantIntent.create;
    }
    if (RegExp(
      r'\b(busca|buscar|consulta|consultar|muestra|mostrar|lista|listar)\b',
    ).hasMatch(value)) {
      return AssistantIntent.query;
    }
    if (RegExp(
      r'\b(modifica|modificar|actualiza|actualizar|cambia|cambiar)\b',
    ).hasMatch(value)) {
      return AssistantIntent.update;
    }
    if (RegExp(r'\b(elimina|eliminar|borra|borrar)\b').hasMatch(value)) {
      return AssistantIntent.delete;
    }
    return null;
  }

  String? _entity(String value) {
    const aliases = {
      'clientes': 'cliente',
      'cliente': 'cliente',
      'productos': 'producto',
      'producto': 'producto',
      'ventas': 'venta',
      'venta': 'venta',
      'usuarios': 'usuario',
      'usuario': 'usuario',
    };
    for (final entry in aliases.entries) {
      if (RegExp('\\b${entry.key}\\b').hasMatch(value)) return entry.value;
    }
    final generic = RegExp(
      r'\b(?:registra(?:r)?|crea(?:r)?|agrega(?:r)?|busca(?:r)?|consulta(?:r)?|muestra|mostrar|lista(?:r)?|modifica(?:r)?|actualiza(?:r)?|cambia(?:r)?|elimina(?:r)?|borra(?:r)?)\s+(?:(?:un|una|el|la|los|las|nuevo|nueva)\s+)*([a-z][a-z0-9_-]+)',
    ).firstMatch(value)?.group(1);
    if (generic == null || const {'informacion', 'datos'}.contains(generic)) {
      return null;
    }
    return generic.endsWith('es')
        ? generic.substring(0, generic.length - 2)
        : generic.endsWith('s')
        ? generic.substring(0, generic.length - 1)
        : generic;
  }

  Map<String, dynamic> _extractData(
    String original,
    AssistantIntent intent,
    String entity,
  ) {
    if (intent == AssistantIntent.query || intent == AssistantIntent.delete) {
      return const {};
    }
    final data = <String, dynamic>{};
    _capture(
      data,
      'telefono',
      original,
      r'(?:tel[eé]fono|celular)\s*(?:es|a|:)?\s*([+\d][\d\s-]{5,})',
    );
    _capture(
      data,
      'email',
      original,
      r'(?:correo|email)\s*(?:es|a|:)?\s*([\w.+-]+@[\w.-]+\.[A-Za-z]{2,})',
    );
    _capture(
      data,
      'nombre',
      original,
      r'(?:nombre|llamad[oa])\s*(?:es|a|:)?\s*([\p{L}][\p{L}\s]{1,60}?)(?=\s+(?:con|y|tel[eé]fono|celular|correo|email|id)\b|$)',
      unicode: true,
    );

    if (intent == AssistantIntent.create && !data.containsKey('nombre')) {
      final display = _displayEntity(entity);
      final match = RegExp(
        '(?:${display}s?|$entity)\\s+([\\p{L}][\\p{L}\\s]{1,60}?)(?=\\s+(?:con|y|tel[eé]fono|celular|correo|email|id)\\b|\$)',
        caseSensitive: false,
        unicode: true,
      ).firstMatch(original);
      final candidate = match?.group(1)?.trim();
      if (candidate != null && candidate.isNotEmpty) data['nombre'] = candidate;
    }

    if (intent == AssistantIntent.update) {
      final change = RegExp(
        r'(?:el|la)\s+([A-Za-zÁÉÍÓÚáéíóúÑñ_][\wÁÉÍÓÚáéíóúÑñ]*)\s+(?:de\s+[\p{L}\s]+?\s+)?a\s+(.+)$',
        caseSensitive: false,
        unicode: true,
      ).firstMatch(original);
      if (change != null) {
        final field = _normalize(change.group(1)!).replaceAll(' ', '');
        data[field] = change.group(2)!.trim();
      }
    }
    return data;
  }

  String? _lookupName(
    String original,
    String normalized,
    AssistantIntent intent,
    String entity,
    String? id,
  ) {
    if (id != null || intent == AssistantIntent.create) return null;
    final patterns = [
      RegExp(
        r'\bde\s+([\p{L}][\p{L}\s]{1,50}?)(?=\s+a\s+|$)',
        caseSensitive: false,
        unicode: true,
      ),
      RegExp(
        '\\b$entity\\s+([\\p{L}][\\p{L}\\s]{1,50})\$',
        caseSensitive: false,
        unicode: true,
      ),
    ];
    for (final pattern in patterns) {
      final value = pattern.firstMatch(original)?.group(1)?.trim();
      if (value != null && value.isNotEmpty) return value;
    }
    return null;
  }

  void _capture(
    Map<String, dynamic> target,
    String key,
    String input,
    String pattern, {
    bool unicode = false,
  }) {
    final value =
        RegExp(
          pattern,
          caseSensitive: false,
          unicode: unicode,
        ).firstMatch(input)?.group(1)?.trim();
    if (value != null && value.isNotEmpty) target[key] = value;
  }

  String _displayEntity(String entity) => entity;

  String _normalize(String value) =>
      value
          .toLowerCase()
          .replaceAll('á', 'a')
          .replaceAll('é', 'e')
          .replaceAll('í', 'i')
          .replaceAll('ó', 'o')
          .replaceAll('ú', 'u')
          .replaceAll(RegExp(r'\s+'), ' ')
          .trim();
}
