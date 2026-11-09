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
          headerHeight(c) +
          10 +
          (attributes.isEmpty
              ? 18
              : attributes
                  .map((a) => rowHeight(attributeText(a)))
                  .reduce((a, b) => a + b)) +
          (methods.isEmpty
              ? 18
              : methods
                  .map((m) => rowHeight(methodText(m)))
                  .reduce((a, b) => a + b));
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
          ? math.max(0.0, value.toDouble())
          : fallback;
  Size get canvasSize => Size(
    boxes.values.fold<double>(350, (v, r) => math.max(v, r.right + 140)),
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
      final reflexive = raw['sourceId'] == raw['targetId'];
      final start =
          reflexive
              ? Offset(from.right, from.top + 31)
              : anchor(from, to.center);
      final end =
          reflexive ? Offset(to.right, to.top + 65) : anchor(to, from.center);
      final direction = end - start;
      if (direction.distance < 1) continue;
      final unit = direction / direction.distance;
      final startUnit = reflexive ? const Offset(1, 0) : unit;
      final endUnit = reflexive ? const Offset(-1, 0) : unit;
      final kind = raw['type'];
      final middle =
          reflexive
              ? Offset(from.right + 63, (start.dy + end.dy) / 2)
              : (start + end) / 2;
      if (reflexive) {
        final loop = reflexivePath(from);
        if (kind == 'dependency') {
          final metric = loop.computeMetrics().single;
          for (var length = 0.0; length < metric.length; length += 12) {
            canvas.drawPath(
              metric.extractPath(length, math.min(length + 6, metric.length)),
              ink,
            );
          }
        } else {
          canvas.drawPath(loop, ink);
        }
      } else if (kind == 'dependency') {
        dashed(canvas, start, end);
      } else {
        canvas.drawLine(start, end, ink);
      }
      if (kind == 'composition' || kind == 'aggregation') {
        final normal = Offset(-startUnit.dy, startUnit.dx);
        final path =
            Path()
              ..moveTo(start.dx, start.dy)
              ..lineTo(
                (start + startUnit * 9 + normal * 5).dx,
                (start + startUnit * 9 + normal * 5).dy,
              )
              ..lineTo((start + startUnit * 18).dx, (start + startUnit * 18).dy)
              ..lineTo(
                (start + startUnit * 9 - normal * 5).dx,
                (start + startUnit * 9 - normal * 5).dy,
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
        final normal = Offset(-endUnit.dy, endUnit.dx),
            tail = end - endUnit * 15;
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
        start + startUnit * 24 + const Offset(0, -17),
      );
      label(
        canvas,
        raw['targetMultiplicity']?.toString() ?? '',
        end - endUnit * 24 + Offset(0, reflexive ? 4 : -17),
      );
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
        Rect.fromLTWH(rect.left, rect.top, rect.width, headerHeight(c)),
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
      var y = rect.top + headerHeight(c);
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
        y += rowHeight(attributeText(attr));
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
        y += rowHeight(methodText(method));
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
  static Path reflexivePath(Rect box) {
    final start = Offset(box.right, box.top + 31);
    final end = Offset(box.right, box.top + 65);
    return Path()
      ..moveTo(start.dx, start.dy)
      ..cubicTo(
        start.dx + 84,
        start.dy - 18,
        end.dx + 84,
        end.dy + 18,
        end.dx,
        end.dy,
      );
  }

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

  double headerHeight(Map<String, dynamic> item) {
    final name = _layout(item['name']?.toString() ?? '', 254, 12, true);
    final height = math.max(50.0, 30 + name.height);
    name.dispose();
    return height;
  }

  double rowHeight(String value) {
    final row = _layout(value, 254, 11, false);
    final height = math.max(18.0, row.height + 5);
    row.dispose();
    return height;
  }

  TextPainter _layout(String value, double width, double size, bool bold) =>
      TextPainter(
        text: TextSpan(
          text: value,
          style: TextStyle(
            color: const Color(0xff172554),
            fontSize: size,
            fontWeight: bold ? FontWeight.bold : FontWeight.normal,
          ),
        ),
        textDirection: TextDirection.ltr,
      )..layout(maxWidth: width);

  void text(
    Canvas canvas,
    String value,
    Offset at,
    double width, {
    double size = 12,
    bool bold = false,
  }) {
    final painter = _layout(value, width, size, bold);
    painter.paint(canvas, at);
    painter.dispose();
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
    p.dispose();
  }

  @override
  bool shouldRepaint(covariant UmlViewerPainter oldDelegate) =>
      oldDelegate.model != model;
}
