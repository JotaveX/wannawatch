#!/bin/sh
# Atualiza a VPS para a imagem mais nova do GHCR. Chamado pelo GitHub Actions por uma chave SSH que só pode rodar
# este script (authorized_keys com command="/opt/wannawatch/deploy.sh"); o token temporário do job (só leitura de
# pacotes, expira com o job) chega pela entrada padrão. À mão, sem pull: deploy.sh < /dev/null.
set -eu
cd /opt/wannawatch
token=$(cat)
if [ -n "$token" ]; then
  printf '%s' "$token" | docker login ghcr.io -u jotavex --password-stdin >/dev/null
  docker compose pull --quiet
  docker logout ghcr.io >/dev/null
fi
docker compose up -d --remove-orphans --wait --wait-timeout 300
docker image prune -f >/dev/null
docker compose ps --format '{{.Service}} {{.State}} {{.Status}}'
