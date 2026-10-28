"""Offline PostgreSQL export -> DynamoDB. Never changes PostgreSQL or activates the app.

Requires boto3 only for import/verify. preflight uses the Python standard library.
The source contains password hashes and personal data: keep it outside Git.
"""
import argparse
import base64
import gzip
import hashlib
import json
from pathlib import Path
import uuid

KINDS = ("USER", "DEPARTMENT", "PROJECT", "MEMBER", "DIAGRAM", "SESSION", "ACTIVITY")
CHUNK_BYTES = 280 * 1024


def compound(*values):
    return base64.urlsafe_b64encode("\0".join(values).encode()).decode().rstrip("=")


def unique_key(namespace, value):
    return namespace + ":" + (value.lower() if namespace in ("username", "department-name", "invite") else value)


def document_id(kind, value):
    if kind == "MEMBER":
        return compound(value["id"]["projectId"], value["id"]["userId"])
    return value["eventId" if kind == "ACTIVITY" else "id"]


def encoded(value):
    raw = json.dumps(value, ensure_ascii=False, separators=(",", ":")).encode()
    if len(raw) > 32 * 1024 * 1024:
        raise ValueError("Un documento supera 32 MiB sin comprimir")
    result = gzip.compress(raw, mtime=0)
    # Leaves room for a future replacement in the same all-or-nothing transaction.
    if len(result) * 2 > 3_300_000:
        raise ValueError("Un documento no cabe en una transacción de actualización DynamoDB")
    return result


def records(source):
    if source.get("format") != "arqnova-dynamodb-export-v1":
        raise ValueError("Formato de exportación incorrecto")
    result = {}

    def add(kind, key, value):
        if (kind, key) in result:
            raise ValueError("Nombre o identificador duplicado en " + kind)
        encoded(value)
        result[kind, key] = value

    def unique(namespace, key, owner):
        add("UNIQUE", unique_key(namespace, key), {"owner": owner})

    for kind in KINDS:
        for value in source[kind]:
            key = document_id(kind, value)
            add(kind, key, value)
            if kind == "USER":
                unique("username", value["username"], key)
            elif kind == "DEPARTMENT":
                unique("department-name", value["nombre"], key)
            elif kind == "PROJECT":
                unique("project-name", compound(value["ownerUserId"], value["name"].lower()), key)
            elif kind == "DIAGRAM":
                unique("diagram-project", value["projectId"], key)
            elif kind == "SESSION":
                unique("invite", value["inviteCode"], key)
    for (kind, _), value in result.items():
        project = value["id"]["projectId"] if kind == "MEMBER" else value.get("projectId")
        if project is not None and ("PROJECT", project) not in result:
            raise ValueError("Referencia a proyecto inexistente en " + kind)
        if kind == "ACTIVITY" and ("SESSION", value["sessionId"]) not in result:
            raise ValueError("Referencia a sesión inexistente")
    return result


def key(kind, identity):
    return {"pk": {"S": kind}, "sk": {"S": identity}}


def read(client, table, kind, identity):
    head = client.get_item(TableName=table, Key=key(kind, identity), ConsistentRead=True).get("Item")
    if head is None:
        return None
    if "chunks" in head:
        parts = []
        for index in range(int(head["chunks"]["N"])):
            part = client.get_item(TableName=table, Key=key("BLOB:" + kind + ":" + identity, head["revision"]["S"] + ":" + str(index)), ConsistentRead=True)["Item"]
            parts.append(part["payload"]["B"])
        payload = b"".join(parts)
    else:
        payload = head["payload"]["B"]
    return json.loads(gzip.decompress(payload))


def identities(client, table, kind):
    request = dict(TableName=table, ConsistentRead=True, KeyConditionExpression="pk = :kind", ExpressionAttributeValues={":kind": {"S": kind}}, ProjectionExpression="sk")
    while True:
        response = client.query(**request)
        yield from (row["sk"]["S"] for row in response["Items"])
        if not response.get("LastEvaluatedKey"):
            return
        request["ExclusiveStartKey"] = response["LastEvaluatedKey"]


def check_target(client, table, expected, require_all):
    found = set()
    for kind in (*KINDS, "UNIQUE"):
        for identity in identities(client, table, kind):
            k = kind, identity
            if k not in expected or read(client, table, *k) != expected[k]:
                raise ValueError("El destino contiene datos distintos. Se canceló sin sobrescribirlos.")
            found.add(k)
    if require_all and found != expected.keys():
        raise ValueError("Faltan documentos en DynamoDB")
    return found


def write_new(client, table, kind, identity, value):
    payload = encoded(value)
    revision = str(uuid.uuid4())
    head = dict(key(kind, identity), revision={"S": revision})
    writes = []
    if len(payload) <= CHUNK_BYTES:
        head["payload"] = {"B": payload}
    else:
        chunks = [payload[i:i + CHUNK_BYTES] for i in range(0, len(payload), CHUNK_BYTES)]
        head["chunks"] = {"N": str(len(chunks))}
        for index, part in enumerate(chunks):
            item = dict(key("BLOB:" + kind + ":" + identity, revision + ":" + str(index)), payload={"B": part})
            writes.append({"Put": {"TableName": table, "Item": item}})
    writes.append({"Put": {"TableName": table, "Item": head, "ConditionExpression": "attribute_not_exists(pk)"}})
    client.transact_write_items(TransactItems=writes, ClientRequestToken=str(uuid.uuid4()))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("source", type=Path)
    parser.add_argument("--mode", choices=("preflight", "import", "verify"), default="preflight")
    parser.add_argument("--table", default="arqnova")
    parser.add_argument("--region", default="us-east-2")
    parser.add_argument("--endpoint", help="Only for local migration rehearsals")
    args = parser.parse_args()
    raw = args.source.read_bytes()
    source = json.loads(raw)
    expected = records(source)
    print("Source SHA256:", hashlib.sha256(raw).hexdigest())
    print("Collections:", json.dumps({k: len(source[k]) for k in KINDS}))
    if args.mode != "preflight":
        import boto3
        options = dict(region_name=args.region)
        if args.endpoint:
            from urllib.parse import urlparse
            if urlparse(args.endpoint).hostname not in ("localhost", "127.0.0.1", "dynamodb-local"):
                raise ValueError("Endpoint de prueba no permitido")
            options.update(endpoint_url=args.endpoint, aws_access_key_id="local", aws_secret_access_key="local")
        client = boto3.client("dynamodb", **options)
        schema = client.describe_table(TableName=args.table)["Table"]["KeySchema"]
        if {(x["AttributeName"], x["KeyType"]) for x in schema} != {("pk", "HASH"), ("sk", "RANGE")}:
            raise ValueError("Esquema de claves incorrecto")
        existing = check_target(client, args.table, expected, args.mode == "verify")
        if args.mode == "import":
            for (kind, identity), value in expected.items():
                if (kind, identity) not in existing:
                    write_new(client, args.table, kind, identity, value)
            check_target(client, args.table, expected, True)
    print("PASS:", args.mode, "(no se modificó PostgreSQL)")


if __name__ == "__main__":
    main()
