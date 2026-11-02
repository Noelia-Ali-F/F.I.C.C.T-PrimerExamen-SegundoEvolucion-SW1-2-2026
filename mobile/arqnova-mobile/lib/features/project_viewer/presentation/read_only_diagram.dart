import 'dart:math' as math;
import 'package:flutter/material.dart';
import '../domain/project_models.dart';

class ReadOnlyDiagram extends StatefulWidget {
  const ReadOnlyDiagram({super.key, required this.diagram});
  final ViewerDiagram diagram;
  @override
  State<ReadOnlyDiagram> createState() => _ReadOnlyDiagramState();
}

class _ReadOnlyDiagramState extends State<ReadOnlyDiagram> {
  final transform = TransformationController();
  bool fitted = false;
  @override
  void dispose() {
    transform.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final painter = UmlViewerPainter(widget.diagram.model);
    if (painter.classes.isEmpty) {
      return const Center(child: Text('El diagrama está vacío.'));
    }
    return LayoutBuilder(
      builder: (context, box) {
        if (!fitted && box.maxWidth.isFinite && box.maxHeight.isFinite) {
          final scale = math.min(
            1.0,
            math.min(
              box.maxWidth / painter.canvasSize.width,
              box.maxHeight / painter.canvasSize.height,
            ),
          );
          transform.value = Matrix4.identity()..scale(scale);
          fitted = true;
        }
        return Semantics(
          label:
              'Diagrama de clases de solo lectura. Amplía y desplaza para consultar sus elementos.',
          child: InteractiveViewer(
            transformationController: transform,
            constrained: false,
            boundaryMargin: const EdgeInsets.all(120),
            minScale: .02,
            maxScale: 4,
            child: SizedBox(
              width: painter.canvasSize.width,
              height: painter.canvasSize.height,
              child: CustomPaint(painter: painter),
            ),
          ),
        );
      },
    );
  }
}

