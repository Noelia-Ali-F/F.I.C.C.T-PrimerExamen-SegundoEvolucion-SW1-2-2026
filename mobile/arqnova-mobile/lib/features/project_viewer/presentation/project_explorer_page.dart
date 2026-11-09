import 'package:flutter/material.dart';
import 'dart:async';
import '../../project_notifications/domain/notification_ports.dart';
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
  bool notificationsEnabled = false, notificationsBusy = false;
  final notificationSubscriptions = <StreamSubscription>[];
  ProjectNotification? pendingNotification;
  MaterialPageRoute<void>? activeDetail;
  Future<void>? stoppingNotifications;
  @override
  void initState() {
    super.initState();
    final notifications = widget.useCases.notifications;
    if (notifications != null) {
      notificationSubscriptions.add(
        notifications.messages.listen((message) {
          if (!mounted || session == null) return;
          ScaffoldMessenger.of(context).showSnackBar(
            SnackBar(
              content: Text('${message.title}: ${message.body}'),
              action: SnackBarAction(
                label: 'Ver',
                onPressed: () => openNotification(message),
              ),
            ),
          );
        }),
      );
      notificationSubscriptions.add(
        notifications.opened.listen(openNotification),
      );
    }
  }

  Future<void> openNotification(ProjectNotification message) async {
    final actor = session;
    if (actor == null) {
      pendingNotification = message;
      return;
    }
    widget.useCases.notifications?.takePending();
    try {
      final values = await widget.useCases.projects(actor);
      if (!mounted || session != actor) return;
      for (final project in values) {
        if (project.id == message.projectId) {
          await openProject(actor, project);
          return;
        }
      }
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('Este proyecto ya no está disponible para tu cuenta.'),
        ),
      );
    } catch (failure) {
      if (failure is ViewerException && failure.status == 401) {
        endSession(actor, expired: true);
        return;
      }
      if (mounted && session == actor) {
        ScaffoldMessenger.of(context).showSnackBar(
          const SnackBar(
            content: Text('No se pudo abrir el proyecto de la notificación.'),
          ),
        );
      }
    }
  }

  Future<void> bindNotifications(ViewerSession actor) async {
    final notifications = widget.useCases.notifications;
    if (notifications == null) return;
    await stoppingNotifications;
    if (!mounted || session != actor) return;
    try {
      final enabled = await notifications.bind(actor.token);
      if (mounted && session == actor) {
        setState(() => notificationsEnabled = enabled);
      }
    } catch (_) {
      /* Reading projects remains available when push is not configured. */
    }
    if (!mounted || session != actor) return;
    final pending = pendingNotification ?? notifications.takePending();
    pendingNotification = null;
    if (mounted && session == actor && pending != null) {
      await openNotification(pending);
    }
  }

  Future<void> toggleNotifications() async {
    final actor = session;
    if (notificationsBusy || actor == null) return;
    final notifications = widget.useCases.notifications!;
    setState(() => notificationsBusy = true);
    try {
      if (notificationsEnabled) {
        await notifications.disable();
      } else {
        await notifications.enable(actor.token);
      }
      if (mounted && session == actor) {
        setState(() => notificationsEnabled = notifications.enabled);
      }
    } catch (e) {
      if (mounted && session == actor) {
        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text(
              e is NotificationException
                  ? e.message
                  : 'No se pudieron actualizar las notificaciones.',
            ),
          ),
        );
      }
    } finally {
      if (mounted && session == actor) {
        setState(() => notificationsBusy = false);
      }
    }
  }

  Future<void> openProject(ViewerSession actor, ViewerProject project) async {
    if (!mounted || session != actor) return;
    final navigator = Navigator.of(context);
    final previous = activeDetail;
    if (previous != null && previous.isActive) navigator.removeRoute(previous);
    final route = MaterialPageRoute<void>(
      builder:
          (_) => ProjectDetailPage(
            useCases: widget.useCases,
            session: actor,
            project: project,
            onSessionExpired: () => endSession(actor, expired: true),
            onProjectUnavailable: () {
              if (mounted && session == actor) unawaited(reload());
            },
          ),
    );
    activeDetail = route;
    await navigator.push(route);
    if (identical(activeDetail, route)) activeDetail = null;
  }

  void endSession(ViewerSession actor, {bool expired = false}) {
    if (!mounted || session != actor) return;
    final detail = activeDetail;
    activeDetail = null;
    setState(() {
      session = null;
      projects = null;
      pendingNotification = null;
      notificationsEnabled = false;
      notificationsBusy = false;
      password.clear();
      error = expired ? 'La sesión venció. Vuelve a iniciar sesión.' : null;
    });
    if (detail != null && detail.isActive) {
      Navigator.of(context).removeRoute(detail);
    }
    // Clearing the session does not depend on the network response.
    stoppingNotifications = disableNotifications();
  }

  Future<void> disableNotifications() async {
    try {
      await widget.useCases.notifications?.disable();
    } catch (_) {
      // The authenticated viewer has already been cleared.
    }
  }

  void logout() {
    final actor = session;
    if (actor != null) endSession(actor);
  }

  Future<List<ViewerProject>> loadProjects(ViewerSession actor) async {
    try {
      final values = await widget.useCases.projects(actor);
      return mounted && session == actor ? values : [];
    } on ViewerException catch (failure) {
      if (failure.status != 401) rethrow;
      endSession(actor, expired: true);
      return [];
    }
  }

  @override
  void dispose() {
    email.dispose();
    password.dispose();
    for (final subscription in notificationSubscriptions) {
      subscription.cancel();
    }
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
        projects = loadProjects(value);
      });
      unawaited(bindNotifications(value));
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
    final actor = session;
    if (actor == null) return;
    final future = loadProjects(actor);
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
            onPressed: logout,
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
                      if (widget.useCases.notifications != null)
                        OutlinedButton.icon(
                          onPressed:
                              notificationsBusy ? null : toggleNotifications,
                          icon: Icon(
                            notificationsEnabled
                                ? Icons.notifications_active_outlined
                                : Icons.notifications_none,
                          ),
                          label: Text(
                            notificationsBusy
                                ? 'Actualizando…'
                                : notificationsEnabled
                                ? 'Desactivar notificaciones'
                                : 'Activar notificaciones',
                          ),
                        ),
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
                            onTap: () {
                              final actor = session;
                              if (actor != null) openProject(actor, project);
                            },
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
