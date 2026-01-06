#!/usr/bin/env bash
# MRP Full API Collection (COMPLETE + FIXED) -> cURL CLI
# Save as: mrp_client.sh
# Usage examples are shown at the end of this file or via: ./mrp_client.sh help

set -euo pipefail

###############################################################################
# Configuration
###############################################################################
: "${BASE_URL:=http://localhost:8080}"
: "${USER_ID:=1}"       # default user id (override via env or flags where supported)
: "${MEDIA_ID:=1}"      # default media id
: "${RATING_ID:=1}"     # default rating id
: "${TOKEN:=}"          # set after login, or inject via environment

# Default cURL options
CURL_OPTS=(-sS -D -)    # -sS silent but show errors, -D - print response headers to stdout
JQ_BIN="${JQ_BIN:-jq}"  # JSON processor; optional but recommended

###############################################################################
# Helpers
###############################################################################

have_jq() { command -v "$JQ_BIN" >/dev/null 2>&1; }

# Prints Authorization header if TOKEN is set
auth_header() {
  if [[ -n "${TOKEN}" ]]; then
    printf 'Authorization: Bearer %s' "$TOKEN"
  fi
}

# Unified curl wrapper that prints status code to stderr and response body to stdout.
# Args:
#   $1 = HTTP method
#   $2 = path (e.g., /api/users/login), absolute or relative
#   $3 = data (optional, for POST/PUT with JSON)
curl_json() {
  local method="$1"
  local path="$2"
  local data="${3:-}"

  local url
  if [[ "$path" == http*://* ]]; then
    url="$path"
  else
    url="${BASE_URL%/}${path}"
  fi

  local headers=(-H "Content-Type: application/json")
  local auth="$(auth_header || true)"
  if [[ -n "$auth" ]]; then headers+=(-H "$auth"); fi

  if [[ -n "$data" ]]; then
    # Print status to stderr; write only body to stdout (strip headers)
    # We print headers to stdout due to -D -, then split. Easier: use --write-out and --dump-header to temp.
    # Implement temp files for cleanliness.
    local hdr tmp
    hdr="$(mktemp)"; tmp="$(mktemp)"
    trap 'rm -f "$hdr" "$tmp"' RETURN
    curl "${CURL_OPTS[@]}" -o "$tmp" --dump-header "$hdr" -X "$method" "${headers[@]}" \
      --data "$data" "$url"
    # Show HTTP status to stderr
    local status
    status="$(awk 'toupper($1) ~ /^HTTP/ {code=$2} END{print code}' "$hdr")"
    echo "HTTP $status" >&2
    cat "$tmp"
    rm -f "$hdr" "$tmp"
    trap - RETURN
  else
    local hdr tmp
    hdr="$(mktemp)"; tmp="$(mktemp)"
    trap 'rm -f "$hdr" "$tmp"' RETURN
    curl "${CURL_OPTS[@]}" -o "$tmp" --dump-header "$hdr" -X "$method" "${headers[@]}" "$url"
    local status
    status="$(awk 'toupper($1) ~ /^HTTP/ {code=$2} END{print code}' "$hdr")"
    echo "HTTP $status" >&2
    cat "$tmp"
    rm -f "$hdr" "$tmp"
    trap - RETURN
  fi
}

# Pretty-print JSON if jq is available; otherwise pass-through
pp_json() {
  if have_jq; then
    "$JQ_BIN" .
  else
    cat
  fi
}

###############################################################################
# Auth
###############################################################################

register_user() {
  local username="${1:-user1}"
  local password="${2:-pass123}"
  curl_json POST "/api/users/register" "$(cat <<JSON
{
  "username": "$username",
  "password": "$password"
}
JSON
)" | pp_json
}

login_user() {
  local username="${1:-user1}"
  local password="${2:-pass123}"
  local resp
  resp="$(curl_json POST "/api/users/login" "$(cat <<JSON
{
  "username": "$username",
  "password": "$password"
}
JSON
)")"

  # Try to extract common token fields
  if have_jq; then
    local t
    t="$(printf '%s' "$resp" | "$JQ_BIN" -r '(.token // .accessToken // .jwt // .data.token // empty)')"
    if [[ -n "$t" && "$t" != "null" ]]; then
      TOKEN="$t"
      export TOKEN
      echo "TOKEN captured and exported." >&2
    else
      echo "Note: Login succeeded, but no recognizable token field found." >&2
    fi
  else
    # Fallback: naive grep for token-like substring
    if echo "$resp" | grep -qE '"(token|accessToken|jwt)"\s*:\s*"' ; then
      TOKEN="$(echo "$resp" | sed -nE 's/.*"(token|accessToken|jwt)"\s*:\s*"([^"]+)".*/\2/p' | head -n1)"
      export TOKEN
      echo "TOKEN captured (fallback) and exported." >&2
    else
      echo "Note: jq not installed; token capture skipped." >&2
    fi
  fi

  printf '%s\n' "$resp" | pp_json
}

###############################################################################
# User
###############################################################################

get_profile()        { curl_json GET "/api/users/${USER_ID}/profile" | pp_json; }
get_rating_history() { curl_json GET "/api/users/${USER_ID}/ratings" | pp_json; }
get_favorites()      { curl_json GET "/api/users/${USER_ID}/favorites" | pp_json; }

update_profile() {
  local email="${1:-user1@example.com}"
  local favorite_genre="${2:-sci-fi}"
  curl_json PUT "/api/users/${USER_ID}/profile" "$(cat <<JSON
{
  "email": "$email",
  "favoriteGenre": "$favorite_genre"
}
JSON
)" | pp_json
}

###############################################################################
# Media
###############################################################################

create_media() {
  local title="${1:-Inception}"
  local description="${2:-Sci-fi thriller}"
  local media_type="${3:-movie}"
  local release_year="${4:-2010}"
  local genres_csv="${5:-sci-fi,thriller}"
  local age_restriction="${6:-12}"

  # Convert CSV to JSON array
  IFS=',' read -r -a GEN_ARR <<< "$genres_csv"
  local genres_json; genres_json="$(printf '"%s",' "${GEN_ARR[@]}")"; genres_json="[${genres_json%,}]"

  curl_json POST "/api/media" "$(cat <<JSON
{
  "title": "$title",
  "description": "$description",
  "mediaType": "$media_type",
  "releaseYear": $release_year,
  "genres": $genres_json,
  "ageRestriction": $age_restriction
}
JSON
)" | pp_json
}

delete_media()       { curl_json DELETE "/api/media/${MEDIA_ID}" | pp_json; }

search_media() {
  local title="${1:-incep}"
  local genre="${2:-sci-fi}"
  local sort_by="${3:-score}"
  curl_json GET "/api/media?title=$(printf %s "$title")&genre=$(printf %s "$genre")&sortBy=$(printf %s "$sort_by")" | pp_json
}

update_media() {
  local title="${1:-Inception Updated}"
  local description="${2:-Updated description}"
  local media_type="${3:-movie}"
  local release_year="${4:-2010}"
  local genres_csv="${5:-sci-fi,action}"
  local age_restriction="${6:-16}"

  IFS=',' read -r -a GEN_ARR <<< "$genres_csv"
  local genres_json; genres_json="$(printf '"%s",' "${GEN_ARR[@]}")"; genres_json="[${genres_json%,}]"

  curl_json PUT "/api/media/${MEDIA_ID}" "$(cat <<JSON
{
  "title": "$title",
  "description": "$description",
  "mediaType": "$media_type",
  "releaseYear": $release_year,
  "genres": $genres_json,
  "ageRestriction": $age_restriction
}
JSON
)" | pp_json
}

