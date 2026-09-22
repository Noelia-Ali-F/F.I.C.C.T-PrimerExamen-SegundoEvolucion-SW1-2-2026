import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:speech_to_text/speech_recognition_result.dart';
import 'package:speech_to_text/speech_to_text.dart';

import '../data/generated_backend_api.dart';
import '../domain/assistant_command.dart';
import '../domain/local_intent_engine.dart';
import '../../offline_sync/data/sync_service.dart';

enum _AssistantStatus {
  ready,
  listening,
  interpreting,
  confirming,
  executing,
  success,
  error,
}

class AssistantPage extends StatefulWidget {
  const AssistantPage({
    super.key,
    required this.api,
    required this.syncService,
    this.intentEngine = const LocalIntentEngine(),
  });

  final GeneratedBackendApi api;
  final SyncService syncService;
  final LocalIntentEngine intentEngine;

  @override
  State<AssistantPage> createState() => _AssistantPageState();
}

class _AssistantPageState extends State<AssistantPage> {
  final _controller = TextEditingController();
  final _speech = SpeechToText();
  _AssistantStatus _status = _AssistantStatus.ready;
  String? _message;
  String? _transcript;
  AssistantCommand? _command;
  dynamic _result;
  bool _voiceFinalHandled = false;

  @override
  void dispose() {
    _speech.cancel();
    _controller.dispose();
    super.dispose();
  }

  Future<void> _toggleVoice() async {
    if (_speech.isListening) {
      await _speech.stop();
      final words = _transcript?.trim() ?? '';
      if (words.isNotEmpty && !_voiceFinalHandled) {
        _voiceFinalHandled = true;
        await _interpret(words);
      } else if (mounted) {
        setState(() => _status = _AssistantStatus.ready);
      }
      return;
    }
    final available = await _speech.initialize(
      onError: (error) {
        if (!mounted) return;
        setState(() {
          _status = _AssistantStatus.error;
          _message =
              error.permanent
                  ? 'No se pudo acceder al micrófono. Revisa el permiso de voz.'
                  : 'No se detectó voz. Inténtalo nuevamente.';
        });
      },
      onStatus: (status) {
        if (!mounted ||
            status != 'done' ||
            _status != _AssistantStatus.listening) {
          return;
        }
        setState(() => _status = _AssistantStatus.ready);
      },
    );
    if (!mounted) return;
    if (!available) {
      setState(() {
        _status = _AssistantStatus.error;
        _message =
            'El reconocimiento de voz no está disponible en este dispositivo.';
      });
      return;
    }
    setState(() {
      _status = _AssistantStatus.listening;
      _message = 'Te estoy escuchando…';
      _transcript = '';
      _voiceFinalHandled = false;
    });
    await _speech.listen(
      onResult: _onSpeechResult,
      listenOptions: SpeechListenOptions(
        localeId: 'es_BO',
        partialResults: true,
      ),
    );
  }

  void _onSpeechResult(SpeechRecognitionResult result) {
    if (!mounted) return;
    final words = result.recognizedWords.trim();
    setState(() {
      _transcript = words;
      _controller.text = words;
      _controller.selection = TextSelection.collapsed(offset: words.length);
    });
    if (result.finalResult && words.isNotEmpty && !_voiceFinalHandled) {
      _voiceFinalHandled = true;
      _speech.stop();
      _interpret(words);
    }
  }

  Future<void> _interpret([String? value]) async {
    if (_status == _AssistantStatus.executing) return;
    FocusScope.of(context).unfocus();
    final input = (value ?? _controller.text).trim();
    if (input.isEmpty) {
      setState(() {
        _status = _AssistantStatus.error;
        _message = 'Escribe o dicta una instrucción antes de ejecutar.';
      });
      return;
    }
    setState(() {
      _status = _AssistantStatus.interpreting;
      _message = 'Interpretando localmente…';
      _command = null;
      _result = null;
    });
    await Future<void>.delayed(const Duration(milliseconds: 120));
    final interpretation = widget.intentEngine.interpret(input);
    if (!mounted) return;
    if (!interpretation.isReady) {
      setState(() {
        _status = _AssistantStatus.error;
        _message = interpretation.clarification;
      });
      return;
    }
    final command = interpretation.command!;
    setState(() {
      _command = command;
      _status =
          command.isDestructive
              ? _AssistantStatus.confirming
              : _AssistantStatus.executing;
      _message =
          command.isDestructive
              ? 'Confirma la eliminación antes de continuar.'
              : 'Ejecutando acción en el backend…';
    });
    if (command.isDestructive) {
      final confirmed = await _confirmDelete(command);
      if (!confirmed || !mounted) {
        if (mounted) {
          setState(() {
            _status = _AssistantStatus.ready;
            _message = 'Operación cancelada. No se modificó información.';
          });
        }
        return;
      }
    }
    await _execute(command);
  }