class UmlViewerPainter extends CustomPainter {
  UmlViewerPainter(this.model) {
    for (final raw in (model['classes'] as List? ?? [])) {
      if (raw is! Map) continue;
      final c = Map<String, dynamic>.from(raw);
      final id = c['id']?.toString();
      if (id == null) continue;
      classes[id] = c;
      final attributes = c['attributes'] as List? ?? [];
      final methods = c['methods'] as List? ?? [];
      final height =
          66 +
          math.max(1, attributes.length) * 18 +
          math.max(1, methods.length) * 18;
      boxes[id] = Rect.fromLTWH(
        number(c['x'], 30),
        number(c['y'], 30),
        270,
        height.toDouble(),
      );
    }
  }
  final Map<String, dynamic> model;
  final classes = <String, Map<String, dynamic>>{};
  final boxes = <String, Rect>{};
  static double number(dynamic value, double fallback) =>
      value is num && value.toDouble().isFinite
          ? value.toDouble().clamp(0, 20000)
          : fallback;
  Size get canvasSize => Size(
    boxes.values.fold<double>(350, (v, r) => math.max(v, r.right + 40)),
    boxes.values.fold<double>(300, (v, r) => math.max(v, r.bottom + 40)),
  );
  final ink =
      Paint()
        ..color = const Color(0xff475569)
        ..strokeWidth = 1.6
        ..style = PaintingStyle.stroke;

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawColor(const Color(0xfff8fafc), BlendMode.src);
    for (final raw in (model['relations'] as List? ?? [])) {
      if (raw is! Map) continue;
      final from = boxes[raw['sourceId']], to = boxes[raw['targetId']];
      if (from == null || to == null) continue;
      final start = anchor(from, to.center), end = anchor(to, from.center);
      final direction = end - start;
      if (direction.distance < 1) continue;
      final unit = direction / direction.distance;
      final kind = raw['type'];
      if (kind == 'dependency') {
        dashed(canvas, start, end);
      } else {
        canvas.drawLine(start, end, ink);
      }
      if (kind == 'composition' || kind == 'aggregation') {
        final normal = Offset(-unit.dy, unit.dx);
        final path =
            Path()
              ..moveTo(start.dx, start.dy)
              ..lineTo(
                (start + unit * 9 + normal * 5).dx,
                (start + unit * 9 + normal * 5).dy,
              )
              ..lineTo((start + unit * 18).dx, (start + unit * 18).dy)
              ..lineTo(
                (start + unit * 9 - normal * 5).dx,
                (start + unit * 9 - normal * 5).dy,
              )
              ..close();
        canvas.drawPath(
          path,
          Paint()
            ..color =
                kind == 'composition' ? const Color(0xff475569) : Colors.white,
        );
        canvas.drawPath(path, ink);
      }
      if (kind == 'inheritance' || kind == 'dependency') {
        final normal = Offset(-unit.dy, unit.dx), tail = end - unit * 15;
        final path =
            Path()
              ..moveTo((tail + normal * 7).dx, (tail + normal * 7).dy)
              ..lineTo(end.dx, end.dy)
              ..lineTo((tail - normal * 7).dx, (tail - normal * 7).dy);
        if (kind == 'inheritance') {
          path.close();
          canvas.drawPath(path, Paint()..color = Colors.white);
        }
        canvas.drawPath(path, ink);
      }
      label(
        canvas,
        raw['sourceMultiplicity']?.toString() ?? '',
        start + unit * 24 + const Offset(0, -17),
      );
      label(
        canvas,
        raw['targetMultiplicity']?.toString() ?? '',
        end - unit * 36 + const Offset(0, -17),
      );
      final middle = (start + end) / 2;
      label(
        canvas,
        raw['label']?.toString() ?? '',
        middle + const Offset(0, -19),
      );
      final association = boxes[raw['associationClassId']];
      if (association != null) {
        dashed(canvas, middle, anchor(association, middle));
      }
    }
    for (final entry in classes.entries) {
      final c = entry.value, rect = boxes[entry.key]!;
      final outline = RRect.fromRectAndRadius(rect, const Radius.circular(4));
      canvas.drawRRect(outline, Paint()..color = Colors.white);
      final color = Color(
        0xff000000 |
            (int.tryParse(
                  (c['color']?.toString() ?? '#2563eb').replaceFirst('#', ''),
                  radix: 16,
                ) ??
                0x2563eb),
      );
      canvas.drawRect(
        Rect.fromLTWH(rect.left, rect.top, rect.width, 50),
        Paint()..color = color.withValues(alpha: .12),
      );
      canvas.drawRRect(outline, ink);
      canvas.drawLine(
        rect.topLeft,
        rect.topRight,
        Paint()
          ..color = color
          ..strokeWidth = 3,
      );
      text(
        canvas,
        '«${c['stereotype'] ?? 'Entidad'}»',
        rect.topLeft + const Offset(8, 8),
        254,
        size: 10,
      );
      text(
        canvas,
        c['name']?.toString() ?? '',
        rect.topLeft + const Offset(8, 24),
        254,
        bold: true,
      );
      var y = rect.top + 50;
      canvas.drawLine(Offset(rect.left, y), Offset(rect.right, y), ink);
      final attrs = c['attributes'] as List? ?? [];
      if (attrs.isEmpty) {
        text(
          canvas,
          'Sin atributos',
          Offset(rect.left + 8, y + 5),
          254,
          size: 11,
        );
        y += 18;
      }
      for (final attr in attrs) {
        text(
          canvas,
          attributeText(attr),
          Offset(rect.left + 8, y + 5),
          254,
          size: 11,
        );
        y += 18;
      }
      y += 5;
      canvas.drawLine(Offset(rect.left, y), Offset(rect.right, y), ink);
      final methods = c['methods'] as List? ?? [];
      if (methods.isEmpty) {
        text(
          canvas,
          'Sin métodos',
          Offset(rect.left + 8, y + 5),
          254,
          size: 11,
        );
      }
      for (final method in methods) {
        text(
          canvas,
          methodText(method),
          Offset(rect.left + 8, y + 5),
          254,
          size: 11,
        );
        y += 18;
      }
    }
  }

  static String attributeText(dynamic value) =>
      value is Map
          ? '${value['isPrimaryKey'] == true ? 'PK ' : ''}${value['name'] ?? ''}: ${value['type'] ?? ''}'
          : value.toString();
  static String methodText(dynamic value) =>
      value is Map
          ? '${value['name'] ?? ''}(${value['parameters'] ?? ''}): ${value['returnType'] ?? ''}'
          : value.toString();
  Offset anchor(Rect box, Offset toward) {
    final delta = toward - box.center;
    if (delta.distance < .1) return box.centerRight;
    final ratio = math.max(
      delta.dx.abs() / (box.width / 2),
      delta.dy.abs() / (box.height / 2),
    );
    return box.center + delta / ratio;
  }

  void dashed(Canvas canvas, Offset start, Offset end) {
    final delta = end - start, length = (end - start).distance;
    if (length < 1) return;
    final unit = delta / length;
    for (double n = 0; n < length; n += 12) {
      canvas.drawLine(
        start + unit * n,
        start + unit * math.min(n + 6, length),
        ink,
      );
    }
  }

  void text(
    Canvas canvas,
    String value,
    Offset at,
    double width, {
    double size = 12,
    bool bold = false,
  }) {
    final p = TextPainter(
      text: TextSpan(
        text: value,
        style: TextStyle(
          color: const Color(0xff172554),
          fontSize: size,
          fontWeight: bold ? FontWeight.bold : FontWeight.normal,
        ),
      ),
      textDirection: TextDirection.ltr,
      maxLines: 1,
      ellipsis: '…',
    )..layout(maxWidth: width);
    p.paint(canvas, at);
  }

  void label(Canvas canvas, String value, Offset at) {
    if (value.isEmpty) return;
    final p = TextPainter(
      text: TextSpan(
        text: value,
        style: const TextStyle(color: Color(0xff172554), fontSize: 12),
      ),
      textDirection: TextDirection.ltr,
    )..layout(maxWidth: 180);
    canvas.drawRect(at & p.size, Paint()..color = Colors.white);
    p.paint(canvas, at);
  }

  @override
  bool shouldRepaint(covariant UmlViewerPainter oldDelegate) =>
      oldDelegate.model != model;
}
