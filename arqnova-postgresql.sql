--
-- PostgreSQL database dump
--

\restrict eFx8ug5B9651dKdpcMN5UIdsejPsK89IHeFWwmPGzrNlTUub4XnAL2N5APqdTqH

-- Dumped from database version 16.15 (Debian 16.15-1.pgdg13+2)
-- Dumped by pg_dump version 18.6

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET transaction_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

ALTER TABLE IF EXISTS ONLY public.uml_collaboration_sessions DROP CONSTRAINT IF EXISTS uml_collaboration_sessions_project_id_fkey;
ALTER TABLE IF EXISTS ONLY public.uml_collaboration_activities DROP CONSTRAINT IF EXISTS uml_collaboration_activities_session_id_fkey;
ALTER TABLE IF EXISTS ONLY public.uml_collaboration_activities DROP CONSTRAINT IF EXISTS uml_collaboration_activities_project_id_fkey;
ALTER TABLE IF EXISTS ONLY public.uml_diagrams DROP CONSTRAINT IF EXISTS fk_uml_diagrams_project;
ALTER TABLE IF EXISTS ONLY public.project_members DROP CONSTRAINT IF EXISTS fk_project_members_project;
DROP INDEX IF EXISTS public.idx_users_role;
DROP INDEX IF EXISTS public.idx_users_enabled;
DROP INDEX IF EXISTS public.idx_uml_collaboration_project;
DROP INDEX IF EXISTS public.idx_uml_collaboration_invite;
DROP INDEX IF EXISTS public.idx_uml_collaboration_created_by;
DROP INDEX IF EXISTS public.idx_uml_collaboration_active;
DROP INDEX IF EXISTS public.idx_uml_activity_session_time;
DROP INDEX IF EXISTS public.idx_uml_activity_project_time;
DROP INDEX IF EXISTS public.idx_projects_updated_at;
DROP INDEX IF EXISTS public.idx_projects_status;
DROP INDEX IF EXISTS public.idx_projects_owner_user_id;
DROP INDEX IF EXISTS public.idx_project_members_user_id;
DROP INDEX IF EXISTS public.idx_departments_active_name;
DROP INDEX IF EXISTS public.flyway_schema_history_s_idx;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_username_key;
ALTER TABLE IF EXISTS ONLY public.users DROP CONSTRAINT IF EXISTS users_pkey;
ALTER TABLE IF EXISTS ONLY public.uml_diagrams DROP CONSTRAINT IF EXISTS uml_diagrams_project_id_key;
ALTER TABLE IF EXISTS ONLY public.uml_diagrams DROP CONSTRAINT IF EXISTS uml_diagrams_pkey;
ALTER TABLE IF EXISTS ONLY public.uml_collaboration_sessions DROP CONSTRAINT IF EXISTS uml_collaboration_sessions_pkey;
ALTER TABLE IF EXISTS ONLY public.uml_collaboration_sessions DROP CONSTRAINT IF EXISTS uml_collaboration_sessions_invite_code_key;
ALTER TABLE IF EXISTS ONLY public.uml_collaboration_activities DROP CONSTRAINT IF EXISTS uml_collaboration_activities_pkey;
ALTER TABLE IF EXISTS ONLY public.projects DROP CONSTRAINT IF EXISTS projects_pkey;
ALTER TABLE IF EXISTS ONLY public.project_members DROP CONSTRAINT IF EXISTS project_members_pkey;
ALTER TABLE IF EXISTS ONLY public.flyway_schema_history DROP CONSTRAINT IF EXISTS flyway_schema_history_pk;
ALTER TABLE IF EXISTS ONLY public.departments DROP CONSTRAINT IF EXISTS departments_pkey;
ALTER TABLE IF EXISTS ONLY public.departments DROP CONSTRAINT IF EXISTS departments_name_key;
ALTER TABLE IF EXISTS ONLY public.app_schema_check DROP CONSTRAINT IF EXISTS app_schema_check_pkey;
DROP TABLE IF EXISTS public.users;
DROP TABLE IF EXISTS public.uml_diagrams;
DROP TABLE IF EXISTS public.uml_collaboration_sessions;
DROP TABLE IF EXISTS public.uml_collaboration_activities;
DROP TABLE IF EXISTS public.projects;
DROP TABLE IF EXISTS public.project_members;
DROP TABLE IF EXISTS public.flyway_schema_history;
DROP TABLE IF EXISTS public.departments;
DROP TABLE IF EXISTS public.app_schema_check;
SET default_tablespace = '';

SET default_table_access_method = heap;

--
-- Name: app_schema_check; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.app_schema_check (
    id integer NOT NULL,
    description character varying(100) NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL
);


--
-- Name: departments; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.departments (
    id character varying(64) NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    created_by character varying(160),
    active boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone,
    updated_at timestamp without time zone
);


--
-- Name: flyway_schema_history; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.flyway_schema_history (
    installed_rank integer NOT NULL,
    version character varying(50),
    description character varying(200) NOT NULL,
    type character varying(20) NOT NULL,
    script character varying(1000) NOT NULL,
    checksum integer,
    installed_by character varying(100) NOT NULL,
    installed_on timestamp without time zone DEFAULT now() NOT NULL,
    execution_time integer NOT NULL,
    success boolean NOT NULL
);


--
-- Name: project_members; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.project_members (
    project_id character varying(128) NOT NULL,
    user_id character varying(64) NOT NULL,
    member_role character varying(30) NOT NULL,
    created_at timestamp without time zone NOT NULL
);


