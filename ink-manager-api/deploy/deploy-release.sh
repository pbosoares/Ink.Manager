#!/usr/bin/env bash
set -Eeuo pipefail
umask 077

sha="${1:-}"
if [[ ! "$sha" =~ ^[0-9a-f]{40}$ ]]; then
  echo "Invalid release identifier" >&2
  exit 2
fi

root=/opt/marcae
env_file="$root/.env"
archive="$root/incoming/$sha.tar.gz"
release="$root/releases/$sha"
test -s "$env_file" || { echo "Production environment file is missing" >&2; exit 3; }
test -s "$archive" || { echo "Release archive is missing" >&2; exit 3; }
command -v docker >/dev/null
command -v curl >/dev/null
command -v python3 >/dev/null

previous_release="$root"
if [[ -s "$root/.current-release" ]]; then
  previous_release="$(cat "$root/.current-release")"
fi
case "$previous_release" in
  "$root"|"$root"/releases/*) ;;
  *) echo "Invalid previous release path" >&2; exit 4 ;;
esac
previous_compose="$previous_release/compose.production.yml"
test -s "$previous_compose" || { echo "Previous compose file is missing" >&2; exit 4; }
previous_tag="$(sed -n 's/^INK_IMAGE_TAG=//p' "$env_file" | head -n 1)"
[[ "$previous_tag" =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Invalid current image tag" >&2; exit 4; }
docker image inspect "ink-manager:$previous_tag" >/dev/null
docker compose --project-name marcae --env-file "$env_file" -f "$previous_compose" \
  exec --no-TTY postgres pg_isready >/dev/null

mkdir -p "$root/releases"
if [[ -e "$release" ]]; then
  echo "Release directory already exists; refusing to overwrite" >&2
  exit 4
fi
mkdir -m 700 "$release"
tar -xzf "$archive" --strip-components=1 -C "$release"
test -s "$release/compose.production.yml"
test -s "$release/deploy/Caddyfile"
domain="$(sed -n 's/^INK_DOMAIN=//p' "$env_file" | head -n 1)"
[[ "$domain" =~ ^[A-Za-z0-9.-]+$ ]] || { echo "Invalid production domain" >&2; exit 4; }

compose=(docker compose --project-name marcae --env-file "$env_file" -f "$release/compose.production.yml")
"${compose[@]}" config --quiet

rollback() {
  status=$?
  if (( status != 0 )); then
    echo "Deployment failed; attempting rollback to $previous_tag" >&2
    INK_IMAGE_TAG="$previous_tag" docker compose --project-name marcae --env-file "$env_file" -f "$previous_compose" \
      up --detach --no-build --no-deps api proxy >&2 || true
  fi
  exit "$status"
}
trap rollback EXIT

INK_IMAGE_TAG="$sha" "${compose[@]}" up --detach --build --no-deps api proxy

healthy=false
for attempt in $(seq 1 36); do
  if body="$(curl --silent --show-error --fail --max-time 5 --resolve "$domain:443:127.0.0.1" "https://$domain/actuator/health" 2>/dev/null)" \
    && grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"' <<<"$body"; then
    healthy=true
    break
  fi
  sleep 5
done
if [[ "$healthy" != true ]]; then
  echo "HTTPS health check did not reach UP" >&2
  exit 5
fi

python3 - "$env_file" "$sha" <<'PY'
import os
import sys
from pathlib import Path

path = Path(sys.argv[1])
sha = sys.argv[2]
lines = path.read_text().splitlines()
matches = [i for i, line in enumerate(lines) if line.startswith('INK_IMAGE_TAG=')]
if len(matches) != 1:
    raise SystemExit('Expected exactly one INK_IMAGE_TAG setting')
lines[matches[0]] = f'INK_IMAGE_TAG={sha}'
temp = path.with_name(path.name + '.new')
temp.write_text('\n'.join(lines) + '\n')
os.chmod(temp, 0o600)
os.replace(temp, path)
PY
printf '%s\n' "$release" > "$root/.current-release.new"
chmod 600 "$root/.current-release.new"
mv -f "$root/.current-release.new" "$root/.current-release"
rm -f "$archive"
trap - EXIT
echo "DEPLOY_OK $sha"
