export type UmlRelationType = 'association' | 'aggregation' | 'composition' | 'inheritance' | 'dependency';

export interface UmlAttribute {
  id: string;
  name: string;
  type: string;
  isPrimaryKey: boolean;
}

export interface UmlMethod {
  id: string;
  name: string;
  parameters: string;
  returnType: string;
}

export interface UmlClassNode {
  id: string;
  name: string;
  description?: string;
  stereotype?: string;
  attributes: UmlAttribute[];
  methods: UmlMethod[];
  responsibilities: string[];
  x: number;
  y: number;
  color: string;
}

export interface UmlRelation {
  id: string;
  sourceId: string;
  targetId: string;
  type: UmlRelationType;
  label: string;
  sourceMultiplicity: string;
  targetMultiplicity: string;
  associationClassId?: string;
}

export interface UmlDiagramModel {
  format: 'uml-class-diagram';
  version: 2;
  title: string;
  classes: UmlClassNode[];
  relations: UmlRelation[];
  updatedAt?: string;
}

export type UmlProjectStatus = 'EN_EDICION' | 'COMPLETADO' | 'PENDIENTE';

export interface UmlProject {
  id: string;
  name: string;
  description: string;
  status: UmlProjectStatus;
  createdAt: string;
  updatedAt: string;
  model: UmlDiagramModel;
  modelVersion?: number;
  owner?: string;
  memberRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
}

const attribute = (id: string, value: string): UmlAttribute => {
  const separator = value.indexOf(':');
  return {
    id,
    name: separator >= 0 ? value.slice(0, separator).trim() : value.trim(),
    type: separator >= 0 ? value.slice(separator + 1).trim() : 'String',
    isPrimaryKey: false
  };
};

const umlClass = (
  id: string, name: string, stereotype: string, x: number, y: number, color: string,
  attributes: string[], description: string
): UmlClassNode => ({
  id, name, stereotype, x, y, color, description,
  attributes: attributes.map((value, index) => attribute(`${id}-attr-${index + 1}`, value)),
  methods: [],
  responsibilities: [description]
});

export const DEFAULT_UML_MODEL: UmlDiagramModel = {
  format: 'uml-class-diagram',
  version: 2,
  title: 'Modelo comercial de ejemplo',
  classes: [
    umlClass('cliente', 'Cliente', 'Entidad', 120, 100, '#1e3a8a', ['id: Long', 'nombre: String', 'correo: String'], 'Representa a un cliente del sistema'),
    umlClass('producto', 'Producto', 'Entidad', 760, 100, '#2563eb', ['id: Long', 'nombre: String', 'precio: BigDecimal'], 'Representa un producto disponible'),
    umlClass('venta', 'Venta', 'Entidad', 440, 420, '#7c3aed', ['id: Long', 'fecha: DateTime', 'total: BigDecimal'], 'Registra una venta realizada a un cliente')
  ],
  relations: [
    { id: 'r1', sourceId: 'cliente', targetId: 'venta', type: 'association', label: 'realiza', sourceMultiplicity: '1', targetMultiplicity: '0..*' },
    { id: 'r2', sourceId: 'venta', targetId: 'producto', type: 'association', label: 'incluye', sourceMultiplicity: '0..*', targetMultiplicity: '1..*' }
  ]
};