get_media_by_id()    { curl_json GET "/api/media/${MEDIA_ID}" | pp_json; }

full_search_media() {
  local title="${1:-inception}"
  local genre="${2:-sci-fi}"
  local media_type="${3:-movie}"
  local release_year="${4:-2010}"
  local age_restriction="${5:-12}"
  local rating="${6:-4}"
  local sort_by="${7:-title}"

  local qs
  qs="title=$(printf %s "$title")&genre=$(printf %s "$genre")&mediaType=$(printf %s "$media_type")&releaseYear=$release_year&ageRestriction=$age_restriction&rating=$rating&sortBy=$(printf %s "$sort_by")"
  curl_json GET "/api/media?${qs}" | pp_json
}

###############################################################################
# Rating
###############################################################################

rate_media() {
  local stars="${1:-5}"
  local comment="${2:-Amazing movie!}"
  curl_json POST "/api/media/${MEDIA_ID}/rate" "$(cat <<JSON
{
  "stars": $stars,
  "comment": "$comment"
}
JSON
)" | pp_json
}

like_rating()        { curl_json POST "/api/ratings/${RATING_ID}/like" | pp_json; }

update_rating() {
  local stars="${1:-4}"
  local comment="${2:-Updated comment}"
  curl_json PUT "/api/ratings/${RATING_ID}" "$(cat <<JSON
{
  "stars": $stars,
  "comment": "$comment"
}
JSON
)" | pp_json
}

