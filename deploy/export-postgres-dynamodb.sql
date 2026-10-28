-- Run psql -qAt -f this-file > a PRIVATE backup; never commit the output.
-- One consistent snapshot across the seven collections. No data is modified.
BEGIN TRANSACTION ISOLATION LEVEL REPEATABLE READ READ ONLY;
SELECT json_build_object(
 'format', 'arqnova-dynamodb-export-v1',
 'USER', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT id,username,password_hash AS "passwordHash",full_name AS "fullName",role,department,avatar_url AS "avatarUrl",enabled,created_at AS "createdAt",updated_at AS "updatedAt" FROM users ORDER BY id) v), '[]'::json),
 'DEPARTMENT', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT id,name AS nombre,description AS descripcion,created_by AS "creadoPor",active AS activo,created_at AS "fechaCreacion" FROM departments ORDER BY id) v), '[]'::json),
 'PROJECT', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT id,name,description,status,owner_user_id AS "ownerUserId",created_at AS "createdAt",updated_at AS "updatedAt" FROM projects ORDER BY id) v), '[]'::json),
 'MEMBER', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT json_build_object('projectId',project_id,'userId',user_id) AS id,member_role AS "memberRole",created_at AS "createdAt" FROM project_members ORDER BY project_id,user_id) v), '[]'::json),
 'DIAGRAM', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT id,project_id AS "projectId",model,model_version AS "modelVersion",created_at AS "createdAt",updated_at AS "updatedAt" FROM uml_diagrams ORDER BY id) v), '[]'::json),
 'SESSION', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT id,project_id AS "projectId",invite_code AS "inviteCode",created_by AS "createdBy",invite_role AS "inviteRole",active,model_snapshot AS model,created_at AS "createdAt",updated_at AS "updatedAt" FROM uml_collaboration_sessions ORDER BY id) v), '[]'::json),
 'ACTIVITY', COALESCE((SELECT json_agg(row_to_json(v)) FROM (SELECT event_id AS "eventId",session_id AS "sessionId",project_id AS "projectId",user_id AS "userId",user_name AS "userName",event_type AS "eventType",element_id AS "elementId",element_type AS "elementType",operation,event_data AS "eventData",occurred_at AS "occurredAt" FROM uml_collaboration_activities ORDER BY event_id) v), '[]'::json)
);
COMMIT;
