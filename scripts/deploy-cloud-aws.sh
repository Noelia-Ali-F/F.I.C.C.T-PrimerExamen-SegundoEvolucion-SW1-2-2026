#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
REMOTE_HOST="${REMOTE_HOST:-aws-mesa}"
REMOTE_DIR="${REMOTE_DIR:-/home/admin/arqnova}"
ENV_FILE="${ENV_FILE:-$ROOT_DIR/.env.aws}"

for command_name in ssh rsync scp docker npm; do
  if ! command -v "$command_name" >/dev/null 2>&1; then
    echo "Falta el comando requerido: $command_name"
    exit 1
  fi
done

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Falta $ENV_FILE. Copia .env.aws.example a .env.aws y ajusta valores."
  exit 1
fi

if grep -Eq '(^|=)(change_me|change_me_with_)|YOUR_AWS_HOST' "$ENV_FILE"; then
  echo "El archivo de entorno todavía contiene valores de ejemplo."
  exit 1
fi

echo "[1/5] Compilando artefactos locales..."
"$ROOT_DIR/scripts/build-local-artifacts.sh"

echo "[2/5] Creando directorio remoto..."
ssh "$REMOTE_HOST" "mkdir -p '$REMOTE_DIR'"

echo "[3/5] Sincronizando proyecto..."
rsync -az --delete \
  --exclude ".git" \
  --exclude ".env" \
  --exclude ".env.*" \
  --exclude "backups" \
  --exclude "backup" \
  --exclude "target" \
  --exclude "dist" \
  --exclude "node_modules" \
  --exclude ".angular" \
  --exclude ".dart_tool" \
  --exclude "build" \
  --exclude "uploads" \
  --exclude ".idea" \
  --exclude ".vscode" \
  --exclude ".codex" \
  --exclude ".agents" \
  "$ROOT_DIR/" "$REMOTE_HOST:$REMOTE_DIR/"

echo "[4/5] Copiando variables cloud..."
scp "$ENV_FILE" "$REMOTE_HOST:$REMOTE_DIR/.env"

echo "[5/5] Levantando Docker en remoto..."
ssh "$REMOTE_HOST" "cd '$REMOTE_DIR' && docker compose --env-file .env config --quiet && docker compose --env-file .env up --build -d && docker compose ps"

echo "Despliegue lanzado en $REMOTE_HOST"
