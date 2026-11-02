import 'package:flutter/material.dart';
import '../application/project_viewer_use_cases.dart';
import '../domain/project_models.dart';
import 'project_detail_page.dart';

class ProjectExplorerPage extends StatefulWidget {
  const ProjectExplorerPage({super.key, required this.useCases});
  final ProjectViewerUseCases useCases;
  @override
  State<ProjectExplorerPage> createState() => _ProjectExplorerPageState();
}

class _ProjectExplorerPageState extends State<ProjectExplorerPage> {
  final email = TextEditingController(), password = TextEditingController();
  ViewerSession? session;
  Future<List<ViewerProject>>? projects;
  bool busy = false;
  String? error;
  @override
  void dispose() {
    email.dispose();
    password.dispose();
    widget.useCases.dispose();
    super.dispose();
  }

  Future<void> login() async {
    setState(() {
      busy = true;
      error = null;
    });
    try {
      final value = await widget.useCases.login(email.text, password.text);
      if (!mounted) return;
      password.clear();
      setState(() {
        session = value;
        busy = false;
        projects = widget.useCases.projects(value);
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          busy = false;
          error =
              e is ViewerException ? e.message : 'No se pudo iniciar sesión.';
        });
      }
    }
  }

  Future<void> reload() async {
    final future = widget.useCases.projects(session!);
    setState(() => projects = future);
    try {
      await future;
    } catch (_) {
      /* FutureBuilder displays a retry action. */
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
    appBar: AppBar(
      title: const Text('Mis proyectos UML'),
      actions: [
        if (session != null)
          IconButton(
            tooltip: 'Cerrar sesión',
            icon: const Icon(Icons.logout),
            onPressed:
                () => setState(() {
                  session = null;
                  projects = null;
                }),
          ),
      ],
    ),
    body:
        session == null
            ? Center(
              child: SingleChildScrollView(
                padding: const EdgeInsets.all(24),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 440),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.stretch,
                    children: [
                      const Icon(Icons.account_tree_outlined, size: 54),
                      const SizedBox(height: 18),
                      Text(
                        'Consulta tus diagramas',
                        style: Theme.of(context).textTheme.headlineSmall,
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 12),
                      const Text(
                        'Ingresa con la misma cuenta del sistema web. Los proyectos se abren en modo de solo lectura.',
                        textAlign: TextAlign.center,
                      ),
                      const SizedBox(height: 24),
                      TextField(
                        controller: email,
                        keyboardType: TextInputType.emailAddress,
                        autofillHints: const [AutofillHints.username],
                        decoration: const InputDecoration(
                          labelText: 'Correo electrónico',
                        ),
                        enabled: !busy,
                      ),
                      const SizedBox(height: 16),
                      TextField(
                        controller: password,
                        obscureText: true,
                        autofillHints: const [AutofillHints.password],
                        decoration: const InputDecoration(
                          labelText: 'Contraseña',
                        ),
                        enabled: !busy,
                        onSubmitted: (_) {
                          if (!busy) login();
                        },
                      ),
                      if (error != null)
                        Padding(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          child: Text(
                            error!,
                            style: TextStyle(
                              color: Theme.of(context).colorScheme.error,
                            ),
                          ),
                        ),
                      const SizedBox(height: 20),
                      FilledButton(
                        onPressed: busy ? null : login,
                        child: Text(busy ? 'Ingresando…' : 'Iniciar sesión'),
                      ),
                    ],
                  ),
                ),
              ),
            )
            : FutureBuilder<List<ViewerProject>>(
              future: projects,
              builder: (context, snapshot) {
                if (snapshot.connectionState != ConnectionState.done) {
                  return const Center(child: CircularProgressIndicator());
                }
                if (snapshot.hasError) {
                  return _Message(
                    message: snapshot.error.toString(),
                    retry: reload,
                  );
                }
                final values = snapshot.data ?? [];
                return RefreshIndicator(
                  onRefresh: reload,
                  child: ListView(
                    physics: const AlwaysScrollableScrollPhysics(),
                    padding: const EdgeInsets.all(16),
                    children: [
                      Text(
                        session!.name,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                      const SizedBox(height: 12),
                      if (values.isEmpty)
                        const Padding(
                          padding: EdgeInsets.all(24),
                          child: Text(
                            'Todavía no tienes proyectos compartidos.',
                          ),
                        ),
                      for (final project in values)
                        Card(
                          child: ListTile(
                            leading: const Icon(Icons.account_tree_outlined),
                            title: Text(project.name),
                            subtitle: Text(
                              project.description.isEmpty
                                  ? 'Diagrama de clases · Solo lectura'
                                  : project.description,
                            ),
                            trailing: const Icon(Icons.chevron_right),
                            onTap:
                                () => Navigator.of(context).push(
                                  MaterialPageRoute<void>(
                                    builder:
                                        (_) => ProjectDetailPage(
                                          useCases: widget.useCases,
                                          session: session!,
                                          project: project,
                                        ),
                                  ),
                                ),
                          ),
                        ),
                    ],
                  ),
                );
              },
            ),
  );
}

class _Message extends StatelessWidget {
  const _Message({required this.message, required this.retry});
  final String message;
  final VoidCallback retry;
  @override
  Widget build(BuildContext context) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(message, textAlign: TextAlign.center),
          const SizedBox(height: 16),
          OutlinedButton(
            onPressed: retry,
            child: const Text('Volver a intentar'),
          ),
        ],
      ),
    ),
  );
}