confirm_rating()     { curl_json POST "/api/ratings/${RATING_ID}/confirm" | pp_json; }

###############################################################################
# Favorites
###############################################################################

mark_favorite()      { curl_json POST   "/api/media/${MEDIA_ID}/favorite" | pp_json; }
unmark_favorite()    { curl_json DELETE "/api/media/${MEDIA_ID}/favorite" | pp_json; }

###############################################################################
# Recommendation
###############################################################################

get_recommendations() {
  local type="${1:-genre}" # genre | content
  curl_json GET "/api/users/${USER_ID}/recommendations?type=$(printf %s "$type")" | pp_json
}

###############################################################################
# Leaderboard
###############################################################################

get_leaderboard()    { curl_json GET "/api/leaderboard" | pp_json; }

###############################################################################
# CLI / Dispatcher
###############################################################################

usage() {
  cat <<'USAGE'
MRP API cURL CLI

Environment variables:
  BASE_URL      Base server URL (default: http://localhost:8080)
  USER_ID       Default user ID (default: 1)
  MEDIA_ID      Default media ID (default: 1)
  RATING_ID     Default rating ID (default: 1)
  TOKEN         Bearer token (auto-captured by 'login', or set manually)

Commands:
  # Auth
  register [username] [password]
  login    [username] [password]      -> captures TOKEN if present

  # User
  get-profile
  get-rating-history
  get-favorites
  update-profile [email] [favoriteGenre]

  # Media
  create-media [title] [desc] [mediaType] [releaseYear] [genresCSV] [ageRestriction]
  delete-media
  search-media [title] [genre] [sortBy]
  update-media [title] [desc] [mediaType] [releaseYear] [genresCSV] [ageRestriction]
  get-media
  full-search-media [title] [genre] [mediaType] [releaseYear] [ageRestriction] [rating] [sortBy]

  # Rating
  rate-media [stars] [comment]
  like-rating
  update-rating [stars] [comment]
  confirm-rating

  # Favorites
  mark-favorite
  unmark-favorite

  # Recommendation
  get-recommendations [genre|content]

  # Leaderboard
  get-leaderboard

Examples:
  ./mrp_client.sh register user1 pass123
  ./mrp_client.sh login user1 pass123
  ./mrp_client.sh update-profile user1@example.com sci-fi
  USER_ID=1 ./mrp_client.sh get-profile
  MEDIA_ID=42 ./mrp_client.sh get-media
  ./mrp_client.sh create-media "Inception" "Sci-fi thriller" movie 2010 "sci-fi,thriller" 12
  ./mrp_client.sh search-media incep sci-fi score
  ./mrp_client.sh full-search-media inception sci-fi movie 2010 12 4 title
USAGE
}

main() {
  local cmd="${1:-help}"; shift || true
  case "$cmd" in
    # Auth
    register)             register_user "$@";;
    login)                login_user "$@";;

    # User
    get-profile)          get_profile;;
    get-rating-history)   get_rating_history;;
    get-favorites)        get_favorites;;
    update-profile)       update_profile "$@";;

    # Media
    create-media)         create_media "$@";;
    delete-media)         delete_media;;
    search-media)         search_media "$@";;
    update-media)         update_media "$@";;
    get-media)            get_media_by_id;;
    full-search-media)    full_search_media "$@";;

    # Rating
    rate-media)           rate_media "$@";;
    like-rating)          like_rating;;
    update-rating)        update_rating "$@";;
    confirm-rating)       confirm_rating;;

    # Favorites
    mark-favorite)        mark_favorite;;
    unmark-favorite)      unmark_favorite;;

    # Recommendation
    get-recommendations)  get_recommendations "$@";;

    # Leaderboard
    get-leaderboard)      get_leaderboard;;

    help|-h|--help)       usage;;
    *) echo "Unknown command: $cmd" >&2; usage; exit 1;;
  esac
}

main "$@"
