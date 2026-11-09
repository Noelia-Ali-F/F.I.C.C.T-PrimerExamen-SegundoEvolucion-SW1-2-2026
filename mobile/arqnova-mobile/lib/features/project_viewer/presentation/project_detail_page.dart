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
  List<String> warnings = [];
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
      warnings = [];
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
      warnings = [];
      activityCursor = aiCursor = null;
    });
    if (error.status == 401) {
      widget.onSessionExpired?.call();
    } else {
      widget.onProjectUnavailable?.call();
    }
    return true;
  }

  Future<void> loadHistory({bool older = false}) async {
    if (loading || accessDenied) return;
    final request = generation;
    setState(() {
      loading = true;
      historyError = null;
    });
    try {
      final page = await widget.useCases.timeline(
        widget.session,
        widget.project.id,
        activityCursor: older ? activityCursor : null,
        aiCursor: older ? aiCursor : null,
        loadActivity: !older || activityCursor != null,
        loadAi: !older || aiCursor != null,
      );
      if (!accepts(request)) return;
      setState(() {
        entries = ProjectViewerUseCases.merge(
          older ? entries : [],
          page.entries,
        );
        activityCursor = page.activityCursor;
        aiCursor = page.aiCursor;
        warnings = page.warnings;
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
  @override
  Widget build(BuildContext context) => DefaultTabController(
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
                          key: ValueKey((widget.project.id, current!.version)),
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
                for (final warning in warnings)
                  Padding(
                    padding: const EdgeInsets.only(bottom: 12),
                    child: Text(warning),
                  ),
                if (!loading && historyError == null && entries.isEmpty)
                  const Padding(
                    padding: EdgeInsets.all(24),
                    child: Text('Todavía no hay actividad registrada.'),
                  ),
                for (final entry in entries)
                  Card(
                    child: ListTile(
                      leading: Icon(
                        entry.ai ? Icons.auto_awesome : Icons.person_outline,
                      ),
                      title: Text(
                        '${entry.author} · ${entry.ai ? entry.kind : 'Actividad'}',
                      ),
                      subtitle: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(entry.at.toLocal().toString().substring(0, 16)),
                          const SizedBox(height: 8),
                          SelectableText(entry.message),
                        ],
                      ),
                    ),
                  ),
                if (loading)
                  const Padding(
                    padding: EdgeInsets.all(20),
                    child: Center(child: CircularProgressIndicator()),
                  ),
                if (!loading && (activityCursor != null || aiCursor != null))
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
