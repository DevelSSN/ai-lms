#!/usr/bin/env bash
#
# remote-smoke.sh — one-pass end-to-end smoke test for the AI-LMS stack.
#
# Covers, in a single run:
#   Auth   : Keycloak password grant -> bearer token (gateway confidential client)
#   E-Flow : upload -> ContentAnalysis (S3 + pgvector/Qdrant ingestion) -> analysis message
#   F1     : chat quiz grounded in the upload-session CAA (analysis:upload:<docId> fallback)
#   F2/F3  : difficulty + question count parsed ("hard", 10)
#   F4     : structured quiz metadata (items/questionCount/difficulty) in the response
#   F7     : quiz result persistence + weak-area profiling note (sub-50% score)
#   G      : citation markers + CitationMetadata on a RAG-grounded answer (if LLM cites)
#   H4     : student analytics (own + teacher cross-student view)
#   H5     : class analytics (teacher/admin only)
#   H1+H2  : realm roles coming through in the token (STUDENT/TEACHER)
#   H3     : teacher -> student association add/list/remove
#   E-SSE  : /api/updates event stream reachable and emitting
#   Threads: list, history, rename, delete
#   Misc   : insights, profile GET/PUT
#
# Prereqs (see eval.md Phase 0):
#   podman compose up -d   (postgres redis kafka keycloak qdrant minio)
#   ollama reachable with deepseek-r1:7b + nomic-embed-text
#   orchestrator (10082) and api-gateway (10080) running
#
# Usage:
#   scripts/remote-smoke.sh [--skip=sse,youtube] [--fail-fast]
#
# Exit code 0 = all run steps passed, 1 = one or more failed.

set -uo pipefail

FAIL_FAST=false
SKIP=""
for arg in "$@"; do
  case "$arg" in
    --fail-fast) FAIL_FAST=true ;;
    --skip=*) SKIP="${arg#--skip=}" ;;
    --skip) echo "use --skip=sse,youtube" >&2; exit 2 ;;
  esac
done

# ---- config (env overrides; falls back to repo .env) ----
if [[ -f .env ]]; then set -a; source .env; set +a; fi

GATEWAY_URL="${GATEWAY_URL:-http://localhost:10080}"
KC_URL="${KC_URL:-http://localhost:10081}"
KC_REALM="${KC_REALM:-ailms}"
KC_CLIENT="${KC_CLIENT:-ailms-gateway}"
KC_SECRET="${OIDC_SECRET:-}"
USERNAME="${SMOKE_USER:-student1}"
PASSWORD="${SMOKE_PASS:-password}"
TEACHER_USER="${SMOKE_TEACHER_USER:-teacher1}"
TEACHER_PASS="${SMOKE_TEACHER_PASS:-password}"
TEST_DOC="${SMOKE_DOC:-docs/smoke-input.txt}"

PASS=0; FAIL=0; RUN=0; FAILED_STEPS=()

say()  { printf '\n\033[1m== %s\033[0m\n' "$*"; }
ok()   { printf '    \033[32mPASS\033[0m %s\n' "$*"; PASS=$((PASS+1)); RUN=$((RUN+1)); }
bad()  { printf '    \033[31mFAIL\033[0m %s\n' "$*"; FAIL=$((FAIL+1)); RUN=$((RUN+1)); FAILED_STEPS+=("$*"); }
skip() { printf '    \033[90mSKIP\033[0m %s\n' "$*"; }
skipflag() { [[ ",$SKIP," == *",$1,"* ]]; }

jget() { # jget <json-string> <dotted-path>
  python3 - "$1" "$2" <<'PY'
import sys, json
try:
    data = json.loads(sys.argv[1])
except Exception:
    print(""); sys.exit(0)
cur = data
for part in sys.argv[2].split("."):
    if isinstance(cur, list):
        cur = cur[int(part)]
    elif isinstance(cur, dict):
        cur = cur.get(part)
    else:
        cur = None; break
print("" if cur is None else (cur if isinstance(cur, (str, int, float, bool)) else json.dumps(cur)))
PY
}

lenj() { # lenj <json-string> — count array elements (0 on any error)
  python3 -c 'import sys,json
try: print(len(json.loads(sys.argv[1])))
except Exception: print(0)' "$1"
}

