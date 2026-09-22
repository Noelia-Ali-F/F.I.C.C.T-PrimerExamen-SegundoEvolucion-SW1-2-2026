import 'package:flutter/material.dart';

import '../data/sync_service.dart';
import '../domain/pending_operation.dart';

class PendingOperationsPage extends StatelessWidget {
  const PendingOperationsPage({super.key, required this.syncService});

  final SyncService syncService;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Operaciones pendientes')),
      body: SafeArea(
        child: ListenableBuilder(
          listenable: syncService,
          builder: (context, _) {
            final operations = syncService.operations;
            return Column(
              children: [
                _ConnectionBanner(service: syncService),
                Expanded(
                  child:
                      operations.isEmpty
                          ? const Center(
                            child: Text('No hay operaciones almacenadas.'),
                          )
                          : ListView.separated(
                            padding: const EdgeInsets.all(16),
                            itemCount: operations.length,
                            separatorBuilder:
                                (_, _) => const SizedBox(height: 10),
                            itemBuilder:
                                (_, index) => _OperationCard(
                                  operation: operations[index],
                                ),
                          ),
                ),
                SafeArea(
                  top: false,
                  minimum: const EdgeInsets.all(16),
                  child: SizedBox(
                    width: double.infinity,
                    child: FilledButton.icon(
                      onPressed:
                          syncService.status == MobileConnectionStatus.online &&
                                  syncService.pendingCount > 0
                              ? syncService.synchronize
                              : null,
                      icon:
                          syncService.status == MobileConnectionStatus.syncing
                              ? const SizedBox.square(
                                dimension: 18,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              )
                              : const Icon(Icons.sync_rounded),
                      label: const Text('Sincronizar ahora'),
                    ),
                  ),
                ),
              ],
            );
          },
        ),
      ),
    );
  }
}

class _ConnectionBanner extends StatelessWidget {
  const _ConnectionBanner({required this.service});
  final SyncService service;

  @override
  Widget build(BuildContext context) {
    final status = service.status;
    final offline = status == MobileConnectionStatus.offline;
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 18, vertical: 12),
      color: offline ? Colors.orange.shade50 : Colors.green.shade50,
      child: Text(
        status == MobileConnectionStatus.syncing
            ? 'Sincronizando operaciones pendientes...'
            : offline
            ? 'Sin conexión. Las operaciones serán sincronizadas posteriormente.'
            : 'Conectado',
        textAlign: TextAlign.center,
      ),
    );
  }
}

class _OperationCard extends StatelessWidget {
  const _OperationCard({required this.operation});
  final PendingOperation operation;

  @override
  Widget build(BuildContext context) {
    final color = switch (operation.status) {
      PendingOperationStatus.pending => Colors.orange,
      PendingOperationStatus.synchronized => Colors.green,
      PendingOperationStatus.error => Theme.of(context).colorScheme.error,
    };
    final name = operation.data['nombre']?.toString();
    return Card(
      child: ListTile(
        leading: CircleAvatar(
          backgroundColor: color.withValues(alpha: .12),
          child: Icon(Icons.cloud_upload_outlined, color: color),
        ),
        title: Text(
          name == null ? operation.entity : '${operation.entity} · $name',
        ),
        subtitle: Text(
          '${operation.action}\n${_formatDate(operation.createdAt.toLocal())}\n'
          '${operation.status.name.toUpperCase()}'
          '\nIntentos: ${operation.attemptCount}'
          '${operation.lastAttemptAt == null ? '' : '\nÚltimo intento: ${_formatDate(operation.lastAttemptAt!.toLocal())}'}'
          '${operation.synchronizedAt == null ? '' : '\nSincronizada: ${_formatDate(operation.synchronizedAt!.toLocal())}'}'
          '${operation.errorMessage == null ? '' : '\n${operation.errorMessage}'}',
        ),
        isThreeLine: true,
      ),
    );
  }

  String _formatDate(DateTime date) =>
      '${date.day.toString().padLeft(2, '0')}/'
      '${date.month.toString().padLeft(2, '0')}/${date.year} '
      '${date.hour.toString().padLeft(2, '0')}:'
      '${date.minute.toString().padLeft(2, '0')}';
}
