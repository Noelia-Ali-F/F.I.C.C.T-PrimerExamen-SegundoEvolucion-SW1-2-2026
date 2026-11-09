import 'package:flutter/material.dart';

import 'core/theme/app_theme.dart';
import 'pages/home_page.dart';
import 'core/composition/mobile_dependencies.dart';

Future<void> runArqNovaMobileApp() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    await MobileDependencies.initializeProjectNotifications().timeout(
      const Duration(seconds: 5),
    );
  } catch (_) {
    /* The existing local assistant remains available without Firebase. */
  }
  runApp(const ArqNovaMobileApp());
}

class ArqNovaMobileApp extends StatelessWidget {
  const ArqNovaMobileApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ArqNova Mobile',
      debugShowCheckedModeBanner: false,
      theme: buildAppTheme(),
      home: const HomePage(),
    );
  }
}
