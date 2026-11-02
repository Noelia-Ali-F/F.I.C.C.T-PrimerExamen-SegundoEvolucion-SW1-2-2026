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
  });
  final ProjectViewerUseCases useCases;
  final ViewerSession session;
  final ViewerProject project;
  @override
  State<ProjectDetailPage> createState() => _ProjectDetailPageState();
}

class _ProjectDetailPageState extends State<ProjectDetailPage> {
  late Future<ViewerDiagram> diagram;
  ViewerDiagram? current;
  List<TimelineEntry> entries = [];
  List<String> warnings = [];
  String? activityCursor, aiCursor, historyError;
  bool loading = false;
  @override
  void initState() {
    super.initState();
    diagram = loadDiagram();
    loadHistory();
  }

  Future<ViewerDiagram> loadDiagram() async {
    final value = await widget.useCases.diagram(
      widget.session,
      widget.project.id,
    );
    current = value;
    return value;
  }

  Future<void> loadHistory({bool older = false}) async {
    if (loading) return;
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
      if (!mounted) return;
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
      if (mounted) {
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

  void showElements() {
    final value = current;
    if (value == null) return;
    final classes = (value.model['classes'] as List).whereType<Map>().toList();
    final names = {for (final c in classes) c['id']: c['name']};
    showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      builder:
          (context) => SafeArea(
            child: SizedBox(
              height: MediaQuery.sizeOf(context).height * .8,
              child: ListView(
                padding: const EdgeInsets.all(16),
                children: [
                  Text(
                    'Clases y relaciones',
                    style: Theme.of(context).textTheme.titleLarge,
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
                            title: SelectableText(
                              UmlViewerPainter.attributeText(a),
                            ),
                          ),
                        for (final m in (c['methods'] as List? ?? []))
                          ListTile(
                            dense: true,
                            title: SelectableText(
                              UmlViewerPainter.methodText(m),
                            ),
                          ),
                      ],
                    ),
                  const Divider(),
                  for (final r
                      in (value.model['relations'] as List).whereType<Map>())
                    ListTile(
                      title: Text(
                        '${names[r['sourceId']] ?? ''} — ${names[r['targetId']] ?? ''}',
                      ),
                      subtitle: Text(
                        '${relationName(r['type'])} · ${r['sourceMultiplicity'] ?? ''} a ${r['targetMultiplicity'] ?? ''}${r['associationClassId'] == null ? '' : '\nClase de asociación: ${names[r['associationClassId']] ?? ''}'}\n${r['label'] ?? ''}',
                      ),
                    ),
                ],
              ),
            ),
          ),
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
        title: Text(widget.project.name),
        actions: [
          IconButton(
            tooltip: 'Ver clases y relaciones',
            icon: const Icon(Icons.list_alt),
            onPressed: showElements,
          ),
          IconButton(
            tooltip: 'Actualizar',
            icon: const Icon(Icons.refresh),
            onPressed: () {
              setState(() => diagram = loadDiagram());
              loadHistory();
            },
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
                child: FutureBuilder<ViewerDiagram>(
                  future: diagram,
                  builder: (context, snapshot) {
                    if (snapshot.hasError) {
                      return Center(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Text(snapshot.error.toString()),
                        ),
                      );
                    }
                    if (!snapshot.hasData) {
                      return const Center(child: CircularProgressIndicator());
                    }
                    return ReadOnlyDiagram(
                      key: ValueKey(snapshot.data!.version),
                      diagram: snapshot.data!,
                    );
                  },
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
