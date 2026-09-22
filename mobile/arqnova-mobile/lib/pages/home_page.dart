import 'package:flutter/material.dart';

import '../features/local_assistant/data/generated_backend_api.dart';
import '../features/local_assistant/presentation/assistant_page.dart';
import '../features/offline_sync/data/sync_service.dart';
import '../features/offline_sync/presentation/pending_operations_page.dart';

class HomePage extends StatefulWidget {
  const HomePage({super.key});

  @override
  State<HomePage> createState() => _HomePageState();
}

class _HomePageState extends State<HomePage> {
  late final GeneratedBackendApi _api;
  late final SyncService _syncService;

  @override
  void initState() {
    super.initState();
    _api = GeneratedBackendApi();
    _syncService = SyncService(api: _api);
    _syncService.initialize();
  }

  @override
  void dispose() {
    _syncService.dispose();
    super.dispose();
  }

  void _openAssistant() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => AssistantPage(api: _api, syncService: _syncService),
      ),
    );
  }

  void _openPendingOperations() {
    Navigator.of(context).push(
      MaterialPageRoute<void>(
        builder: (_) => PendingOperationsPage(syncService: _syncService),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Scaffold(
      appBar: AppBar(title: const Text('ArqNova Mobile')),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: _syncService,
          builder:
              (context, _) => Column(
                children: [
                  _ConnectionIndicator(service: _syncService),
                  Expanded(
                    child: Center(
                      child: SingleChildScrollView(
                        padding: const EdgeInsets.all(24),
                        child: ConstrainedBox(
                          constraints: const BoxConstraints(maxWidth: 520),
                          child: Card(
                            child: Padding(
                              padding: const EdgeInsets.all(28),
                              child: Column(
                                mainAxisSize: MainAxisSize.min,
                                children: [
                                  CircleAvatar(
                                    radius: 42,
                                    backgroundColor: colors.primaryContainer,
                                    child: Icon(
                                      Icons.account_tree_rounded,
                                      size: 44,
                                      color: colors.primary,
                                    ),
                                  ),
                                  const SizedBox(height: 20),
                                  Text(
                                    'ArqNova Mobile',
                                    style: Theme.of(context)
                                        .textTheme
                                        .headlineSmall
                                        ?.copyWith(fontWeight: FontWeight.w700),
                                    textAlign: TextAlign.center,
                                  ),
                                  const SizedBox(height: 12),
                                  Text(
                                    'Asistente IA Local',
                                    style:
                                        Theme.of(context).textTheme.titleLarge,
                                    textAlign: TextAlign.center,
                                  ),
                                  const SizedBox(height: 10),
                                  const Text(
                                    'Interactúe con el sistema mediante comandos de voz o texto.',
                                    textAlign: TextAlign.center,
                                  ),
                                  const SizedBox(height: 26),
                                  SizedBox(
                                    width: double.infinity,
                                    child: FilledButton.icon(
                                      onPressed: _openAssistant,
                                      icon: const Icon(Icons.smart_toy_rounded),
                                      label: const Text(
                                        'Abrir Asistente IA Local',
                                      ),
                                    ),
                                  ),
                                  const SizedBox(height: 12),
                                  SizedBox(
                                    width: double.infinity,
                                    child: OutlinedButton.icon(
                                      onPressed: _openPendingOperations,
                                      icon: const Icon(
                                        Icons.cloud_upload_outlined,
                                      ),
                                      label: Text(
                                        'Operaciones pendientes '
                                        '(${_syncService.pendingCount})',
                                      ),
                                    ),
                                  ),
                                ],
                              ),
                            ),
                          ),
                        ),
                      ),
                    ),
                  ),
                ],
              ),
        ),
      ),
    );
  }
}

class _ConnectionIndicator extends StatelessWidget {
  const _ConnectionIndicator({required this.service});
  final SyncService service;

  @override
  Widget build(BuildContext context) {
    final status = service.status;
    final offline = status == MobileConnectionStatus.offline;
    final syncing = status == MobileConnectionStatus.syncing;
    return Container(
      width: double.infinity,
      color:
          offline
              ? Colors.red.shade50
              : syncing
              ? Colors.blue.shade50
              : Colors.green.shade50,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 11),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.center,
        children: [
          Icon(
            offline
                ? Icons.wifi_off_rounded
                : syncing
                ? Icons.sync_rounded
                : Icons.wifi_rounded,
            size: 18,
          ),
          const SizedBox(width: 8),
          Flexible(
            child: Text(
              syncing
                  ? 'Sincronizando operaciones pendientes...'
                  : offline
                  ? 'Sin conexión. Las operaciones serán sincronizadas posteriormente.'
                  : 'Conectado',
              textAlign: TextAlign.center,
            ),
          ),
        ],
      ),
    );
  }
}