--
-- Name: projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.projects (
    id character varying(128) NOT NULL,
    name character varying(150) NOT NULL,
    description text,
    status character varying(30) NOT NULL,
    owner_user_id character varying(64) NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: uml_collaboration_activities; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uml_collaboration_activities (
    event_id character varying(64) NOT NULL,
    session_id character varying(64) NOT NULL,
    project_id character varying(128) NOT NULL,
    user_id character varying(64) NOT NULL,
    user_name character varying(180) NOT NULL,
    event_type character varying(60) NOT NULL,
    element_id character varying(128),
    element_type character varying(30),
    operation character varying(255) NOT NULL,
    event_data jsonb,
    occurred_at timestamp without time zone NOT NULL
);


--
-- Name: uml_collaboration_sessions; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uml_collaboration_sessions (
    id character varying(64) NOT NULL,
    project_id character varying(128) NOT NULL,
    invite_code character varying(100) NOT NULL,
    created_by character varying(64) NOT NULL,
    invite_role character varying(30) DEFAULT 'EDITOR'::character varying NOT NULL,
    active boolean DEFAULT true NOT NULL,
    model_snapshot jsonb,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_uml_collaboration_invite_role CHECK (((invite_role)::text = ANY ((ARRAY['EDITOR'::character varying, 'VIEWER'::character varying])::text[])))
);


--
-- Name: uml_diagrams; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.uml_diagrams (
    id character varying(128) NOT NULL,
    project_id character varying(128) NOT NULL,
    model jsonb NOT NULL,
    model_version integer DEFAULT 1 NOT NULL,
    created_at timestamp without time zone NOT NULL,
    updated_at timestamp without time zone NOT NULL
);


--
-- Name: users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.users (
    id character varying(64) NOT NULL,
    username character varying(180) NOT NULL,
    password_hash character varying(255) NOT NULL,
    full_name character varying(200),
    role character varying(50) NOT NULL,
    department character varying(150),
    avatar_url text,
    enabled boolean DEFAULT true NOT NULL,
    created_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    updated_at timestamp without time zone DEFAULT CURRENT_TIMESTAMP NOT NULL,
    CONSTRAINT ck_users_role CHECK (((role)::text = ANY ((ARRAY['ADMINISTRADOR'::character varying, 'REVISOR'::character varying, 'SOLICITANTE'::character varying])::text[])))
);


--
-- Data for Name: app_schema_check; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.app_schema_check (id, description, created_at) FROM stdin;
1	PostgreSQL infrastructure initialized	2026-09-18 03:30:13.956747
\.


--
-- Data for Name: departments; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.departments (id, name, description, created_by, active, created_at, updated_at) FROM stdin;
6aa99c4ebdbe4709c690dd7d	Sistemas	Tecnología, seguridad digital y soporte de infraestructura en la nube.	system	t	2026-09-15 19:28:14.52	2026-09-18 14:30:10.275448
6aa99c4ebdbe4709c690dd7e	Recursos Humanos	Gestión del talento humano, bienestar corporativo y nóminas.	system	t	2026-09-15 19:28:14.53	2026-09-18 14:30:10.304274
6aa99c4ebdbe4709c690dd7f	Ventas	Área comercial, captación de clientes corporativos y licencias de software.	system	t	2026-09-15 19:28:14.531	2026-09-18 14:30:10.30638
6aa99c4ebdbe4709c690dd80	Finanzas	Control del presupuesto anual, tesorería y auditorías de gastos.	system	t	2026-09-15 19:28:14.531	2026-09-18 14:30:10.308314
\.


--
-- Data for Name: flyway_schema_history; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.flyway_schema_history (installed_rank, version, description, type, script, checksum, installed_by, installed_on, execution_time, success) FROM stdin;
1	1	postgresql baseline	SQL	V1__postgresql_baseline.sql	1177763148	arqnova	2026-09-18 03:30:13.936456	9	t
2	2	create uml project core	SQL	V2__create_uml_project_core.sql	-571350027	arqnova	2026-09-18 03:39:09.789275	33	t
3	3	create uml collaboration sessions	SQL	V3__create_uml_collaboration_sessions.sql	-1413860004	arqnova	2026-09-18 13:08:38.196096	50	t
4	4	create users	SQL	V4__create_users.sql	229877898	arqnova	2026-09-18 13:24:15.405511	40	t
5	5	create departments	SQL	V5__create_departments.sql	811725101	arqnova	2026-09-18 14:30:08.411618	12	t
6	6	create application documents	SQL	V6__create_application_documents.sql	-1298150364	arqnova	2026-09-18 14:30:08.43867	5	t
7	7	drop application documents	SQL	V7__drop_application_documents.sql	1215922894	arqnova	2026-09-21 03:26:41.744411	21	t
8	8	create uml collaboration activities	SQL	V8__create_uml_collaboration_activities.sql	-748770914	arqnova	2026-09-21 22:51:23.283697	39	t
\.


--
-- Data for Name: project_members; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.project_members (project_id, user_id, member_role, created_at) FROM stdin;
uml-1790012248673-yl3fkr	alison	OWNER	2026-09-21 17:37:28.688515
uml-1790012273586-5r87k9	admin	OWNER	2026-09-21 17:37:53.601961
uml-1790012248673-yl3fkr	diego	EDITOR	2026-09-21 17:39:20.699143
uml-1789755617271-r3lvw1	admin	OWNER	2026-09-21 18:23:06.490167
uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	OWNER	2026-09-21 22:52:39.064497
\.


--
-- Data for Name: projects; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.projects (id, name, description, status, owner_user_id, created_at, updated_at) FROM stdin;
uml-1790012273586-5r87k9	Modelo conceptual del sistema	Diagrama recuperado de la versión anterior.	EN_EDICION	admin	2026-09-21 17:37:53.600931	2026-09-21 17:37:53.619797
uml-1790012248673-yl3fkr	hospital	bd hospital	EN_EDICION	alison	2026-09-21 17:37:28.68646	2026-09-21 19:56:05.081438
uml-1789755617271-r3lvw1	hospital		EN_EDICION	admin	2026-09-21 18:23:06.471897	2026-09-21 18:23:06.51421
uml-1790031159041-2hgkwf	Validación CU7 actividad	Proyecto temporal para validar historial colaborativo	EN_EDICION	cu4-audit-20260921@arqnova.test	2026-09-21 22:52:39.055804	2026-09-21 22:52:59.980372
\.


--
-- Data for Name: uml_collaboration_activities; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.uml_collaboration_activities (event_id, session_id, project_id, user_id, user_name, event_type, element_id, element_type, operation, event_data, occurred_at) FROM stdin;
597d85fd-7c18-4011-adf4-e6de21b3bac4	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:51:30.145
7a092ab6-f579-4e2a-b243-c7e60099c53c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:52:29.951
a8bf33d4-6644-4ef6-a135-e9c6536d53b7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:52:33.91
1e25583f-d6d7-4c80-8b5a-b0e19f86a805	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:52:52.131
21ba5aeb-6fd3-4d37-abfd-2dcb78e731ce	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:53:33.952
07867cd7-b7b8-4205-91d8-295788384281	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:53:37.907
20a9332a-15a0-4f01-831e-de068b738ecb	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:53:59.608
642fad78-361e-48f9-be9c-0a2a6bf4faa3	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:54:02.626
b3f65596-d79c-4fde-a514-da5326c14dc1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:54:37.95
1c266798-72ef-4ea8-a621-7a177faca924	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:54:41.902
4e795bd4-eeae-41f5-945b-6a35f8d359ac	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:55:02.671
70b0cf2d-234a-4455-a45f-7ad17b165abf	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:55:05.699
884b8ff0-b89a-48b0-9177-d28b2fd6cbb7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:55:41.948
01cf4c70-81f0-447c-91d3-6859c9ee7a79	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:55:45.906
3f2f3dc9-e094-4241-b097-bd1b07c16afb	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:55:50.077
77df22fb-50a0-4d24-a949-c8c4afbc1c4a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:56:45.949
f0c00fa6-1dfc-43e0-a16b-b2687a00143c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:56:49.909
3a5ac82d-f41a-4640-818f-cc5508534df7	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:56:52.652
a2c8ff9e-b576-470a-9b5f-23efb366b59f	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	class:create	facturacu7	class	Clase creada	{"element": {"x": 420, "y": 70, "id": "facturacu7", "name": "FacturaCU7", "color": "#2563eb", "methods": [], "attributes": [], "stereotype": "Entidad", "description": "", "responsibilities": []}}	2026-09-21 22:57:03.166
3ca2c633-0737-4fbb-9362-848ab91190f0	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:57:16.053
64aaec42-727c-4de2-92be-c5938cfabe4a	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:57:17.875
5a39b7fe-6c2a-4bac-95df-624e03aa929b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:57:49.923
eca9f398-fe44-4f02-87ea-e6dbdfc10bd3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:57:53.906
ac66a1dc-7ac7-469a-88f8-be6db5950ab0	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:58:17.886
359d316c-99dc-4490-953c-6267aae25174	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": true, "status": "online", "userId": "cu4-audit-20260921@arqnova.test", "userName": "Auditor CU4"}]}	2026-09-21 22:58:20.903
b572ae5d-2a50-4627-9c05-974dfb51e995	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:58:53.92
d34d287d-4dd3-43d0-9b90-0feddad10790	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 22:58:57.907
ea70d197-aabe-4884-84af-450ce8dcae8b	0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	cu4-audit-20260921@arqnova.test	Auditor CU4	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:59:17.968
747026e4-92cd-4c30-b0e8-1c17f985b7e4	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 22:59:57.93
2c681f28-3510-43b4-b2c3-ece6accef74f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:00:01.903
0ed422bc-5c2a-419e-9a68-89f9fb439f39	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:01:01.949
b8b03ea5-9eb2-45ba-904f-db9b9e39af3b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:01:05.903
26a2c73b-4b00-4b3e-b91d-a28699ee76fd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:02:09.902
af62c383-2f35-425d-add2-db5fb5f4935f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:03:13.913
fb37ea23-18b4-4508-a9c6-480611b8f8cf	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:04:17.902
65804328-fc5c-4209-85da-44dffd635fbd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:05:17.948
0c7957aa-e908-4869-b0ba-1fb4df76028a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:05:21.905
563edaf5-7649-4288-a4e1-91f85bea32d7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:06:21.95
c21e21f6-f810-48bf-8819-302af6237e85	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:06:25.907
196f1e8b-333c-4235-9be7-e33ec271d3e6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:07:29.903
93b2fb60-ab3f-4634-a28d-1e8268120d95	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:08:33.906
43e5f929-bf6f-4c0b-876b-4010d113fd3f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:09:37.915
c43d8c16-b805-4825-9020-3344435ee8e3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:10:37.951
616ca384-8551-4049-95b7-bf6046bf94b5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:10:41.904
0e4c182c-8cd2-4a5e-81f2-d262b661fde5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:11:41.949
98b3117c-f8b4-4a66-bfa9-9b88496ded20	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:11:45.919
332b8a34-5415-4f6c-b9f6-8b0abbcd0956	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:02:05.95
9bc3d9bf-3ea8-4cf2-b451-b8a35c2a1dfe	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:03:09.949
c1b74d74-530d-4cab-a9f3-4159b770bb1e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:04:13.938
ba27a317-7609-4338-9970-4db02055c0f4	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:07:25.95
2ca73284-6fa6-4705-aaa6-d46d164add31	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:08:29.948
da17c6d6-3fcc-4ecb-8599-06f4739272ac	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:09:33.95
5ce16c3b-eb7b-4ddf-b2ab-46e2389993c9	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:12:45.952
c24870b7-2b64-479b-adf3-10c13a1c95d0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:12:49.916
f5f1f35c-a6f9-4ecb-951f-37b4921da8e0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:13:49.96
80f18751-e4c8-44d1-b7b4-9a7b87e0891d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:13:53.959
6ef1e790-7b06-4ae5-baa9-d20c4f00f08a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:14:53.952
97ffb15c-e0ef-4d30-b5e4-483149a59842	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:14:57.9
d695ebe2-40fe-4a40-835e-078ea6ed7240	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:15:57.949
aeaed1f7-367b-49ef-b6cb-d2d1c950f47c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:16:01.9
f99e697a-051c-4aa9-81b0-aff97243b775	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:16:43.687
05932b98-dfcf-46fa-8a84-b8408aabc2e4	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:16:51.101
16bfaf88-5929-4094-92d8-2ad70f744a9d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:17:50.949
fe0a3651-914b-49e3-98e1-467de5408de0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:17:54.902
49b1b09d-4554-4280-8b54-ed5e98524710	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:18:54.95
afc07da6-0601-4a85-a698-ddbb8b50bb24	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:18:58.904
1f808731-441e-4cbf-8377-bbfb1885d420	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:19:58.949
e1a3d4a8-3b75-46f4-9cb7-ddf45591216f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:20:02.902
7e8965f3-294d-4058-ac6f-7c76300d9356	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:21:02.948
0a78dfcc-f2c7-4d00-8676-3ea161405a54	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:21:06.934
0e6cc2a1-d7b0-453e-84c8-3e41c65f1fea	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:21:45.406
ea8c46ee-421d-486c-b46f-e6dfdbf727dd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:21:53.111
45130868-2b70-4906-b08a-9b16c61a75e1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:22:52.949
d92db215-071b-452a-8197-03a78f91828c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:22:56.907
8f34787d-05c0-4db1-9d2b-e5d81cffa98e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:23:56.949
fcc70e72-3274-4d67-a830-aaa58e8e3b1a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:24:00.907
f3ed9889-839c-436a-a0e3-256f48e62c4b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:25:00.959
132a01d4-c99a-4704-bd72-22bd04f307c5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:25:04.911
f3752961-584f-432a-aa20-2e245165dd13	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:26:04.956
f81946a8-242d-4462-b93c-773110763816	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:26:08.904
42ce434a-44bd-4108-9b80-31e53e42609e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:27:08.948
a1bba35e-5a64-487a-8d95-04f1a38af891	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:27:12.914
2e6a161f-7140-460f-b40b-c6d6bfccaa93	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:28:12.954
48bd1b07-8c9e-4b76-ad06-26547f147c0e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:28:16.904
b7060fbc-6d70-4306-8f94-e8f71d28869b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:29:16.948
530668ba-1d02-42e8-8c55-a98e0a7f00d8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:29:20.904
d7449fb7-ec68-4345-bd78-e170ec0f333a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:30:20.948
d0b6d23b-a46b-4316-aadf-83a6d327f20d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:30:24.899
833a8175-21ee-48da-ba03-96a5cfd31db7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:31:24.948
e4628b1d-bc48-4b4f-963e-fcb04fb6a3d1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:31:28.901
45f66fb6-4970-4cf4-b460-d582bcde5d69	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:32:28.948
0d51cf39-10d1-400b-9c9d-25bef0d10913	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:32:32.9
72307f40-ca7a-48fc-98d8-7a1671770c0f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:33:32.948
68efb41d-011c-46ee-bd47-e5a00eac8f0f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:33:36.9
2177b9e0-e7a3-4183-a9bd-40131d4dd023	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:34:36.948
09085666-ed59-4a37-b81e-df03842403e8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:34:40.902
7a819b4c-f5b3-49fa-aad6-743d92a12796	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:35:40.949
5f8db600-b190-4492-b19e-fa24f0944fa1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:35:44.9
1d6d6c5e-bc90-4a66-b1e7-aa1e9793a7ed	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:36:44.948
edae8e37-4d5b-4596-affd-a61fd00e43b1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:36:48.911
e65bf58e-4bbf-4f80-b3c8-49db50de108d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:37:48.949
a9b803ad-42e1-4c5b-9b68-4000e099918a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:37:52.915
402efff5-9f36-443b-896f-58a03b3a1228	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:38:52.957
244bbaaa-e5a1-41bd-afb2-d9603a05b057	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:38:56.903
868871ff-6cdc-4b76-a03d-1b04320aa5b8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:39:56.949
9594fc20-5e67-4bc0-850d-37a70cec9b76	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:40:00.901
ba7c3518-fed5-4990-946e-ef6497a09bfc	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:41:00.948
f98104b8-7015-46a5-aefc-97f22a58f202	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:41:04.902
4ae2505f-b1a1-4381-bcf6-6d0342368ba5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:42:04.949
7cc40e6c-35da-4332-80f4-05a68bce4c15	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:42:08.902
d22799b1-d60f-42d6-b72f-4af34c92ed65	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:43:08.948
682d34d2-34a5-4290-84b0-ff80c18a7ed1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:43:12.902
65354dbe-95cd-4575-9cfd-ebf0b7b8d3fc	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:44:12.95
912bfac8-b350-4d09-b829-66ca4d8bdce8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:44:16.904
1231e789-0632-4a6a-9906-4c18ba6dc104	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:45:16.949
b048e904-2a22-4f99-8abf-2cf808760ffe	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:45:20.903
5f6998a8-2a94-4e01-8758-889d7d171acf	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:46:20.949
abd3c6d7-2463-45f3-a76a-657db04effd2	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:46:24.901
f2003115-0fb8-404f-8a81-61e518072c9f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:47:24.949
a16c09c4-40c5-4356-a900-699aa306c279	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:47:28.901
bff3e011-6d20-4da3-9a02-65b1cef3f399	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:48:28.949
7b25ff6b-874f-4014-93f4-0b1a42c70294	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:48:32.904
7d3f46c0-00b2-42d8-9e5b-bc510972b736	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:49:32.955
7112488e-2e67-4997-96d7-c8a5ff94d9b3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:49:36.912
e19e698d-5ee9-4116-b787-c05a86e9841f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:50:36.949
6654b4ce-3c83-409f-860e-9bbb872b5f26	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:50:40.907
d1e598f7-d832-4b8f-9c43-f8c6bb26c1ed	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:51:40.956
702eae9c-7a3b-483a-aa77-16d69e1e4df8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:51:44.911
47a6d825-52dc-4899-8239-4c54c9e8323a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:52:44.949
660afc24-9a99-4647-8538-1f81533ab6aa	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:52:48.902
f37c2a97-d690-48e6-b45a-e21bba394b98	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:53:48.948
05d32819-4052-4054-aa49-7f16a4468516	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:53:52.899
f7cd1b22-de04-4e0b-ac51-36fa4169a8db	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:54:52.948
a89c5969-7621-4e7b-802b-6d0af1bbf5d3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:54:56.921
d8b5736b-c69a-442e-bacb-37f2ca3b028c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:55:56.949
19707b9e-2b4c-4497-91a7-6a287a58734d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:56:00.901
f2efc70b-f8cf-4cb3-acce-365645d4095f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:57:00.948
ed402cb1-6c29-465e-92ba-c16e65ae3058	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:57:04.899
e063c125-5bff-405b-94c2-f6854ff36203	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:58:04.948
bd3f0916-3eb1-4b58-bde8-9b90da143127	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:58:08.9
6efbb045-1a14-4481-92d9-2f71aa485986	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-21 23:59:08.948
10f69873-2137-4df2-a556-285b8d7d8b7f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-21 23:59:12.899
eb316a4b-aef6-49d9-9e8b-695950ddc938	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:00:12.948
e4d8e5cc-1e87-4668-b4a2-cc295f0728b1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:00:16.906
64978a53-1569-4e5a-9a9b-1f5c802fae6b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:01:16.949
d6e067ad-3efd-4c98-8e0f-969b93ee1f3a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:01:20.91
0b8d0f84-f765-400e-b38f-980216e7b0f1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:02:20.946
57facf10-1a90-4043-b476-b1eeb27034f5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:02:24.899
6445b985-864d-46db-ba51-702383013aef	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:03:24.966
fa0561a2-8832-4e0c-a935-5d8904266a67	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:03:28.946
71cce81e-49d7-414a-9da7-59fa50b32be0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:04:28.986
93bf3999-f41e-4446-82b4-9c7731c9a0db	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:04:32.916
ea2b5e2d-d00b-4853-a8d3-e09d9358a15e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:05:32.954
4c4e12c4-6335-4686-a745-7d25c7c09d08	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:05:36.901
e0bc722f-7d0e-4cf0-8b86-3307d8347e75	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:06:36.949
da6feec0-8444-4c76-8272-b1b4a96fe1f5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:06:40.901
f9917ec1-3325-4992-9444-f7cfec3d9694	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:07:40.948
f2160d87-549f-431e-ac23-de55034cfd72	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:07:44.899
bf094f9e-b6aa-46f5-a58b-80b7a260a679	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:08:44.948
944bce9b-02ba-4405-9746-fed3cdd11d6e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:08:48.9
3118fae7-ee86-4ff2-be98-a20f382885cd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:09:48.949
c1c09568-7fbe-4151-aecc-2a7948548c1b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:09:52.899
361972c5-0cba-416e-9188-62d479b18ecc	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:10:52.95
431e8662-7df6-4b46-8cce-21833a0a3f90	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:10:56.909
e32923e8-514e-4fef-add4-a78602f9d1e6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:11:56.948
386b42ac-1ab6-4520-87e1-30219764aa84	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:12:00.9
7db296ba-1ebb-4867-86cd-a1dfd1751916	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:13:00.948
02728290-7b21-4335-833b-0d56d4017d81	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:13:04.9
b120558a-dd11-4e11-8197-5df4c5d6d3a5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:14:04.949
c5c7845c-0e5d-4a13-a894-9fb979307a8e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:14:08.9
0a25fcdc-b626-4549-a8ce-a566a3f866fa	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:15:08.948
41447e1c-f3ea-45ad-8e0e-b8d15dec7231	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:15:12.911
97d77e4d-453b-48bc-8aeb-cda29018eec8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:16:12.948
7eb1805f-7bab-419b-a22a-236a946196ad	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:16:16.899
f76dbc12-d953-4bdf-9426-cdacdf89acfe	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:17:16.948
aaaf6652-316c-4fc8-924f-f7521707a5af	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:17:20.9
7e1b0812-8c34-40d6-a3a0-96f8f88d0c33	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:18:20.948
8b8991ff-04c0-4814-adfb-5fbf7ae342aa	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:18:24.9
1ccae9d4-1cd0-48f6-b153-21dfbe31f81f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:19:24.949
87378e93-5fcd-4ecc-8002-cb785ae02060	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:19:28.899
16101f6e-36b4-419c-8930-ab5c7f99dc2c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:20:28.949
dccebbd1-250c-4904-9a8c-a02fc07dca2a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:20:32.901
1fbb13e6-5e6a-401c-9128-fcf8310059d6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:21:32.948
98b64d0d-7373-41c8-8173-c1bdc2c3b191	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:21:36.902
203180d6-bebd-4809-9755-862f82f818a2	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:22:36.948
f1eb38f6-feea-4d96-8b50-a2bab6224adf	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:22:40.9
6122f0b0-6034-4fed-871e-2c5aa9018d08	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:23:40.949
a7bb41e0-a07f-4dcd-b760-9e9b148a5bca	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:23:44.912
ace90741-50c4-4edd-935a-214bf1bc79b5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:24:44.915
95851acf-e631-4f90-ab6a-44ba400fbe40	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:24:48.898
23b22b73-338b-4ad2-9e87-08f8cccddc7d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:25:48.948
b6a258e0-68c8-41c5-9924-d586552ae9ae	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:25:52.899
ab720d60-500f-4ad8-91d9-1bb1650365ba	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:26:52.948
9687e681-6379-4e4d-8c42-21995e34f87a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:26:56.912
ffc1d3f9-cdc8-4cee-86b1-9bb25794b209	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:27:56.948
26c75415-b57b-4c8f-b002-af345507bc52	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:28:00.898
3a5fcbc3-8ade-4456-9572-bf9455a9324c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:29:00.948
6f60441f-2f6e-41a5-a983-04d4bbb76874	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:29:04.906
ccbeec56-6997-45be-abe0-3a2174617022	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:30:04.953
45bab534-325a-4720-9d33-97f43aa07eaa	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:30:08.899
69756b50-5dc0-4933-8b87-3edb7943b343	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:31:08.948
b0876e0c-d91d-45be-94fc-7198664fa363	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:31:12.899
1f211971-594c-4da3-b912-1853a8bdfb53	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:32:12.948
edf74b91-a73e-41dc-b6fb-0635d3cf56cb	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:32:16.9
22ec66a5-e3fa-47fd-8bdc-74f1d1c5f4cd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:33:16.948
f686bc56-b981-4409-9228-3adcf65abdcc	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:33:20.899
8a100317-8da1-403a-b957-38506621cd51	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:34:20.948
a5d2ec06-588e-4521-9f66-3c8146b8a25f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:34:24.899
18985c1d-c70c-420d-91ec-9eb015a4cc87	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:35:24.948
396a66a0-f4e1-4e89-9759-d5b0b424c23b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:35:28.899
e6a44cd9-0dfc-4b56-becd-dd09647fec87	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:36:28.949
25e4a8c1-689e-41fe-bf2a-43969f7a55b8	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:36:32.899
dd730611-406a-4315-817e-d818f750cb68	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:37:32.948
0bd57b2e-ef7a-45cb-a6b5-04e09753db77	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:37:36.899
a7aae584-4c76-4a7b-a288-d5d61de6b4dd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:38:36.948
185fa429-c1b3-4428-ae18-c377e03c3f86	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:38:40.903
c5d4f543-6356-48b8-8a03-efc3689151a1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:39:40.949
9c91bb4d-c5f8-429c-8f3c-d17eb9a0c589	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:39:44.909
4dc2a88a-cf6b-4702-8673-f51d1b74bc2f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:40:44.957
72108bfa-f364-4540-922f-293c85a25840	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:40:48.899
ba7b6323-29fa-4007-88a4-73e6ea469d16	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:41:48.949
bf8fc43e-58fb-4e82-9f0b-543a928606fd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:41:52.907
333a1a57-6ef3-48c5-9fb7-a6dd61ede4dd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:42:52.948
edc74bd2-037b-4b14-a081-fd13b5be8ab0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:42:56.911
5a7aff25-eb29-4d75-9055-4d8c9d1f9b23	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:43:56.948
64c1dc6b-aa32-43ee-84db-c73b1c966d94	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:44:00.899
80f16a22-c370-4c19-8003-673ce4247b2f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:45:00.949
270ebdc1-e689-41c4-bc16-665070e29174	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:45:04.899
e50c1f0b-c37c-4ba3-ad97-269ceeab522b	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:46:04.948
a92c0203-0c74-4c1c-a4b1-831f28ab171a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:46:08.899
360c955b-adaf-41a6-a220-188246589f3c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:47:08.949
47099651-2f94-4909-b720-1aeb7dcb2abf	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:47:12.901
77d2f7cd-60e8-4934-b76d-379c4db60b46	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:48:12.95
213f93f5-345e-4d00-90d5-4454ead67e06	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:48:16.902
fc5e246c-8917-4ed1-850e-f994ce9e1fed	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:49:16.948
dd2e9df0-25b4-456d-86b8-5a17786b146a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:49:19.96
266c10cd-8a68-46f8-bff0-1033c90650e5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:50:20.011
fc27a8d4-8658-4502-bff5-58a00eb88bd7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:50:23.033
9413859e-2be2-4145-96d7-cf0be8307577	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:51:23.079
e9f2b1e4-ffd1-40a2-9b4d-2ba6a1d2d7c9	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:51:26.128
ced518a9-4967-4744-b0dc-2d6f2c027346	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:52:26.137
3bc8d26a-bf5c-4bad-9401-a3192e6dbf88	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:52:29.191
9c4191b5-7839-4d8d-a5d8-804a13329dbf	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:53:29.201
0229ceaf-54f7-4c12-9885-c621fd58e486	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:53:32.214
878abe8e-d8fe-47be-9c4e-7acb0979d51c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:54:12.653
42c1c18d-8d67-4487-bfb0-2de6be3997cd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:54:18.683
9e218ce7-5476-420a-83e1-57bf1d0ef33d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:54:30.037
fbaecad6-8d2b-4795-9fe8-7656503d205d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:54:42.331
47153536-ab17-4c49-ab59-913ec2007186	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:55:42.116
b76489ac-502f-4aad-97d6-0129fbedc5b5	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:55:45.129
ccce4883-94f2-4cb6-bdaf-b75cf355530e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:56:32.01
1c01c14a-378f-4f73-aa15-eaaf3e93b6c1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:56:47.239
a9674ed8-0648-4524-9c09-9655d06d6999	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:57:47.08
e97d90b5-71c7-494a-805b-96571c67d672	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:57:50.094
f41a8fec-2765-404f-8642-8c2663c30b5c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:58:50.14
eea4890f-3dac-40e8-bf31-b8e83e8fd5b3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:58:53.161
e245b93b-38a3-4c52-ba1a-d2c2b69cc77e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 00:59:53.148
8a4d2425-f072-4901-aa97-fa50b63fc805	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 00:59:56.91
29ff5112-ea33-4341-914c-f9830fefc99f	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:00:56.949
145ea499-fe08-4a6d-bec6-166876027b35	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:01:00.909
e6a5c150-9fb5-4224-b8ee-25cd7570cc2e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:02:00.949
aaf61dbc-a5cb-4587-9286-b7dfff936549	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:02:04.909
41df2d61-08ec-4ad0-b1f1-5208ba353e8e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:03:04.95
6939cb18-07ee-4666-a02b-7f0b95936500	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:03:08.902
b20414cc-fc6a-4589-b4d4-6bac28e95d2d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:04:08.949
bfd0037a-ac78-4a57-aacd-4f4f01f7bda3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:04:12.901
d830361b-f259-4ecb-a10c-0e622c0f8abb	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:05:12.948
8d8942b4-054f-4526-bdb6-f6b7bc2e2f78	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:05:16.9
1f2a8627-7518-40fc-b1f2-2477a38e1387	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:06:16.949
7431c592-4e6c-43a4-a9fa-396835cbb672	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:06:20.901
73ede870-9e73-4a83-b556-a06085e1d374	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:07:20.948
b9e55e02-8387-4f72-861b-90b4c4229822	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:07:24.901
85fed30a-aa30-43fc-af9e-8a50baabbf0a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:08:24.949
b5f4b9a8-283e-4ccf-acb2-e3d14a8bf5e3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:08:28.902
b6097a6c-24b2-4571-87fb-c1fe997ed7c3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:09:28.948
5fe36243-45c7-43db-8108-fc520394f5f6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:09:32.901
c7868aad-5306-411e-8b38-33e05412eeb4	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:10:32.948
c6d9b3d9-3585-44da-b489-d4951787d3f9	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:10:36.901
830c6d10-2d51-4464-8f6a-eb0a796df627	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:11:36.948
db49d814-2f29-4d1d-a2f4-ce96a129546e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:11:40.901
5305d603-21c0-4776-8284-d8f263769771	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:12:40.948
22b40f50-9800-46f9-89fc-cbcd27e1fa06	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:12:44.91
9f4ea3b3-2fe2-44f3-a5f3-f8c038eb2194	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:13:44.949
6ee202c8-27a6-448b-b7d5-e7c3e34b3d67	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:13:48.9
86700636-93b1-4c43-91f6-451dfcabba99	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:14:48.948
87cb3b9b-88b7-49d7-b170-e37c7f5a69f0	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:14:52.899
dfd8b29e-fc81-436c-850c-58907d05ceda	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:15:52.948
95c0eed7-fc6d-4afd-9bc6-3ef655c91864	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:15:56.903
2067fbfc-5b00-4ddd-8727-4de974628eb6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:16:56.943
735ac2b8-ebcd-4510-bb97-dfb8d3fb0694	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:17:00.904
b38e17ec-4802-4ba8-b15f-9638e2b7e61c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:18:00.948
bd018b70-c487-4dce-88dc-ace11d8b4824	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:18:04.903
5357d8bb-939d-47dc-8cba-ef6c309c9939	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:19:04.948
be6a12e3-14a8-409d-8fed-4451d5efc973	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:19:08.908
b387e6dc-27c9-4d61-a9af-894b4da98f4c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:20:08.948
d3d2554e-c071-4171-a4a7-f215eeaab65e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:20:12.905
fd8d6b93-aaf3-4f4c-9950-08f956b124f1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:21:12.952
c39b5636-2fa8-4365-a5de-abbe80f6689e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:21:16.905
358de0d3-3584-437b-9a11-aff4be0c0335	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:22:16.949
8fdbd581-1480-4026-82a0-f2f5553aa061	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:22:20.902
439fac0c-42ff-4b25-a843-f2cfc89d2af3	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:23:20.949
13c5c1a8-a91d-43fd-b98b-b5def019220e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:23:24.913
603d2160-670f-44ea-a726-0a12615ea319	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:24:24.94
92b19bae-6797-401f-82ef-dbfc5a5656f2	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:24:28.903
428871d2-3ef7-492c-a453-7b8cd3e8b29d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:25:28.949
543ddfb4-7920-4a8e-b00d-8d0dda5bd798	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:25:32.904
aa5c10c0-7765-412d-9da3-64d5e99a45cd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:26:32.95
1676ffed-7b03-4288-81a7-70f33de175f6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:26:36.908
76c940c1-79c7-411d-9bda-450cd8cdfc2c	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:27:36.948
c887d612-1a06-41a0-8c73-81be60140f61	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:27:40.906
8f4bf789-6f78-4e5f-951e-2e7027a3de6e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:28:40.956
067eb1b8-3c7d-482e-8623-f406b6ee1c87	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:28:44.9
daed2a5d-f54e-4b53-af28-0849032c15af	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:29:44.948
b50eb471-526d-4ec6-9300-6d133a423d55	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:29:48.9
fc04ca46-dad5-49c8-8735-306b7e711fd1	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:30:48.948
8d7038f4-41d5-4c20-90cf-18c9291ee887	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:30:52.908
13bb3a7b-b7aa-48fd-b013-2272442a65c6	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:31:52.949
cc4fcc98-f0fc-4a61-93d3-a7e8846229b7	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:31:56.905
75fd66ef-4ae9-47e5-aa12-f3e464be392d	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:32:56.949
83fb7a61-1c65-48b8-8639-73e44e056684	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:33:00.907
79a16123-2a16-425f-ace3-a4355e67df4e	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:34:00.949
6297c062-0b2c-4768-90eb-bf4176de1058	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:34:04.906
036ed5f6-da77-4c00-a2b6-8eb256f3a638	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:35:04.949
1b5fe2da-f6e2-4930-af8a-ff4efb4018b9	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:35:08.905
9f28b6e6-5b7d-4956-9816-8974a4b94288	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:36:08.901
50594174-bb64-4f2e-8cca-e1edec1613fd	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:36:12.901
4f200155-0517-4140-97a1-0bd73c06f477	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:37:12.948
d42d6c5f-6425-49de-bd07-ce791c0f38ea	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:37:16.903
6809f5d4-c9d0-40fa-be3c-757ccc50e45a	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:38:16.95
9c67b270-bb66-4f5c-a02d-de5eff4184eb	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:join	\N	\N	se unió a la sesión	{"participants": [{"host": false, "status": "online", "userId": "diego", "userName": "diego toledo"}]}	2026-09-22 01:38:20.901
a5964bdc-18b1-4930-b175-bc238ca8a602	0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	diego	diego toledo	session:leave	\N	\N	salió de la sesión	{"participants": []}	2026-09-22 01:39:20.948
\.


--
-- Data for Name: uml_collaboration_sessions; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.uml_collaboration_sessions (id, project_id, invite_code, created_by, invite_role, active, model_snapshot, created_at, updated_at) FROM stdin;
af0cfdec-1ee5-422a-af97-1b8af17faad1	uml-1789755617271-r3lvw1	8AFB8C1A91	admin	EDITOR	t	{"title": "hospital", "format": "uml-class-diagram", "classes": [{"x": 528, "y": 521, "id": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "name": "agenda", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_71038E27_B913_43c9_A7C6_6063DD4687F5", "name": "fecha", "type": "Date", "isPrimaryKey": false}, {"id": "EAID_6E20F9EB_A52E_4121_8AE0_F3B4BEDF7CB2", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_FE074A5F_B866_4aba_951D_44590C6792CC", "name": "observaciones", "type": "String", "isPrimaryKey": false}, {"id": "EAID_0A3F7DA1_ED9D_4a26_90A0_0F8B184435D9", "name": "ubicacion", "type": "DateTime", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 875, "y": 154, "id": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "name": "profesional", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_5AFA1C2B_57BF_4152_8F9C_E71BA4CE3DBC", "name": "email", "type": "String", "isPrimaryKey": false}, {"id": "EAID_844531DD_9748_4a17_AF50_BEAE7DE4C3D5", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_43CF2DFC_251A_4e25_A959_CFAA914079E6", "name": "matricula", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_530D0703_75D1_441b_BCE3_B3C937F3B8AE", "name": "nombre", "type": "String", "isPrimaryKey": false}, {"id": "EAID_FAD8A1A6_2F9C_431d_9F50_C3A837628FBB", "name": "telefono", "type": "Integer", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 118, "y": 114, "id": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "name": "turno", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_203C8397_6901_4dd9_805D_4CB1ADD221AB", "name": "estado", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_AB348A87_2BE8_4e8f_9B2A_AB76992F6046", "name": "fechahora", "type": "DateTime", "isPrimaryKey": false}, {"id": "EAID_229824FC_E1D4_48c6_9C8A_CCA6EFB98243", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_532DF1F8_1E48_4ae1_8EB4_11F430E3BA0C", "name": "motivo", "type": "String", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}], "version": 2, "relations": [{"id": "EAID_4A77D5EF_D5E1_474a_BAF2_8F75A3B540FF", "type": "composition", "label": "posee", "sourceId": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "targetId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "sourceMultiplicity": "1..*", "targetMultiplicity": "1"}, {"id": "EAID_693B2E4E_E082_4654_83A3_C343B40804D8", "type": "association", "label": "atendido por", "sourceId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "targetId": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}], "updatedAt": "2026-09-21T14:44:34.157Z"}	2026-09-21 18:23:16.316288	2026-09-21 18:43:38.748492
07573a37-b77a-4dba-a0fa-ad5164d09f4b	uml-1789755617271-r3lvw1	01C64AA9BD	admin	EDITOR	t	{"title": "hospital", "format": "uml-class-diagram", "classes": [{"x": 528, "y": 521, "id": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "name": "agenda", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_71038E27_B913_43c9_A7C6_6063DD4687F5", "name": "fecha", "type": "Date", "isPrimaryKey": false}, {"id": "EAID_6E20F9EB_A52E_4121_8AE0_F3B4BEDF7CB2", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_FE074A5F_B866_4aba_951D_44590C6792CC", "name": "observaciones", "type": "String", "isPrimaryKey": false}, {"id": "EAID_0A3F7DA1_ED9D_4a26_90A0_0F8B184435D9", "name": "ubicacion", "type": "DateTime", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 875, "y": 154, "id": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "name": "profesional", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_5AFA1C2B_57BF_4152_8F9C_E71BA4CE3DBC", "name": "email", "type": "String", "isPrimaryKey": false}, {"id": "EAID_844531DD_9748_4a17_AF50_BEAE7DE4C3D5", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_43CF2DFC_251A_4e25_A959_CFAA914079E6", "name": "matricula", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_530D0703_75D1_441b_BCE3_B3C937F3B8AE", "name": "nombre", "type": "String", "isPrimaryKey": false}, {"id": "EAID_FAD8A1A6_2F9C_431d_9F50_C3A837628FBB", "name": "telefono", "type": "Integer", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 118, "y": 114, "id": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "name": "turno", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_203C8397_6901_4dd9_805D_4CB1ADD221AB", "name": "estado", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_AB348A87_2BE8_4e8f_9B2A_AB76992F6046", "name": "fechahora", "type": "DateTime", "isPrimaryKey": false}, {"id": "EAID_229824FC_E1D4_48c6_9C8A_CCA6EFB98243", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_532DF1F8_1E48_4ae1_8EB4_11F430E3BA0C", "name": "motivo", "type": "String", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}], "version": 2, "relations": [{"id": "EAID_4A77D5EF_D5E1_474a_BAF2_8F75A3B540FF", "type": "composition", "label": "posee", "sourceId": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "targetId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "sourceMultiplicity": "1..*", "targetMultiplicity": "1"}, {"id": "EAID_693B2E4E_E082_4654_83A3_C343B40804D8", "type": "association", "label": "atendido por", "sourceId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "targetId": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}], "updatedAt": "2026-09-21T14:44:34.157Z"}	2026-09-21 18:23:16.316288	2026-09-21 18:23:16.318156
0f6742f7-114a-4730-b936-86ad1f84caff	uml-1790031159041-2hgkwf	E5F54B5232	cu4-audit-20260921@arqnova.test	EDITOR	t	{"title": "Validación CU7 actividad", "format": "uml-class-diagram", "classes": [{"x": 70, "y": 70, "id": "cliente", "name": "Cliente", "color": "#1e3a8a", "methods": [], "attributes": [], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 420, "y": 70, "id": "facturacu7", "name": "FacturaCU7", "color": "#2563eb", "methods": [], "attributes": [], "stereotype": "Entidad", "description": "", "responsibilities": []}], "version": 2, "relations": [], "updatedAt": "2026-09-21T22:57:03.171271062"}	2026-09-21 22:52:52.084515	2026-09-21 22:57:17.84431
0eb6366d-2528-41de-a700-177473814e5b	uml-1790012248673-yl3fkr	7BCD43B186	alison	EDITOR	t	{"title": "hospital", "format": "uml-class-diagram", "classes": [{"x": 660, "y": 50, "id": "turno", "name": "Turno", "color": "#2563eb", "methods": [], "attributes": [{"id": "attr-1790012419815-hahx8", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-6nxbz", "name": "FechaHora", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-qsi2h", "name": "Estado", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-6g00i", "name": "motivo", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 748, "y": 327, "id": "profesional", "name": "Profesional", "color": "#7c3aed", "methods": [], "attributes": [{"id": "attr-1790012419815-r14pd", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-kdy1p", "name": "nombre", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-69s5s", "name": "matricula", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-gtn11", "name": "telefono", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-1nipw", "name": "email", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 753, "y": 666, "id": "agenda", "name": "Agenda", "color": "#b45309", "methods": [], "attributes": [{"id": "attr-1790012419815-rn4un", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-8ofbz", "name": "Fecha", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-aq4qw", "name": "Ubicacion", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-0hln2", "name": "observaciones", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 86, "y": 194, "id": "paciente", "name": "Paciente", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "attr-1790012419815-upjxp", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-2yrw9", "name": "nombre", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-nx9j6", "name": "Documento", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-cxpnq", "name": "Telefono", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}], "version": 2, "relations": [{"id": "rel-1790012419815-9yipg", "type": "association", "label": "reserva", "sourceId": "paciente", "targetId": "turno", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1790012419815-zgp9v", "type": "association", "label": "atendido por", "sourceId": "turno", "targetId": "profesional", "sourceMultiplicity": "0..*", "targetMultiplicity": "1"}, {"id": "rel-1790012419815-pg2v8", "type": "composition", "label": "posee", "sourceId": "profesional", "targetId": "agenda", "sourceMultiplicity": "1", "targetMultiplicity": "1..*"}], "updatedAt": "2026-09-21T20:13:47.092853444"}	2026-09-21 17:39:06.372587	2026-09-21 20:13:47.093486
\.


--
-- Data for Name: uml_diagrams; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.uml_diagrams (id, project_id, model, model_version, created_at, updated_at) FROM stdin;
diagram-uml-1790012273586-5r87k9	uml-1790012273586-5r87k9	{"title": "Modelo conceptual del sistema", "format": "uml-class-diagram", "classes": [{"x": 152, "y": 141, "id": "usuario", "name": "Usuario", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "usuario-attr-1", "name": "id", "type": "String"}, {"id": "usuario-attr-2", "name": "username", "type": "String"}, {"id": "usuario-attr-3", "name": "nombreCompleto", "type": "String"}, {"id": "usuario-attr-4", "name": "rol", "type": "RolUsuario"}, {"id": "usuario-attr-5", "name": "departamento", "type": "String"}, {"id": "usuario-attr-6", "name": "fechaCreacion", "type": "DateTime"}], "stereotype": "Entidad", "description": "Representa una persona que utiliza el sistema", "responsibilities": ["Representa una persona que utiliza el sistema"]}, {"x": 833, "y": 182, "id": "departamento", "name": "Departamento", "color": "#2563eb", "methods": [], "attributes": [{"id": "departamento-attr-1", "name": "id", "type": "String"}, {"id": "departamento-attr-2", "name": "nombre", "type": "String"}, {"id": "departamento-attr-3", "name": "descripcion", "type": "String"}, {"id": "departamento-attr-4", "name": "activo", "type": "Boolean"}], "stereotype": "Entidad", "description": "Agrupa usuarios y solicitudes por área organizacional", "responsibilities": ["Agrupa usuarios y solicitudes por área organizacional"]}, {"x": 767, "y": 447, "id": "solicitud", "name": "Solicitud", "color": "#7c3aed", "methods": [], "attributes": [{"id": "solicitud-attr-1", "name": "id", "type": "String"}, {"id": "solicitud-attr-2", "name": "codigoSeguimiento", "type": "String"}, {"id": "solicitud-attr-3", "name": "titulo", "type": "String"}, {"id": "solicitud-attr-4", "name": "descripcion", "type": "String"}, {"id": "solicitud-attr-5", "name": "prioridad", "type": "Prioridad"}, {"id": "solicitud-attr-8", "name": "fechaLimiteAtencion", "type": "DateTime"}], "stereotype": "Entidad principal", "description": "Concentra la información principal de una gestión", "responsibilities": ["Concentra la información principal de una gestión"]}, {"x": 1199, "y": 187, "id": "evento", "name": "EventoHistorial", "color": "#b45309", "methods": [], "attributes": [{"id": "evento-attr-1", "name": "estadoAnterior", "type": "EstadoSolicitud"}, {"id": "evento-attr-2", "name": "estadoNuevo", "type": "EstadoSolicitud"}, {"id": "evento-attr-3", "name": "usuario", "type": "String"}, {"id": "evento-attr-4", "name": "comentario", "type": "String"}, {"id": "evento-attr-5", "name": "fecha", "type": "DateTime"}], "stereotype": "Objeto de valor", "description": "Registra un cambio auditable sobre una solicitud", "responsibilities": ["Registra un cambio auditable sobre una solicitud"]}, {"x": 88, "y": 390, "id": "documento", "name": "Documento", "color": "#0369a1", "methods": [], "attributes": [{"id": "documento-attr-1", "name": "id", "type": "String"}, {"id": "documento-attr-2", "name": "nombre", "type": "String"}, {"id": "documento-attr-3", "name": "descripcion", "type": "String"}, {"id": "documento-attr-4", "name": "tipo", "type": "TipoDocumento"}, {"id": "documento-attr-5", "name": "estado", "type": "EstadoDocumento"}, {"id": "documento-attr-6", "name": "versionActual", "type": "Integer"}, {"id": "documento-attr-7", "name": "fechaCreacion", "type": "DateTime"}], "stereotype": "Entidad", "description": "Representa un documento asociado a una solicitud", "responsibilities": ["Representa un documento asociado a una solicitud"]}, {"x": 513, "y": 680, "id": "version", "name": "VersionDocumento", "color": "#475569", "methods": [], "attributes": [{"id": "version-attr-1", "name": "version", "type": "Integer"}, {"id": "version-attr-2", "name": "nombreOriginal", "type": "String"}, {"id": "version-attr-3", "name": "tipoContenido", "type": "String"}, {"id": "version-attr-4", "name": "tamanoBytes", "type": "Long"}, {"id": "version-attr-5", "name": "subidoPor", "type": "String"}, {"id": "version-attr-6", "name": "fechaSubida", "type": "DateTime"}], "stereotype": "Objeto de valor", "description": "Conserva una versión histórica del documento", "responsibilities": ["Conserva una versión histórica del documento"]}, {"x": 1161, "y": 606, "id": "archivo", "name": "ArchivoAdjunto", "color": "#be123c", "methods": [], "attributes": [{"id": "archivo-attr-1", "name": "id", "type": "String"}, {"id": "archivo-attr-2", "name": "nombreOriginal", "type": "String"}, {"id": "archivo-attr-3", "name": "tipoContenido", "type": "String"}, {"id": "archivo-attr-4", "name": "tamanoBytes", "type": "Long"}, {"id": "archivo-attr-5", "name": "subidoPor", "type": "String"}, {"id": "archivo-attr-6", "name": "fechaSubida", "type": "DateTime"}], "stereotype": "Objeto de valor", "description": "Describe un archivo adjunto a una solicitud", "responsibilities": ["Describe un archivo adjunto a una solicitud"]}, {"x": 553, "y": 12, "id": "dispositivo", "name": "DispositivoUsuario", "color": "#4f46e5", "methods": [], "attributes": [{"id": "dispositivo-attr-1", "name": "id", "type": "String"}, {"id": "dispositivo-attr-2", "name": "token", "type": "String"}, {"id": "dispositivo-attr-3", "name": "plataforma", "type": "String"}, {"id": "dispositivo-attr-4", "name": "fechaRegistro", "type": "DateTime"}], "stereotype": "Entidad", "description": "Identifica un dispositivo habilitado para notificaciones", "responsibilities": ["Identifica un dispositivo habilitado para notificaciones"]}, {"x": 70, "y": 730, "id": "casa", "name": "casa", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "casa-attr-1", "name": "id", "type": "String"}], "stereotype": "Entidad", "description": "Describir responsabilidad de la clase", "responsibilities": ["Describir responsabilidad de la clase"]}], "version": 2, "relations": [{"id": "r1", "type": "association", "label": "agrupa", "sourceId": "departamento", "targetId": "usuario", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "r2", "type": "association", "label": "crea", "sourceId": "usuario", "targetId": "solicitud", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "r3", "type": "association", "label": "gestiona", "sourceId": "departamento", "targetId": "solicitud", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "r4", "type": "composition", "label": "registra", "sourceId": "solicitud", "targetId": "evento", "sourceMultiplicity": "1", "targetMultiplicity": "1..*"}, {"id": "r5", "type": "aggregation", "label": "contiene", "sourceId": "solicitud", "targetId": "documento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "r6", "type": "composition", "label": "versiona", "sourceId": "documento", "targetId": "version", "sourceMultiplicity": "1", "targetMultiplicity": "1..*"}, {"id": "r7", "type": "composition", "label": "adjunta", "sourceId": "solicitud", "targetId": "archivo", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "r8", "type": "composition", "label": "registra", "sourceId": "usuario", "targetId": "dispositivo", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1789506595584", "type": "association", "label": "", "sourceId": "usuario", "targetId": "departamento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1789506619456", "type": "association", "label": "pruebaaa", "sourceId": "usuario", "targetId": "departamento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1789506631472", "type": "association", "label": "", "sourceId": "usuario", "targetId": "departamento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1789506632844", "type": "association", "label": "", "sourceId": "usuario", "targetId": "departamento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1789507562819", "type": "association", "label": "carga", "sourceId": "usuario", "targetId": "departamento", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}], "updatedAt": "2026-09-16T17:01:46.294Z"}	1	2026-09-21 17:37:53.61923	2026-09-21 17:37:53.61923
diagram-uml-1790031159041-2hgkwf	uml-1790031159041-2hgkwf	{"title": "Validación CU7 actividad", "format": "uml-class-diagram", "classes": [{"x": 70, "y": 70, "id": "cliente", "name": "Cliente", "color": "#1e3a8a", "methods": [], "attributes": [], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 420, "y": 70, "id": "facturacu7", "name": "FacturaCU7", "color": "#2563eb", "methods": [], "attributes": [], "stereotype": "Entidad", "description": "", "responsibilities": []}], "version": 2, "relations": [], "updatedAt": "2026-09-21T22:57:03.171271062"}	2	2026-09-21 22:52:59.980033	2026-09-21 22:57:03.176014
diagram-uml-1789755617271-r3lvw1	uml-1789755617271-r3lvw1	{"title": "hospital", "format": "uml-class-diagram", "classes": [{"x": 528, "y": 521, "id": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "name": "agenda", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_71038E27_B913_43c9_A7C6_6063DD4687F5", "name": "fecha", "type": "Date", "isPrimaryKey": false}, {"id": "EAID_6E20F9EB_A52E_4121_8AE0_F3B4BEDF7CB2", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_FE074A5F_B866_4aba_951D_44590C6792CC", "name": "observaciones", "type": "String", "isPrimaryKey": false}, {"id": "EAID_0A3F7DA1_ED9D_4a26_90A0_0F8B184435D9", "name": "ubicacion", "type": "DateTime", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 875, "y": 154, "id": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "name": "profesional", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_5AFA1C2B_57BF_4152_8F9C_E71BA4CE3DBC", "name": "email", "type": "String", "isPrimaryKey": false}, {"id": "EAID_844531DD_9748_4a17_AF50_BEAE7DE4C3D5", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_43CF2DFC_251A_4e25_A959_CFAA914079E6", "name": "matricula", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_530D0703_75D1_441b_BCE3_B3C937F3B8AE", "name": "nombre", "type": "String", "isPrimaryKey": false}, {"id": "EAID_FAD8A1A6_2F9C_431d_9F50_C3A837628FBB", "name": "telefono", "type": "Integer", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}, {"x": 118, "y": 114, "id": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "name": "turno", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "EAID_203C8397_6901_4dd9_805D_4CB1ADD221AB", "name": "estado", "type": "Integer", "isPrimaryKey": false}, {"id": "EAID_AB348A87_2BE8_4e8f_9B2A_AB76992F6046", "name": "fechahora", "type": "DateTime", "isPrimaryKey": false}, {"id": "EAID_229824FC_E1D4_48c6_9C8A_CCA6EFB98243", "name": "id", "type": "Integer", "isPrimaryKey": true}, {"id": "EAID_532DF1F8_1E48_4ae1_8EB4_11F430E3BA0C", "name": "motivo", "type": "String", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "", "responsibilities": []}], "version": 2, "relations": [{"id": "EAID_4A77D5EF_D5E1_474a_BAF2_8F75A3B540FF", "type": "composition", "label": "posee", "sourceId": "EAID_BE0449B6_4030_4ddd_B075_82FAA7D926C8", "targetId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "sourceMultiplicity": "1..*", "targetMultiplicity": "1"}, {"id": "EAID_693B2E4E_E082_4654_83A3_C343B40804D8", "type": "association", "label": "atendido por", "sourceId": "EAID_52D51035_C111_4cd2_99C6_EAD14B579DD9", "targetId": "EAID_15961A92_D57A_406b_86D9_43FD47208CF8", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}], "updatedAt": "2026-09-21T14:44:34.157Z"}	1	2026-09-21 18:23:06.513944	2026-09-21 18:23:06.513944
diagram-uml-1790012248673-yl3fkr	uml-1790012248673-yl3fkr	{"title": "hospital", "format": "uml-class-diagram", "classes": [{"x": 660, "y": 50, "id": "turno", "name": "Turno", "color": "#2563eb", "methods": [], "attributes": [{"id": "attr-1790012419815-hahx8", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-6nxbz", "name": "FechaHora", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-qsi2h", "name": "Estado", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-6g00i", "name": "motivo", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 748, "y": 327, "id": "profesional", "name": "Profesional", "color": "#7c3aed", "methods": [], "attributes": [{"id": "attr-1790012419815-r14pd", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-kdy1p", "name": "nombre", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-69s5s", "name": "matricula", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-gtn11", "name": "telefono", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-1nipw", "name": "email", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 753, "y": 666, "id": "agenda", "name": "Agenda", "color": "#b45309", "methods": [], "attributes": [{"id": "attr-1790012419815-rn4un", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-8ofbz", "name": "Fecha", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-aq4qw", "name": "Ubicacion", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-0hln2", "name": "observaciones", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}, {"x": 86, "y": 194, "id": "paciente", "name": "Paciente", "color": "#1e3a8a", "methods": [], "attributes": [{"id": "attr-1790012419815-upjxp", "name": "Id", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-2yrw9", "name": "nombre", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-nx9j6", "name": "Documento", "type": "", "isPrimaryKey": false}, {"id": "attr-1790012419815-cxpnq", "name": "Telefono", "type": "", "isPrimaryKey": false}], "stereotype": "Entidad", "description": "Reconocida desde imagen", "responsibilities": []}], "version": 2, "relations": [{"id": "rel-1790012419815-9yipg", "type": "association", "label": "reserva", "sourceId": "paciente", "targetId": "turno", "sourceMultiplicity": "1", "targetMultiplicity": "0..*"}, {"id": "rel-1790012419815-zgp9v", "type": "association", "label": "atendido por", "sourceId": "turno", "targetId": "profesional", "sourceMultiplicity": "0..*", "targetMultiplicity": "1"}, {"id": "rel-1790012419815-pg2v8", "type": "composition", "label": "posee", "sourceId": "profesional", "targetId": "agenda", "sourceMultiplicity": "1", "targetMultiplicity": "1..*"}], "updatedAt": "2026-09-21T20:13:47.092853444"}	108	2026-09-21 17:40:20.173568	2026-09-21 20:13:47.093527
\.


--
-- Data for Name: users; Type: TABLE DATA; Schema: public; Owner: -
--

COPY public.users (id, username, password_hash, full_name, role, department, avatar_url, enabled, created_at, updated_at) FROM stdin;
6aa99c4ebdbe4709c690dd81	admin	$2a$12$NbUmx5XGANgNe5ilGl8nP.Uao3CJU0uKVnQ3mVC7rgSpghyWp2RLe	Súper Administrador	ADMINISTRADOR	Sistemas	https://ui-avatars.com/api/?name=Súper+Administrador&background=random&color=fff&size=128	t	2026-09-15 19:28:14.548	2026-09-18 13:25:32.242743
f8dbfcd2-aa05-496c-94b6-1c06647ce7a0	alison	$2a$12$mCYBuSgX2JwtCnu9wSAvPOKrv54SIf/tRDGzbss0rwhoMfH5swArm	alison canaviri mamani	SOLICITANTE	Finanzas	\N	t	2026-09-21 06:59:13.012479	2026-09-21 06:59:13.016994
90f5d4f1-8e90-44a6-8a06-e3e69d018d39	diego	$2a$12$9c0osFzM/k3RO.8sJQHD/OErT0roUITdCNHtgCjmg6tL4/c9qO5SC	diego toledo	SOLICITANTE	Finanzas	\N	t	2026-09-21 17:36:28.835764	2026-09-21 17:36:28.839048
4e07e085-c35d-4b13-9a77-54812e173f82	cu4-audit-20260921@arqnova.test	$2a$12$BMU9FXO.V34/aQ3rMyzvN.MgFYWdvLUlQdh5V3Cutw0YumIWWLKFW	Auditor CU4	SOLICITANTE	Sistemas	https://ui-avatars.com/api/?name=Auditor+CU4&background=random&color=fff&bold=true&size=128	t	2026-09-21 19:38:12.01975	2026-09-21 19:38:12.021692
\.


--
-- Name: app_schema_check app_schema_check_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.app_schema_check
    ADD CONSTRAINT app_schema_check_pkey PRIMARY KEY (id);


--
-- Name: departments departments_name_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_name_key UNIQUE (name);


--
-- Name: departments departments_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.departments
    ADD CONSTRAINT departments_pkey PRIMARY KEY (id);


--
-- Name: flyway_schema_history flyway_schema_history_pk; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.flyway_schema_history
    ADD CONSTRAINT flyway_schema_history_pk PRIMARY KEY (installed_rank);


--
-- Name: project_members project_members_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_members
    ADD CONSTRAINT project_members_pkey PRIMARY KEY (project_id, user_id);


--
-- Name: projects projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.projects
    ADD CONSTRAINT projects_pkey PRIMARY KEY (id);


--
-- Name: uml_collaboration_activities uml_collaboration_activities_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_activities
    ADD CONSTRAINT uml_collaboration_activities_pkey PRIMARY KEY (event_id);


--
-- Name: uml_collaboration_sessions uml_collaboration_sessions_invite_code_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_sessions
    ADD CONSTRAINT uml_collaboration_sessions_invite_code_key UNIQUE (invite_code);


--
-- Name: uml_collaboration_sessions uml_collaboration_sessions_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_sessions
    ADD CONSTRAINT uml_collaboration_sessions_pkey PRIMARY KEY (id);


--
-- Name: uml_diagrams uml_diagrams_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_diagrams
    ADD CONSTRAINT uml_diagrams_pkey PRIMARY KEY (id);


--
-- Name: uml_diagrams uml_diagrams_project_id_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_diagrams
    ADD CONSTRAINT uml_diagrams_project_id_key UNIQUE (project_id);


--
-- Name: users users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_pkey PRIMARY KEY (id);


--
-- Name: users users_username_key; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.users
    ADD CONSTRAINT users_username_key UNIQUE (username);


--
-- Name: flyway_schema_history_s_idx; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX flyway_schema_history_s_idx ON public.flyway_schema_history USING btree (success);


--
-- Name: idx_departments_active_name; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_departments_active_name ON public.departments USING btree (active, name);


--
-- Name: idx_project_members_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_project_members_user_id ON public.project_members USING btree (user_id);


--
-- Name: idx_projects_owner_user_id; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_owner_user_id ON public.projects USING btree (owner_user_id);


--
-- Name: idx_projects_status; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_status ON public.projects USING btree (status);


--
-- Name: idx_projects_updated_at; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_projects_updated_at ON public.projects USING btree (updated_at);


--
-- Name: idx_uml_activity_project_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_activity_project_time ON public.uml_collaboration_activities USING btree (project_id, occurred_at DESC);


--
-- Name: idx_uml_activity_session_time; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_activity_session_time ON public.uml_collaboration_activities USING btree (session_id, occurred_at DESC);


--
-- Name: idx_uml_collaboration_active; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_collaboration_active ON public.uml_collaboration_sessions USING btree (active);


--
-- Name: idx_uml_collaboration_created_by; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_collaboration_created_by ON public.uml_collaboration_sessions USING btree (created_by);


--
-- Name: idx_uml_collaboration_invite; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_collaboration_invite ON public.uml_collaboration_sessions USING btree (invite_code);


--
-- Name: idx_uml_collaboration_project; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_uml_collaboration_project ON public.uml_collaboration_sessions USING btree (project_id);


--
-- Name: idx_users_enabled; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_enabled ON public.users USING btree (enabled);


--
-- Name: idx_users_role; Type: INDEX; Schema: public; Owner: -
--

CREATE INDEX idx_users_role ON public.users USING btree (role);


--
-- Name: project_members fk_project_members_project; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.project_members
    ADD CONSTRAINT fk_project_members_project FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: uml_diagrams fk_uml_diagrams_project; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_diagrams
    ADD CONSTRAINT fk_uml_diagrams_project FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: uml_collaboration_activities uml_collaboration_activities_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_activities
    ADD CONSTRAINT uml_collaboration_activities_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- Name: uml_collaboration_activities uml_collaboration_activities_session_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_activities
    ADD CONSTRAINT uml_collaboration_activities_session_id_fkey FOREIGN KEY (session_id) REFERENCES public.uml_collaboration_sessions(id) ON DELETE CASCADE;


--
-- Name: uml_collaboration_sessions uml_collaboration_sessions_project_id_fkey; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.uml_collaboration_sessions
    ADD CONSTRAINT uml_collaboration_sessions_project_id_fkey FOREIGN KEY (project_id) REFERENCES public.projects(id) ON DELETE CASCADE;


--
-- PostgreSQL database dump complete
--

\unrestrict eFx8ug5B9651dKdpcMN5UIdsejPsK89IHeFWwmPGzrNlTUub4XnAL2N5APqdTqH