  Future<bool> _confirmDelete(AssistantCommand command) async {
    return await showDialog<bool>(
          context: context,
          builder:
              (context) => AlertDialog(
                icon: const Icon(Icons.warning_amber_rounded),
                title: const Text('Confirmar eliminación'),
                content: Text(
                  'Se eliminará ${command.lookupName ?? 'el registro ${command.entityId ?? ''}'} de ${command.entity}. Esta acción no se puede deshacer.',
                ),
                actions: [
                  TextButton(
                    onPressed: () => Navigator.pop(context, false),
                    child: const Text('Cancelar'),
                  ),
                  FilledButton(
                    onPressed: () => Navigator.pop(context, true),
                    child: const Text('Eliminar'),
                  ),
                ],
              ),
        ) ??
        false;
  }

  Future<void> _execute(AssistantCommand command) async {
    if (mounted) {
      setState(() {
        _status = _AssistantStatus.executing;
        _message = 'Ejecutando acción en el backend…';
      });
    }
    if (widget.syncService.isOffline &&
        command.intent != AssistantIntent.query) {
      await _saveOffline(command);
      return;
    }
    try {
      final result = await widget.api.execute(command);
      if (!mounted) return;
      setState(() {
        _status = _AssistantStatus.success;
        _message = result.message;
        _result = result.data;
      });
    } on AssistantOfflineException catch (error) {
      widget.syncService.markOffline();
      if (command.intent != AssistantIntent.query) {
        await _saveOffline(command);
        return;
      }
      if (!mounted) return;
      setState(() {
        _status = _AssistantStatus.error;
        _message = error.message;
      });
    } on AssistantApiException catch (error) {
      if (!mounted) return;
      setState(() {
        _status = _AssistantStatus.error;
        _message = error.message;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _status = _AssistantStatus.error;
        _message = 'Ocurrió un error inesperado. Inténtalo nuevamente.';
      });
    }
  }

