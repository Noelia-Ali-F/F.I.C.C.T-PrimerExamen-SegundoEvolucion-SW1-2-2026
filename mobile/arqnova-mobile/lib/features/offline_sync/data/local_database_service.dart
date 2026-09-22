import 'dart:convert';

import 'package:path/path.dart' as path;
import 'package:sqflite/sqflite.dart';

import '../../local_assistant/domain/assistant_command.dart';
import '../domain/pending_operation.dart';

abstract interface class PendingOperationStore {
  Future<List<PendingOperation>> getOperations();
  Future<void> saveOperation(PendingOperation operation);
}

class LocalDatabaseService implements PendingOperationStore {
  static const _databaseName = 'arqnova_mobile.db';
  static const _table = 'pending_operations';
  Database? _database;

  Future<Database> get _db async {
    if (_database != null) return _database!;
    final databasePath = path.join(await getDatabasesPath(), _databaseName);
    _database = await openDatabase(
      databasePath,
      version: 2,
      onCreate:
          (db, _) => db.execute('''
        CREATE TABLE $_table (
          id_local TEXT PRIMARY KEY,
          entidad TEXT NOT NULL,
          accion TEXT NOT NULL,
          datos TEXT NOT NULL,
          fecha_hora TEXT NOT NULL,
          estado TEXT NOT NULL,
          comando TEXT NOT NULL,
          respuesta_servidor TEXT,
          error TEXT,
          intentos INTEGER NOT NULL DEFAULT 0,
          ultimo_intento TEXT,
          fecha_sincronizacion TEXT
        )
      '''),
      onUpgrade: (db, oldVersion, _) async {
        if (oldVersion < 2) {
          await db.execute('ALTER TABLE $_table ADD COLUMN intentos INTEGER NOT NULL DEFAULT 0');
          await db.execute('ALTER TABLE $_table ADD COLUMN ultimo_intento TEXT');
          await db.execute('ALTER TABLE $_table ADD COLUMN fecha_sincronizacion TEXT');
        }
      },
    );
    return _database!;
  }

  @override
  Future<List<PendingOperation>> getOperations() async {
    final rows = await (await _db).query(_table, orderBy: 'fecha_hora DESC');
    return rows.map(_fromRow).toList();
  }

  @override
  Future<void> saveOperation(PendingOperation operation) async {
    await (await _db).insert(
      _table,
      _toRow(operation),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Map<String, Object?> _toRow(PendingOperation operation) => {
    'id_local': operation.localId,
    'entidad': operation.entity,
    'accion': operation.action,
    'datos': jsonEncode(operation.data),
    'fecha_hora': operation.createdAt.toIso8601String(),
    'estado': operation.status.name.toUpperCase(),
    'comando': jsonEncode(operation.command.toStoredJson()),
    'respuesta_servidor':
        operation.serverData == null ? null : jsonEncode(operation.serverData),
    'error': operation.errorMessage,
    'intentos': operation.attemptCount,
    'ultimo_intento': operation.lastAttemptAt?.toIso8601String(),
    'fecha_sincronizacion': operation.synchronizedAt?.toIso8601String(),
  };

  PendingOperation _fromRow(Map<String, Object?> row) => PendingOperation(
    localId: row['id_local']! as String,
    entity: row['entidad']! as String,
    action: row['accion']! as String,
    data: Map<String, dynamic>.from(jsonDecode(row['datos']! as String) as Map),
    createdAt: DateTime.parse(row['fecha_hora']! as String),
    status: PendingOperationStatus.values.byName(
      (row['estado']! as String).toLowerCase(),
    ),
    command: AssistantCommand.fromStoredJson(
      Map<String, dynamic>.from(jsonDecode(row['comando']! as String) as Map),
    ),
    serverData:
        row['respuesta_servidor'] == null
            ? null
            : jsonDecode(row['respuesta_servidor']! as String),
    errorMessage: row['error'] as String?,
    attemptCount: (row['intentos'] as int?) ?? 0,
    lastAttemptAt: row['ultimo_intento'] == null
        ? null
        : DateTime.parse(row['ultimo_intento']! as String),
    synchronizedAt: row['fecha_sincronizacion'] == null
        ? null
        : DateTime.parse(row['fecha_sincronizacion']! as String),
  );
}