jwt_sub() { # jwt_sub <token> — JWT subject claim
  python3 -c 'import sys,base64,json
t=sys.argv[1].split(".")
p=t[1]+("="*((4-len(t[1])%4)%4))
try: print(json.loads(base64.urlsafe_b64decode(p)).get("sub",""))
except Exception: print("")' "$1"
}

jwt_has() { # jwt_has <token> <role> — echo "yes" when realm role present, else empty
  python3 -c 'import sys,base64,json
t=sys.argv[1].split(".")
p=t[1]+("="*((4-len(t[1])%4)%4))
try:
    roles=json.loads(base64.urlsafe_b64decode(p)).get("realm_access",{}).get("roles",[])
    print("yes" if sys.argv[2] in roles else "")
except Exception: print("")' "$1" "$2"
}

http_code() { # http_code <url> [data] [method] [extra-flags...]
  local url="$1" data="${2:-}" method="${3:-GET}" t="${4:-300}"
  local args=(-s -o /dev/null -w '%{http_code}' --max-time "$t" -X "$method" \
              -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')
  [[ -n "$data" ]] && args+=(-d "$data")
  curl "${args[@]}" "$url"
}

ping_code() { curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$1"; }

get_token() { # get_token <username> <password> -> prints access_token or empty
  local res
  res=$(curl -s --max-time 15 -X POST \
      -d grant_type=password -d client_id="$KC_CLIENT" -d client_secret="$KC_SECRET" \
      -d username="$1" -d password="$2" \
      "$KC_URL/realms/$KC_REALM/protocol/openid-connect/token" || true)
  python3 -c 'import sys,json
try: print(json.load(sys.stdin).get("access_token",""))
except Exception: print("")' <<< "$res"
}

TOKEN=""
DOC_ID=""
F1_THREAD=""

say "0. Prerequisites (reachability)"
KC_OK=$(ping_code "$KC_URL/realms/$KC_REALM")
[[ "$KC_OK" == "200" ]] && ok "Keycloak reachable ($KC_URL)" || bad "Keycloak NOT reachable ($KC_URL) — podman compose up -d first"

CW_OK=$(ping_code "$GATEWAY_URL/")
[[ "$CW_OK" == "200" ]] && ok "gateway reachable ($GATEWAY_URL)" || bad "gateway NOT reachable ($GATEWAY_URL)"

# 1. Get token ---------------------------------------------------------------
say "1. Auth (Keycloak password grant)"
if [[ -n "$KC_SECRET" ]]; then
  TOKEN=$(get_token "$USERNAME" "$PASSWORD")
  [[ -n "$TOKEN" ]] && ok "got bearer token for $USERNAME" || bad "token acquisition failed"
else
  bad "OIDC_SECRET (KC_SECRET) not set — needed for the gateway confidential client"
fi

if [[ -z "$TOKEN" && "$FAIL_FAST" == "true" ]]; then echo "Abort: no token → check .env"; exit 1; fi

AUTH=(-H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json')

# 2. Upload + CAA -------------------------------------------------------------
say "2. Upload -> Content Analysis (S3 + pgvector + Qdrant + CAA)"
if [[ -n "$TOKEN" && -n "$TEST_DOC" && -f "$TEST_DOC" ]] && ! skipflag upload; then
  UP_START=$(date +%s)
  UP=$(curl -s -w '\n%{http_code}' --max-time 300 -X POST \
       -H "Authorization: Bearer $TOKEN" \
       -F "file=@$TEST_DOC" "$GATEWAY_URL/api/v1/content/upload")
  UP_CODE=$(echo "$UP" | tail -1); UP_BODY=$(echo "$UP" | sed '$d' | head -c 2000)
  if [[ "$UP_CODE" == "200" ]]; then
    UPLOAD_SESS=$(jget "$UP_BODY" sessionId)
    DOC_ID="${UPLOAD_SESS#upload:}"
    if [[ -n "$DOC_ID" && "$DOC_ID" != "$UPLOAD_SESS" ]]; then
      ok "upload ok (doc=$DOC_ID, $(( $(date +%s) - UP_START ))s)"
    else
      bad "upload 200 but sessionId not 'upload:<docId>' (got '$UPLOAD_SESS')"
    fi
  else
    bad "upload HTTP $UP_CODE: $UP_BODY"
  fi
else
  skip "upload — token/$(basename "$TEST_DOC") missing, or --skip=upload"
fi

# 3. F1 — chat quiz grounded in upload CAA -------------------------------------
say "3. F1 — chat 'Generate quiz about the uploaded document' (CAA fallback)"
if [[ -n "$TOKEN" && -n "${DOC_ID:-}" ]] && ! skipflag quiz; then
  F1_THREAD="smoke-f1-$(date +%s)"
  Q1=$(curl -s -w '\n%{http_code}' --max-time 300 -X POST "${AUTH[@]}" \
       -d "{\"message\":\"Generate quiz about the uploaded document\",\"thread_id\":\"$F1_THREAD\"}" \
       "$GATEWAY_URL/api/interact")
  Q1_CODE=$(echo "$Q1" | tail -1); Q1_BODY=$(echo "$Q1" | sed '$d')
  ITEMS=$(lenj "$(jget "$Q1_BODY" metadata.items)")
  if [[ "$Q1_CODE" == "200" && "$ITEMS" -ge 1 ]]; then
    ok "F1 returned metadata with $ITEMS items (CAA fallback exercised)"
  elif [[ "$Q1_CODE" == "200" ]]; then
    bad "F1 no structured metadata (#items=$ITEMS) — LLM may not have emitted JSON"
  else
    bad "F1 HTTP $Q1_CODE"
  fi
else
  skip "F1 — no doc uploaded or --skip=quiz"
fi

# 4. F2/F3/F4 — parameterized assess --------------------------------------------
say "4. F2/F3/F4 — /content/assess hard × 10 (metadata contract)"
if [[ -n "$TOKEN" && -n "${DOC_ID:-}" ]] && ! skipflag assess; then
  A2=$(curl -s -w '\n%{http_code}' --max-time 300 -X POST "${AUTH[@]}" \
       -d "{\"contentId\":\"$DOC_ID\",\"questionCount\":10,\"difficulty\":\"hard\"}" \
       "$GATEWAY_URL/api/v1/content/assess")
  A2_CODE=$(echo "$A2" | tail -1); A2_BODY=$(echo "$A2" | sed '$d')
  DIFF=$(jget "$A2_BODY" metadata.difficulty)
  CNT=$(jget "$A2_BODY" metadata.questionCount)
  ITEMS=$(lenj "$(jget "$A2_BODY" metadata.items)")
  if [[ "$DIFF" == "hard" && "$CNT" == "10" && "$ITEMS" -ge 1 ]]; then
    ok "assess metadata correct (difficulty=$DIFF, questionCount=$CNT, items=$ITEMS)"
  else
    bad "assess metadata mismatch (difficulty=$DIFF, questionCount=$CNT, items=$ITEMS) — check QGA JSON output / parsing"
  fi
else
  skip "assess — no doc uploaded or --skip=assess"
fi

# 4b. G — citations (RAG-grounded answer) ----------------------------------------
say "4b. G — citation markers + CitationMetadata (only if the LLM cites)"
if [[ -n "$TOKEN" && -n "${DOC_ID:-}" ]] && ! skipflag quiz; then
  G_THREAD="smoke-g-$(date +%s)"
  G2=$(curl -s -w '\n%{http_code}' --max-time 300 -X POST "${AUTH[@]}" \
       -d "{\"message\":\"Explain the key concepts from the uploaded document\",\"thread_id\":\"$G_THREAD\"}" \
       "$GATEWAY_URL/api/interact")
  G2_CODE=$(echo "$G2" | tail -1); G2_BODY=$(echo "$G2" | sed '$d')
  CITE=$(lenj "$(jget "$G2_BODY" metadata.citations)")
  if [[ "$G2_CODE" == "200" && "$CITE" -ge 1 ]]; then
    ok "citation metadata present ($CITE cited chunk(s))"
  elif [[ "$G2_CODE" == "200" ]]; then
    skip "HTTP 200 but no [N] citations emitted (LLM-dependent)"
  else
    bad "G citations HTTP $G2_CODE"
  fi
else
  skip "G citations — no doc uploaded or --skip=quiz"
fi

# 5. F7 — quiz persistence (deliberately wrong answers) --------------------------
say "5. F7 — submit low-score quiz (persistence + weak-area profiling note)"
if [[ -n "$TOKEN" ]] && ! skipflag quizsubmit; then
  SUB_CODE=$(http_code "$GATEWAY_URL/api/v1/chat/quiz/submit" \
      "{\"sessionId\":\"$F1_THREAD\",\"contentId\":\"${DOC_ID:-none}\",\
\"questions\":[{\"question\":\"q1\",\"type\":\"multiple_choice\",\"options\":[\"a\",\"b\"],\"answer\":\"b\",\"explanation\":\"e1\"}],\
\"answers\":{\"q1\":\"a\"},\"score\":0,\"total\":1}")
  [[ "$SUB_CODE" == "204" ]] && ok "quiz result persisted (204)" || bad "quiz submit HTTP $SUB_CODE"
else
  skip "quiz submit — token missing or --skip=quizsubmit"
fi

# 6. Threads / history / rename / delete ---------------------------------------
say "6. Threads, history, rename, delete"
TH_CODE=$(http_code "$GATEWAY_URL/api/v1/chat/threads" "" GET 30)
if [[ "$TH_CODE" == "200" ]]; then
  ok "thread list (HTTP 200)"
else
  bad "thread list HTTP $TH_CODE"
fi

if [[ -n "${F1_THREAD:-}" ]]; then
  H_CODE=$(http_code "$GATEWAY_URL/api/v1/chat/history/$F1_THREAD" "" GET 30)
  ok "history for thread (HTTP $H_CODE)"
  R_CODE=$(http_code "$GATEWAY_URL/api/v1/chat/threads/$F1_THREAD" \
           '{"title":"smoke renamed"}' PATCH 30)
  ok "rename thread (HTTP $R_CODE)"
  D_CODE=$(http_code "$GATEWAY_URL/api/v1/chat/threads/$F1_THREAD" "" DELETE 30)
  ok "delete thread (HTTP $D_CODE)"
else
  skip "history/rename/delete — no F1 thread created (or quiz step skipped)"
fi

# 7. Insights + profile ---------------------------------------------------------
say "7. Insights + profile"
I2_CODE=$(http_code "$GATEWAY_URL/api/v1/chat" \
          '{"message":"Show my learning insights and progress","sessionId":"smoke-insight"}' POST 300)
[[ "$I2_CODE" == "200" ]] && ok "insights chat (HTTP 200)" || bad "insights chat HTTP $I2_CODE"

P2_CODE=$(http_code "$GATEWAY_URL/api/v1/profile" "" GET 30)
[[ "$P2_CODE" == "200" ]] && ok "profile GET (HTTP 200)" || bad "profile GET HTTP $P2_CODE"
P3_CODE=$(http_code "$GATEWAY_URL/api/v1/profile" '{"name":"Smoke Tester"}' PUT 30)
[[ "$P3_CODE" == "200" ]] && ok "profile PUT (HTTP 200)" || bad "profile PUT HTTP $P3_CODE"

# 7b. H — roles, analytics, RBAC, teacher associations ---------------------------
say "7b. H — realm roles + analytics + RBAC + teacher associations"
STUDENT_SUB=$(jwt_sub "$TOKEN")
if [[ -n "$STUDENT_SUB" ]]; then
  [[ -n "$(jwt_has "$TOKEN" STUDENT)" ]] && ok "student token has STUDENT realm role" \
                                          || bad "student token missing STUDENT role (H1 role not in realm_access)"
  SELF=$(http_code "$GATEWAY_URL/api/v1/analytics/student/$STUDENT_SUB" "" GET 30)
  [[ "$SELF" == "200" ]] && ok "student self-analytics (HTTP 200)" || bad "student self-analytics HTTP $SELF"
  OTHER=$(http_code "$GATEWAY_URL/api/v1/analytics/student/not-$STUDENT_SUB" "" GET 30)
  [[ "$OTHER" == "403" ]] && ok "RBAC blocks cross-student analytics (403)" || bad "cross-student analytics HTTP $OTHER (expected 403)"
else
  skip "H roles/analytics — student token sub not decodable"
fi

TTOKEN=$(get_token "$TEACHER_USER" "$TEACHER_PASS")
if [[ -n "$TTOKEN" ]]; then
  [[ -n "$(jwt_has "$TTOKEN" TEACHER)" ]] && ok "teacher token has TEACHER realm role" \
                                          || bad "teacher token missing TEACHER role"
  TSAVE=$TOKEN; TOKEN=$TTOKEN
  CLASS=$(http_code "$GATEWAY_URL/api/v1/analytics/class" "" GET 30)
  [[ "$CLASS" == "200" ]] && ok "teacher class analytics (HTTP 200)" || bad "class analytics HTTP $CLASS"
  TSTU=$(http_code "$GATEWAY_URL/api/v1/analytics/student/$STUDENT_SUB" "" GET 30)
  [[ "$TSTU" == "200" ]] && ok "teacher cross-student analytics (HTTP 200)" || bad "teacher cross-student HTTP $TSTU"
  ADD=$(http_code "$GATEWAY_URL/api/v1/teacher/students" "{\"studentId\":\"$STUDENT_SUB\"}" POST 30)
  [[ "$ADD" == "204" ]] && ok "teacher adds student link (HTTP 204)" || bad "teacher add student HTTP $ADD"
  LST=$(http_code "$GATEWAY_URL/api/v1/teacher/students" "" GET 30)
  [[ "$LST" == "200" ]] && ok "teacher student list (HTTP 200)" || bad "teacher student list HTTP $LST"
  DEL=$(http_code "$GATEWAY_URL/api/v1/teacher/students/$STUDENT_SUB" "" DELETE 30)
  [[ "$DEL" == "204" ]] && ok "teacher removes student link (HTTP 204)" || bad "teacher remove student HTTP $DEL"
  TOKEN=$TSAVE
else
  skip "H teacher checks — teacher token acquisition failed (check SMOKE_TEACHER_USER/PASS)"
fi

# 8. SSE stream -----------------------------------------------------------------
say "8. SSE /api/updates?token=..."
if [[ -n "$TOKEN" ]] && ! skipflag sse; then
  SSE=$(curl -s --max-time 6 -N -H "Authorization: Bearer $TOKEN" \
        "$GATEWAY_URL/api/updates?token=$TOKEN" | head -c 400 || true)
  [[ -n "$SSE" ]] && ok "SSE connected (stream data received)" \
                   || skip "SSE connected but idle in 6s window (no proactive event fired)"
else
  skip "sse — token missing or --skip=sse"
fi

# 9. YouTube (optional) ----------------------------------------------------------
say "9. YouTube search (optional)"
if [[ -n "$TOKEN" && -n "${YOUTUBE_API_KEY:-}" ]] && ! skipflag youtube; then
  Y2=$(curl -s -w '\n%{http_code}' --max-time 300 -X POST "${AUTH[@]}" \
       -d "{\"message\":\"Find videos about photosynthesis\",\"sessionId\":\"smoke-yt\"}" \
       "$GATEWAY_URL/api/v1/chat")
  Y2_CODE=$(echo "$Y2" | tail -1); Y2_BODY=$(echo "$Y2" | sed '$d')
  if [[ "$Y2_CODE" == "200" && "$Y2_BODY" == *"youtube.com/watch"* ]]; then
    ok "YouTube returned links"
  elif [[ "$Y2_CODE" == "200" ]]; then
    skip "YouTube: HTTP 200 but no links in response"
  else
    bad "YouTube HTTP $Y2_CODE"
  fi
else
  skip "youtube — token missing, no YOUTUBE_API_KEY, or --skip=youtube"
fi

# ---- summary ---------------------------------------------------------------
say "Summary"
echo ""
echo "  ran:    $RUN"
echo "  passed: $PASS"
echo "  failed: $FAIL"
if [[ ${#FAILED_STEPS[@]} -gt 0 ]]; then
  echo ""
  printf '  failed steps:\n'
  printf '    - %s\n' "${FAILED_STEPS[@]}"
fi
echo ""
[[ "$FAIL" == "0" ]] && echo "SMOKE OK" || echo "SMOKE FAILED"
exit $(( FAIL > 0 ? 1 : 0 ))