import { UmlDiagramModel } from './uml-class-diagram.models';

type Row = Record<string, unknown>;
const record = (value: unknown): value is Row => !!value && typeof value === 'object' && !Array.isArray(value);

export function reconcileDiagram(base: UmlDiagramModel, local: UmlDiagramModel, remote: UmlDiagramModel) {
  let conflict = false;
  function equal(a: unknown, b: unknown): boolean {
    if (a === b) return true;
    if (Array.isArray(a) && Array.isArray(b)) return a.length === b.length && a.every((v, i) => equal(v, b[i]));
    return record(a) && record(b) && Object.keys(a).length === Object.keys(b).length
      && Object.keys(a).every(key => Object.hasOwn(b, key) && equal(a[key], b[key]));
  }

  function merge(before: unknown, mine: unknown, theirs: unknown): unknown {
    if (equal(mine, before) || equal(mine, theirs)) return theirs;
    if (equal(theirs, before)) return mine;
    if (record(before) && record(mine) && record(theirs)) {
      const keys = new Set([...Object.keys(theirs), ...Object.keys(mine), ...Object.keys(before)]);
      return Object.fromEntries([...keys].map(key => [key, merge(before[key], mine[key], theirs[key])])
        .filter(([, value]) => value !== undefined));
    }
    if (Array.isArray(before) && Array.isArray(mine) && Array.isArray(theirs)
      && [...before, ...mine, ...theirs].every(row => record(row) && typeof row['id'] === 'string')) {
      const keyed = (rows: Row[]) => new Map(rows.map(row => [row['id'], row]));
      const b = keyed(before), l = keyed(mine), r = keyed(theirs);
      const ids = new Set([...r.keys(), ...l.keys(), ...b.keys()]);
      return [...ids].map(id => merge(b.get(id), l.get(id), r.get(id))).filter(row => row !== undefined);
    }
    conflict = true;
    return mine;
  }

  const baseVersion = { ...base, updatedAt: remote.updatedAt };
  const localVersion = { ...local, updatedAt: remote.updatedAt };
  const model = merge(baseVersion, localVersion, remote) as UmlDiagramModel;
  const classes = new Set(model.classes.map(node => node.id));
  const dangling = model.relations.some(relation => !classes.has(relation.sourceId)
    || !classes.has(relation.targetId) || (!!relation.associationClassId && !classes.has(relation.associationClassId)));
  return conflict || dangling ? null : { model, dirty: !equal(model, remote) };
}