  Future<void> _saveOffline(AssistantCommand command) async {
    try {
      await widget.syncService.enqueue(command);
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _status = _AssistantStatus.error;
        _message = 'No se pudo guardar la operación en el dispositivo.';
      });
      return;
    }
    if (!mounted) return;
    setState(() {
      _status = _AssistantStatus.success;
      _message =
          '${command.entity} guardado localmente. Se sincronizará cuando vuelva la conexión.';
      _result = {
        'estado': 'PENDIENTE',
        'entidad': command.entity,
        'accion': command.intentLabel,
      };
    });
  }

  @override
  Widget build(BuildContext context) {
    final busy =
        _status == _AssistantStatus.interpreting ||
        _status == _AssistantStatus.executing;
    return Scaffold(
      appBar: AppBar(
        title: const Text('Asistente IA local'),
        actions: const [
          Padding(
            padding: EdgeInsets.only(right: 16),
            child: Tooltip(
              message: 'El texto se interpreta en este dispositivo',
              child: Icon(Icons.offline_bolt_rounded),
            ),
          ),
        ],
      ),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: widget.syncService,
          builder:
              (context, _) => Column(
                children: [
                  if (widget.syncService.isOffline)
                    Container(
                      width: double.infinity,
                      color: Colors.orange.shade50,
                      padding: const EdgeInsets.symmetric(
                        horizontal: 20,
                        vertical: 10,
                      ),
                      child: const Text(
                        'Sin conexión · las operaciones se guardarán localmente.',
                        textAlign: TextAlign.center,
                      ),
                    ),
                  Expanded(
                    child: ListView(
                      padding: const EdgeInsets.fromLTRB(20, 12, 20, 24),
                      children: [
                        Card(
                          child: Padding(
                            padding: const EdgeInsets.all(20),
                            child: Column(
                              children: [
                                CircleAvatar(
                                  radius: 34,
                                  backgroundColor:
                                      Theme.of(
                                        context,
                                      ).colorScheme.primaryContainer,
                                  child: Icon(
                                    Icons.smart_toy_rounded,
                                    size: 38,
                                    color:
                                        Theme.of(context).colorScheme.primary,
                                  ),
                                ),
                                const SizedBox(height: 14),
                                Text(
                                  '¿Qué necesitas hacer?',
                                  style: Theme.of(context).textTheme.titleLarge,
                                ),
                                const SizedBox(height: 6),
                                const Text(
                                  'Puedo registrar, consultar, modificar o eliminar información usando la API REST generada.',
                                  textAlign: TextAlign.center,
                                ),
                                const SizedBox(height: 18),
                                TextField(
                                  controller: _controller,
                                  minLines: 2,
                                  maxLines: 4,
                                  enabled: !busy,
                                  decoration: const InputDecoration(
                                    hintText:
                                        'Ej. Registrar cliente Juan Pérez con teléfono 70000000',
                                    prefixIcon: Icon(
                                      Icons.chat_bubble_outline_rounded,
                                    ),
                                  ),
                                  onSubmitted: (_) => _interpret(),
                                ),
                                const SizedBox(height: 12),
                                Row(
                                  children: [
                                    Expanded(
                                      child: OutlinedButton.icon(
                                        onPressed: busy ? null : _toggleVoice,
                                        icon: Icon(
                                          _speech.isListening
                                              ? Icons.stop_rounded
                                              : Icons.mic_rounded,
                                        ),
                                        label: Text(
                                          _speech.isListening
                                              ? 'Detener'
                                              : 'Hablar',
                                        ),
                                      ),
                                    ),
                                    const SizedBox(width: 10),
                                    Expanded(
                                      child: FilledButton.icon(
                                        onPressed: busy ? null : _interpret,
                                        icon:
                                            busy
                                                ? const SizedBox.square(
                                                  dimension: 16,
                                                  child:
                                                      CircularProgressIndicator(
                                                        strokeWidth: 2,
                                                      ),
                                                )
                                                : const Icon(
                                                  Icons.send_rounded,
                                                ),
                                        label: const Text('Ejecutar'),
                                      ),
                                    ),
                                  ],
                                ),
                                if (_transcript?.isNotEmpty == true) ...[
                                  const SizedBox(height: 12),
                                  Align(
                                    alignment: Alignment.centerLeft,
                                    child: Text(
                                      'Transcripción: ${_transcript!}',
                                    ),
                                  ),
                                ],
                              ],
                            ),
                          ),
                        ),
                        if (_message != null) ...[
                          const SizedBox(height: 16),
                          _StatusCard(status: _status, message: _message!),
                        ],
                        if (_command != null) ...[
                          const SizedBox(height: 16),
                          _JsonCard(
                            title: 'Intención local',
                            value: _command!.toJson(),
                          ),
                        ],
                        if (_result != null) ...[
                          const SizedBox(height: 16),
                          _JsonCard(
                            title: 'Resultado del backend',
                            value: _result,
                          ),
                        ],
                        const SizedBox(height: 16),
                        const Card(
                          child: Padding(
                            padding: EdgeInsets.all(18),
                            child: Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                Text('Ejemplos'),
                                SizedBox(height: 8),
                                Text(
                                  '• Registrar cliente Ana López con teléfono 72233445',
                                ),
                                Text('• Buscar los clientes registrados'),
                                Text(
                                  '• Modificar el teléfono de Ana López a 76543210',
                                ),
                                Text('• Eliminar cliente id 12'),
                              ],
                            ),
                          ),
                        ),
                      ],
                    ),
                  ),
                ],
              ),
        ),
      ),
    );
  }
}

class _StatusCard extends StatelessWidget {
  const _StatusCard({required this.status, required this.message});
  final _AssistantStatus status;
  final String message;

  @override
  Widget build(BuildContext context) {
    final error = status == _AssistantStatus.error;
    final success = status == _AssistantStatus.success;
    final color =
        error
            ? Theme.of(context).colorScheme.error
            : success
            ? const Color(0xFF16875B)
            : Theme.of(context).colorScheme.primary;
    return Card(
      child: ListTile(
        leading: Icon(
          error
              ? Icons.error_outline_rounded
              : success
              ? Icons.check_circle_outline_rounded
              : Icons.hourglass_top_rounded,
          color: color,
        ),
        title: Text(message),
      ),
    );
  }
}

class _JsonCard extends StatelessWidget {
  const _JsonCard({required this.title, required this.value});
  final String title;
  final dynamic value;

  @override
  Widget build(BuildContext context) {
    const encoder = JsonEncoder.withIndent('  ');
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(18),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(title, style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 10),
            SelectableText(
              value is String ? value as String : encoder.convert(value),
              style: const TextStyle(fontFamily: 'monospace'),
            ),
          ],
        ),
      ),
    );
  }
}
