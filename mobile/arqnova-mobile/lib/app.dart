import 'package:flutter/material.dart';

import 'core/theme/app_theme.dart';
import 'pages/home_page.dart';

void runArqNovaMobileApp() {
  WidgetsFlutterBinding.ensureInitialized();
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
