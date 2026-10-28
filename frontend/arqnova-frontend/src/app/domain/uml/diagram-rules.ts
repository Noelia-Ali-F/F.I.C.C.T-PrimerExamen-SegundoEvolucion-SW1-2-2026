import {
  UmlAttribute,
  UmlClassNode,
  UmlDiagramModel,
  UmlRelation,
  UmlRelationType,
} from './uml-class-diagram.models';
import { UmlImageAnalysis } from './ai.models';
export interface CanvasLayout {
  cardWidth: number;
  canvasWidth: number;
  canvasHeight: number;
}
export type IdFactory = (prefix: string) => string;
export function normalizedName(value: string) {
  return value
    .trim()
    .normalize('NFD')
    .replace(/[\u0300-\u036f]/g, '')
    .toLowerCase();
}
export function uniqueIdAgainst(name: string, items: UmlClassNode[]) {
  const base =
    name
      .toLowerCase()
      .normalize('NFD')
      .replace(/[\u0300-\u036f]/g, '')
      .replace(/[^a-z0-9]+/g, '-')
      .replace(/^-|-$/g, '') || 'clase';
  let id = base,
    n = 2;
  while (items.some((i) => i.id === id)) id = `${base}-${n++}`;
  return id;
}
export function classHeight(item: UmlClassNode) {
  return 74 + Math.max(1, item.attributes.length) * 18 + Math.max(1, item.methods.length) * 18;
}
export function validMultiplicity(value: string) {
  return /^(\*|\d+|\d+\.\.(\d+|\*))$/.test(value.trim());
}
export function needsAssociationClass(relation: UmlRelation) {
  return (
    relation.type === 'association' &&
    relation.sourceMultiplicity.trim().endsWith('*') &&
    relation.targetMultiplicity.trim().endsWith('*')
  );
}
export function normalizeModel(value: any): UmlDiagramModel {
  if (
    value?.format !== 'uml-class-diagram' ||
    ![1, 2].includes(value?.version) ||
    !Array.isArray(value.classes) ||
    !Array.isArray(value.relations)
  )
    throw new Error('El modelo UML almacenado tiene un formato incompatible.');
  const classes = value.classes.map((item: any) => ({
    ...item,
    description: item.description ?? item.responsibilities?.[0] ?? '',
    responsibilities: Array.isArray(item.responsibilities) ? item.responsibilities : [],
    attributes: (item.attributes ?? []).map((a: any, n: number) =>
      typeof a === 'string'
        ? parseLegacyAttribute(item.id, a, n)
        : { ...a, isPrimaryKey: a.isPrimaryKey === true },
    ),
    methods: Array.isArray(item.methods) ? item.methods : [],
    color: item.color || '#2563eb',
  }));
  const classIds = new Set(classes.map((item: any) => item.id));
  const relations = value.relations.filter(
    (relation: any) => classIds.has(relation.sourceId) && classIds.has(relation.targetId),
  );
  return { ...value, version: 2, classes, relations };
}
export function parseLegacyAttribute(classId: string, value: string, index: number): UmlAttribute {
  const split = value.indexOf(':');
  const name = (split < 0 ? value : value.slice(0, split)).trim();
  return {
    id: `${classId}-attr-${index + 1}`,
    name,
    type: (split < 0 ? 'String' : value.slice(split + 1)).trim() || 'String',
    isPrimaryKey: false,
  };
}
export function findRecognizedAssociationClass(
  detected: UmlImageAnalysis['relations'][number],
  inserted: UmlClassNode[],
  relations: UmlRelation[],
) {
  const used = new Set(relations.map((item) => item.associationClassId));

  const unused = inserted.filter((item) => !used.has(item.id));

  const explicit = detected.associationClassName?.trim();

  if (explicit)
    return unused.find((item) => normalizedName(item.name) === normalizedName(explicit)) ?? null;

  return null;
}
export function createAssociationClass(
  relation: UmlRelation,
  classes: UmlClassNode[],
  layout: CanvasLayout,
  idFactory: IdFactory,
  requestedName = '',
) {
  if (
    !needsAssociationClass(relation) ||
    (relation.associationClassId && classes.some((item) => item.id === relation.associationClassId))
  )
    return null;

  const source = classes.find((item) => item.id === relation.sourceId),
    target = classes.find((item) => item.id === relation.targetId);

  if (!source || !target) return null;

  const base = requestedName.trim() || `Detalle${source.name}${target.name}`;

  let name = base,
    suffix = 2;

  while (classes.some((item) => normalizedName(item.name) === normalizedName(name)))
    name = `${base}${suffix++}`;

  const centerX = (source.x + target.x + layout.cardWidth) / 2;

  const centerY = (source.y + target.y + classHeight(source) / 2 + classHeight(target) / 2) / 2;

  const associationClass: UmlClassNode = {
    id: idFactory('assoc'),
    name,
    description: 'Atributos de la asociación',
    stereotype: 'Clase de asociación',

    attributes: [],
    methods: [],
    responsibilities: [],
    x: Math.max(
      20,
      Math.min(layout.canvasWidth - layout.cardWidth - 20, centerX - layout.cardWidth / 2),
    ),

    y: Math.max(20, Math.min(layout.canvasHeight - 150, centerY + 110)),
    color: '#2563eb',
  };

  relation.associationClassId = associationClass.id;

  return associationClass;
}
export interface ImageImportResult {
  model?: UmlDiagramModel;
  inserted: UmlClassNode[];
  insertedRelations: UmlRelation[];
  error?: string;
}
export function buildImageImport(
  model: UmlDiagramModel,
  analysis: UmlImageAnalysis,
  replaceCanvas: boolean,
  layout: CanvasLayout,
  idFactory: IdFactory,
): ImageImportResult {
  const previousClasses = model.classes,
    previousRelations = model.relations;
  const baseClasses = replaceCanvas ? [] : previousClasses,
    baseRelations = replaceCanvas ? [] : previousRelations;
  const existingByName = new Map(baseClasses.map((item) => [normalizedName(item.name), item]));
  const inserted: UmlClassNode[] = [];
  for (const detected of analysis.classes) {
    const key = normalizedName(detected.name);
    if (existingByName.has(key)) continue;
    const count = baseClasses.length + inserted.length;
    const item: UmlClassNode = {
      id: uniqueIdAgainst(detected.name, [...baseClasses, ...inserted]),
      name: detected.name,
      description: 'Reconocida desde imagen',
      stereotype: 'Entidad',
      attributes: detected.attributes.map((value) => ({
        id: idFactory('attr'),
        name: value.name,
        type: value.type,
        isPrimaryKey: value.isPrimaryKey === true,
      })),
      methods: (detected.methods || []).map((method) => ({
        id: idFactory('method'),
        name: method.name,
        parameters: method.parameters || '',
        returnType: method.returnType || '',
      })),
      responsibilities: [],
      x: 70 + (count % 4) * 350,
      y: 70 + Math.floor(count / 4) * 300,
      color: ['#1e3a8a', '#2563eb', '#7c3aed', '#b45309'][count % 4],
    };
    if (Number.isFinite(detected.x) && Number.isFinite(detected.y)) {
      item.x = Math.round(
        40 + Math.max(0, Math.min(1, detected.x!)) * (layout.canvasWidth - layout.cardWidth - 80),
      );
      item.y = Math.round(
        40 +
          Math.max(0, Math.min(1, detected.y!)) *
            Math.max(0, layout.canvasHeight - classHeight(item) - 80),
      );
    }
    inserted.push(item);
    existingByName.set(key, item);
  }

  const insertedRelations: UmlRelation[] = [];
  for (const detected of analysis.relations) {
    const source = existingByName.get(normalizedName(detected.sourceClassName)),
      target = existingByName.get(normalizedName(detected.targetClassName));
    if (!source || !target) continue;
    const duplicate = [...baseRelations, ...insertedRelations].some(
      (value) =>
        value.sourceId === source.id &&
        value.targetId === target.id &&
        value.type === detected.type &&
        value.label === (detected.label || '') &&
        value.sourceMultiplicity === detected.sourceMultiplicity &&
        value.targetMultiplicity === detected.targetMultiplicity &&
        value.associationClassId ===
          (detected.associationClassName
            ? existingByName.get(normalizedName(detected.associationClassName))?.id
            : undefined),
    );
    if (duplicate) continue;
    const relation: UmlRelation = {
      id: idFactory('rel'),
      sourceId: source.id,
      targetId: target.id,
      type: detected.type as UmlRelationType,
      label: detected.label || '',
      sourceMultiplicity: detected.sourceMultiplicity,
      targetMultiplicity: detected.targetMultiplicity,
    };
    const recognized = findRecognizedAssociationClass(
      detected,
      [...baseClasses, ...inserted],
      [...baseRelations, ...insertedRelations],
    );
    if (
      recognized &&
      (needsAssociationClass(relation) || !!detected.associationClassName?.trim())
    ) {
      relation.associationClassId = recognized.id;
      recognized.stereotype = 'Clase de asociación';
      const placement = analysis.classes.find(
        (item) => normalizedName(item.name) === normalizedName(recognized.name),
      );
      if (!Number.isFinite(placement?.x) || !Number.isFinite(placement?.y)) {
        recognized.x = Math.max(
          20,
          Math.min(layout.canvasWidth - layout.cardWidth - 20, (source.x + target.x) / 2),
        );
        recognized.y = Math.max(
          20,
          Math.min(layout.canvasHeight - 150, (source.y + target.y) / 2 + 130),
        );
      }
    }
    insertedRelations.push(relation);
  }

  if (!inserted.length && !insertedRelations.length)
    return {
      inserted,
      insertedRelations,
      error: 'No hay elementos nuevos para insertar. Revisa los conflictos indicados.',
    };
  return {
    model: {
      ...model,
      classes: [...baseClasses, ...inserted],
      relations: [...baseRelations, ...insertedRelations],
    },
    inserted,
    insertedRelations,
  };
}
