import 'package:flutter/material.dart';
import '../application/project_viewer_use_cases.dart';
import '../domain/project_models.dart';
import 'read_only_diagram.dart';

class ProjectDetailPage extends StatefulWidget {
  const ProjectDetailPage({
    super.key,
    required this.useCases,
    required this.session,
    required this.project,
    this.onSessionExpired,
    this.onProjectUnavailable,
  });
  final ProjectViewerUseCases useCases;
  final ViewerSession session;
  final ViewerProject project;
  final VoidCallback? onSessionExpired;
  final VoidCallback? onProjectUnavailable;
  @override
  State<ProjectDetailPage> createState() => _ProjectDetailPageState();
}

class _ProjectDetailPageState extends State<ProjectDetailPage> {
  ViewerDiagram? current;
  int generation = 0;
  bool diagramLoading = true, detailsOpen = false, accessDenied = false;
  String? diagramError;
  List<TimelineEntry> entries = [];
  Map<TimelineSource, String> failures = {};
  String? activityCursor, aiCursor, historyError;
  bool loading = false;
  @override
  void initState() {
    super.initState();
    refresh();
  }

  @override
  void didUpdateWidget(covariant ProjectDetailPage oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.session != widget.session ||
        oldWidget.project.id != widget.project.id ||
        oldWidget.useCases != widget.useCases) {
      refresh();
    }
  }

  bool accepts(int request) => mounted && request == generation;

  void refresh() {
    final request = ++generation;
    setState(() {
      current = null;
      detailsOpen = false;
      accessDenied = false;
      diagramLoading = true;
      diagramError = null;
      entries = [];
      failures = {};
      activityCursor = aiCursor = historyError = null;
      loading = false;
    });
    loadDiagram(request);
    loadHistory();
  }

  Future<void> loadDiagram(int request) async {
    try {
      final value = await widget.useCases.diagram(
        widget.session,
        widget.project.id,
      );
      if (!accepts(request)) return;
      setState(() {
        current = value;
        diagramLoading = false;
      });
    } catch (error) {
      if (!accepts(request) || invalidateAccess(error)) return;
      setState(() {
        diagramLoading = false;
        diagramError =
            error is ViewerException
                ? error.message
                : 'No se pudo cargar el diagrama.';
      });
    }
  }

  bool invalidateAccess(Object error) {
    if (error is! ViewerException ||
        !const {401, 403, 404}.contains(error.status)) {
      return false;
    }
    ++generation;
    setState(() {
      current = null;
      detailsOpen = false;
      accessDenied = true;
      diagramLoading = loading = false;
      diagramError = historyError = error.message;
      entries = [];
      failures = {};
      activityCursor = aiCursor = null;
    });
    if (error.status == 401) {
      widget.onSessionExpired?.call();
    } else {
      widget.onProjectUnavailable?.call();
    }
    return true;
  }

  Future<void> loadHistory({bool older = false, TimelineSource? retry}) async {
    if (loading || accessDenied) return;
    final request = generation;
    final paginate = older || retry != null;
    final readActivity =
        retry == TimelineSource.activity ||
        (retry == null &&
            (!older ||
                activityCursor != null &&
                    !failures.containsKey(TimelineSource.activity)));
    final readAi =
        retry == TimelineSource.conversation ||
        (retry == null &&
            (!older ||
                aiCursor != null &&
                    !failures.containsKey(TimelineSource.conversation)));
    setState(() {
      loading = true;
      historyError = null;
    });
    try {
      final page = await widget.useCases.timeline(
        widget.session,
        widget.project.id,
        activityCursor: paginate ? activityCursor : null,
        aiCursor: paginate ? aiCursor : null,
        loadActivity: readActivity,
        loadAi: readAi,
      );
      if (!accepts(request)) return;
      setState(() {
        entries = ProjectViewerUseCases.merge(
          paginate
              ? entries
              : entries
                  .where(
                    (entry) => page.failures.containsKey(
                      entry.ai
                          ? TimelineSource.conversation
                          : TimelineSource.activity,
                    ),
                  )
                  .toList(),
          page.entries,
        );
        activityCursor = page.activityCursor;
        aiCursor = page.aiCursor;
        failures = {
          if (!readActivity && failures.containsKey(TimelineSource.activity))
            TimelineSource.activity: failures[TimelineSource.activity]!,
          if (!readAi && failures.containsKey(TimelineSource.conversation))
            TimelineSource.conversation: failures[TimelineSource.conversation]!,
          ...page.failures,
        };
        loading = false;
      });
    } catch (e) {
      if (accepts(request) && !invalidateAccess(e)) {
        setState(() {
          loading = false;
          historyError =
              e is ViewerException
                  ? e.message
                  : 'No se pudo recuperar la actividad.';
        });
      }
    }
  }

  Widget elements(ViewerDiagram value) {
    final classes = (value.model['classes'] as List).whereType<Map>().toList();
    final names = {for (final c in classes) c['id']: c['name']};
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        ListTile(
          title: const Text('Clases y relaciones'),
          trailing: IconButton(
            tooltip: 'Cerrar detalles',
            icon: const Icon(Icons.close),
            onPressed:
                () => setState(() {
                  detailsOpen = false;
                }),
          ),
        ),
        for (final c in classes)
          ExpansionTile(
            title: Text(c['name']?.toString() ?? ''),
            subtitle: Text(c['stereotype']?.toString() ?? 'Entidad'),
            childrenPadding: const EdgeInsets.all(16),
            children: [
              for (final a in (c['attributes'] as List? ?? []))
                ListTile(
                  dense: true,
                  title: SelectableText(UmlViewerPainter.attributeText(a)),
                ),
              for (final m in (c['methods'] as List? ?? []))
                ListTile(
                  dense: true,
                  title: SelectableText(UmlViewerPainter.methodText(m)),
                ),
            ],
          ),
        const Divider(),
        for (final r in (value.model['relations'] as List).whereType<Map>())
          ListTile(
            title: Text(
              '${names[r['sourceId']] ?? ''} — ${names[r['targetId']] ?? ''}',
            ),
            subtitle: Text(
              '${relationName(r['type'])} · ${r['sourceMultiplicity'] ?? ''} a ${r['targetMultiplicity'] ?? ''}${r['associationClassId'] == null ? '' : '\nClase de asociación: ${names[r['associationClassId']] ?? ''}'}\n${r['label'] ?? ''}',
            ),
          ),
      ],
    );
  }

  static String relationName(dynamic type) => switch (type) {
    'composition' => 'Composición',
    'aggregation' => 'Agregación',
    'inheritance' => 'Herencia',
    'dependency' => 'Dependencia',
    _ => 'Asociación',
  };
  String sourceName(TimelineSource source) =>
      source == TimelineSource.activity ? 'Bitácora UML' : 'Conversación IA';

  Widget activityCard(TimelineEntry entry, TimelineEntry? conversation) {
    final details = [
      if (entry.elementName != null) 'Elemento: ${entry.elementName}',
      if (entry.elementType != null) 'Tipo: ${entry.elementType}',
      if (entry.elementId != null) 'ID: ${entry.elementId}',
      if (entry.confirmedAiChange) ...[
        if (entry.requesterId != null) 'Solicitante: ${entry.requesterId}',
        'Ejecutor: ${entry.executor}',
        if (entry.provider != null) 'Proveedor: ${entry.provider}',
        if (entry.requestId != null) 'Solicitud: ${entry.requestId}',
        if (entry.instruction != null) 'Instrucción: ${entry.instruction}',
      ],
    ];
    final title = Text(
      '${entry.author} · ${entry.ai
          ? entry.kind
          : entry.confirmedAiChange
          ? 'Modificación UML por IA'
          : 'Actividad'}',
    );
    final icon = Icon(
      entry.ai || entry.confirmedAiChange
          ? Icons.auto_awesome
          : Icons.person_outline,
    );
    final body = Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(entry.at.toLocal().toString().substring(0, 16)),
        const SizedBox(height: 8),
        SelectableText(entry.message),
        for (final line in details) SelectableText(line),
      ],
    );
    return Card(
      key: ValueKey(entry.key),
      child:
          entry.confirmedAiChange && conversation != null
              ? ExpansionTile(
                leading: icon,
                title: title,
                subtitle: body,
                children: [
                  ListTile(
                    title: const Text('Conversación vinculada'),
                    subtitle: SelectableText(conversation.message),
                  ),
                ],
              )
              : ListTile(leading: icon, title: title, subtitle: body),
    );
  }

  @override
  Widget build(BuildContext context) {
    final conversations = {
      for (final entry in entries)
        if (entry.ai) entry.requestId: entry,
    };
    return DefaultTabController(
      length: 2,
      child: Scaffold(
        appBar: AppBar(
          title: Text(accessDenied ? 'Proyecto UML' : widget.project.name),
          actions: [
            IconButton(
              tooltip: 'Ver clases y relaciones',
              icon: const Icon(Icons.list_alt),
              onPressed:
                  current == null
                      ? null
                      : () => setState(() {
                        detailsOpen = !detailsOpen;
                      }),
            ),
            IconButton(
              tooltip: 'Actualizar',
              icon: const Icon(Icons.refresh),
              onPressed: refresh,
            ),
          ],
          bottom: const TabBar(
            tabs: [Tab(text: 'Diagrama'), Tab(text: 'Actividad')],
          ),
        ),
        body: TabBarView(
          children: [
            Column(
              children: [
                const Padding(
                  padding: EdgeInsets.all(10),
                  child: Text('Solo lectura · Amplía y desplaza el diagrama'),
                ),
                Expanded(
                  child:
                      diagramError != null
                          ? Center(
                            child: Padding(
                              padding: const EdgeInsets.all(24),
                              child: Text(diagramError!),
                            ),
                          )
                          : diagramLoading
                          ? const Center(child: CircularProgressIndicator())
                          : current == null
                          ? const SizedBox.shrink()
                          : detailsOpen
                          ? elements(current!)
                          : ReadOnlyDiagram(
                            key: ValueKey((
                              widget.project.id,
                              current!.version,
                            )),
                            diagram: current!,
                          ),
                ),
              ],
            ),
            RefreshIndicator(
              onRefresh: () => loadHistory(),
              child: ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                padding: const EdgeInsets.all(16),
                children: [
                  if (historyError != null)
                    ListTile(
                      leading: const Icon(Icons.error_outline),
                      title: Text(historyError!),
                      trailing: IconButton(
                        icon: const Icon(Icons.refresh),
                        onPressed: () => loadHistory(),
                      ),
                    ),
                  for (final failure in failures.entries)
                    ListTile(
                      title: Text(
                        '${sourceName(failure.key)}: ${failure.value}',
                      ),
                      trailing: TextButton(
                        onPressed:
                            loading
                                ? null
                                : () => loadHistory(retry: failure.key),
                        child: const Text('Reintentar'),
                      ),
                    ),
                  if (!loading &&
                      historyError == null &&
                      failures.isEmpty &&
                      entries.isEmpty)
                    const Padding(
                      padding: EdgeInsets.all(24),
                      child: Text('Todavía no hay actividad registrada.'),
                    ),
                  for (final entry in entries)
                    activityCard(entry, conversations[entry.requestId]),
                  if (loading)
                    const Padding(
                      padding: EdgeInsets.all(20),
                      child: Center(child: CircularProgressIndicator()),
                    ),
                  if (!loading &&
                      (activityCursor != null &&
                              !failures.containsKey(TimelineSource.activity) ||
                          aiCursor != null &&
                              !failures.containsKey(
                                TimelineSource.conversation,
                              )))
                    OutlinedButton(
                      onPressed: () => loadHistory(older: true),
                      child: const Text('Ver actividad anterior'),
                    ),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }
}
