import { DEFAULT_UML_MODEL } from './uml-class-diagram.models';
import { reconcileDiagram } from './reconcile-diagram';

describe('reconcileDiagram', () => {
  const base = { ...DEFAULT_UML_MODEL, classes: DEFAULT_UML_MODEL.classes.slice(0, 2), relations: [] };
  const attribute = { id: 'new-attr', name: 'Descripcion', type: 'String', isPrimaryKey: false };

  it('combina cambios de campos distintos incluso en la misma clase', () => {
    const local = { ...base, classes: [{ ...base.classes[0], description: 'Local' }, base.classes[1]] };
    const remote = { ...base, classes: [{ ...base.classes[0], attributes: [...base.classes[0].attributes, attribute] }, base.classes[1]] };
    const merged = reconcileDiagram(base, local, remote);
    expect(merged?.dirty).toBe(true);
    expect(merged?.model.classes[0].description).toBe('Local');
    expect(merged?.model.classes[0].attributes).toContainEqual(attribute);
  });

  it('conserva las dos versiones si cambian el mismo campo o se elimina el elemento editado', () => {
    const local = { ...base, classes: [{ ...base.classes[0], description: 'Local' }, base.classes[1]] };
    const remote = { ...base, classes: [{ ...base.classes[0], description: 'Equipo' }, base.classes[1]] };
    expect(reconcileDiagram(base, local, remote)).toBeNull();
    expect(reconcileDiagram(base, local, { ...base, classes: [base.classes[1]] })).toBeNull();
  });
});
