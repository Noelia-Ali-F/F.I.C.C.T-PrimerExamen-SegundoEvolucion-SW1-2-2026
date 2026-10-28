import { UmlDiagramModel } from './uml-class-diagram.models';
export interface UmlSession {
  sessionId: string;
  projectId: string;
  projectName: string;
  inviteCode: string;
  hostUserId?: string;
  memberRole?: 'OWNER' | 'EDITOR' | 'VIEWER';
  model: UmlDiagramModel;
}

export interface UmlParticipant {
  userId: string;
  userName: string;
  status: string;
  host?: boolean;
}
export type UmlLockElementType = 'class' | 'attribute' | 'relation';
export interface UmlElementLock {
  projectId: string;
  sessionId: string;
  elementId: string;
  elementType: UmlLockElementType;
  userId: string;
  userName: string;
  lockedAt: number;
  expiresAt: number;
}
export interface UmlLiveEvent {
  eventId?: string;
  type: string;
  projectId: string;
  sessionId?: string;
  userId?: string;
  userName?: string;
  elementId?: string;
  elementType?: UmlLockElementType;
  operation?: string;
  data?: {
    element?: unknown;
    classId?: string;
    attributeId?: string;
    attribute?: unknown;
    participants?: UmlParticipant[];
    lock?: UmlElementLock;
    requesterId?: string;
  };
  timestamp?: number;
}
