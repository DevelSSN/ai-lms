const API_BASE_URL = "/api";

let currentThreadId = null;

// Optimistic titles for brand-new threads until the server-generated
// (async LLM) title arrives via loadThreads().
const pendingTitles = new Map();

function fallbackTitle(text) {
  const collapsed = (text || "").replace(/\s+/g, " ").trim();
  if (!collapsed) return "New chat";
  return collapsed.length <= 40 ? collapsed : `${collapsed.slice(0, 40).trim()}…`;
}
let currentUserSub = null;

function threadIdKey() {
  return `ailms_thread_id_${currentUserSub || "anon"}`;
}

function saveThreadId() {
  localStorage.setItem(threadIdKey(), currentThreadId);
}

function newThreadId() {
  const c = typeof crypto !== "undefined" ? crypto : null;
  if (c && typeof c.randomUUID === "function") {
    return c.randomUUID();
  }
  return "xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx".replace(/[xy]/g, (ch) => {
    const r = (Math.random() * 16) | 0;
    const v = ch === "x" ? r : (r & 0x3) | 0x8;
    return v.toString(16);
  });
}

const KEYCLOAK_URL = document.querySelector('meta[name="keycloak-url"]')?.content;
if (!KEYCLOAK_URL) {
  console.error("Missing keycloak-url meta tag");
}

let keycloak = null;

document.addEventListener("DOMContentLoaded", () => {
  initKeycloak();
});

async function initKeycloak() {
  keycloak = new Keycloak({
    url: KEYCLOAK_URL,
    realm: "ailms",
    clientId: "ailms-frontend",
  });

  try {
    const authenticated = await keycloak.init({ 
      onLoad: "login-required",
      checkLoginIframe: false,
    });
    if (!authenticated) {
      window.location.reload();
      return;
    }

    setupUI();
    setupTokenRefresh();
    initThread();
    startSSE();
    setupEventListeners();
  setupUploadHandlers();
  } catch (error) {
    console.error("Keycloak init failed:", error);
  }
}

function initThread() {
  currentUserSub = keycloak.tokenParsed?.sub || "";
  currentThreadId = localStorage.getItem(threadIdKey());
  if (currentThreadId) {
    loadHistory(currentThreadId);
  } else {
    clearChat();
    showWelcome();
  }
  loadThreads();
}

function setupUI() {
  const parsed = keycloak.tokenParsed;
  const username = parsed.preferred_username || parsed.email || "User";
  const initials = username
    .split(/[\s._-]+/)
    .map((w) => w[0])
    .join("")
    .toUpperCase()
    .slice(0, 2);

  document.getElementById("user-avatar").textContent = initials;
  document.getElementById("welcome-name").textContent = `Hello, ${username}.`;

  document.getElementById("logout-btn").addEventListener("click", () => {
    keycloak.logout({ redirectUri: window.location.origin });
  });

  if (isTeacherOrAdmin()) {
    const analyticsBtn = document.getElementById("analytics-btn");
    analyticsBtn.hidden = false;
    analyticsBtn.addEventListener("click", toggleAnalyticsPanel);
    document.getElementById("analytics-close").addEventListener("click", toggleAnalyticsPanel);
  }
}

function isTeacherOrAdmin() {
  const roles = keycloak.tokenParsed?.realm_access?.roles || [];
  return roles.includes("TEACHER") || roles.includes("ADMIN");
}

async function apiGet(path) {
  await keycloak.updateToken(5);
  const response = await fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${keycloak.token}` },
  });
  if (!response.ok) throw new Error(`GET ${path} failed: ${response.status}`);
  return response.json();
}

let topicChart = null;

async function toggleAnalyticsPanel() {
  const panel = document.getElementById("analytics-panel");
  const chat = document.getElementById("chat-container");
  const wasHidden = panel.hidden;
  panel.hidden = !wasHidden;
  document
    .querySelector(".footer-note")
    .scrollIntoView({ behavior: "smooth", block: "end" });
  if (wasHidden) {
    document.getElementById("chat-container").style.display = "none";
    await loadClassAnalytics();
  } else {
    document.getElementById("chat-container").style.display = "";
    document.getElementById("analytics-empty")?.remove();
  }
}

async function loadClassAnalytics() {
  const summary = document.getElementById("analytics-summary");
  const select = document.getElementById("analytics-student-select");
  summary.innerHTML = '<div class="analytics-loading">Loading class analytics…</div>';
  try {
    const cls = await apiGet("/api/v1/analytics/class");
    renderClassSummary(summary, cls);
    renderTopicChart(cls.topics || []);
    const students = await apiGet("/api/v1/teacher/students");
    populateStudentSelect(select, students);
  } catch (e) {
    console.error("Analytics load failed:", e);
    summary.innerHTML =
      '<div class="analytics-loading analytics-error">Could not load analytics. Try again later.</div>';
  }
}

function renderClassSummary(summary, cls) {
  const cards = [
    ["Students", cls.totalStudents ?? 0],
    ["Active (30d)", cls.activeStudentsLast30Days ?? 0],
    ["Conversations", cls.totalConversations ?? 0],
    ["Avg score", `${Math.round((cls.averageScore ?? 0) * 10) / 10}%`],
    ["Quiz attempts", cls.totalQuizAttempts ?? 0],
  ];
  summary.innerHTML = "";
  cards.forEach(([label, value]) => {
    const card = document.createElement("div");
    card.classList.add("stat-card");
    const v = document.createElement("div");
    v.classList.add("stat-value");
    v.textContent = value;
    const l = document.createElement("div");
    l.classList.add("stat-label");
    l.textContent = label;
    card.appendChild(v);
    card.appendChild(l);
    summary.appendChild(card);
  });
}

function renderTopicChart(topics) {
  const canvas = document.getElementById("topic-chart");
  const labels = topics.map((t) => t.topic);
  const values = topics.map((t) => t.studentCount);
  if (!labels.length) {
    const box = canvas.parentElement;
    const empty = document.createElement("div");
    empty.id = "analytics-empty";
    empty.className = "analytics-loading";
    empty.textContent = "No topic data yet.";
    box.appendChild(empty);
    return;
  }
  if (typeof Chart === "undefined") {
    canvas.parentElement.appendChild(note("Chart library unavailable"));
    return;
  }
  if (topicChart) topicChart.destroy();
  topicChart = new Chart(canvas.getContext("2d"), {
    type: "bar",
    data: {
      labels,
      datasets: [
        {
          label: "Students",
          data: values,
          backgroundColor: "rgba(59, 130, 246, 0.6)",
          borderColor: "#3b82f6",
          borderWidth: 1,
          borderRadius: 6,
        },
      ],
    },
    options: {
      responsive: true,
      plugins: { legend: { display: false } },
      scales: {
        y: { beginAtZero: true, ticks: { precision: 0 } },
      },
    },
  });
}

function note(text) {
  const el = document.createElement("div");
  el.className = "analytics-loading";
  el.textContent = text;
  return el;
}

function populateStudentSelect(select, students) {
  select.innerHTML = '<option value="">Select a student…</option>';
  (students || []).forEach((s) => {
    const opt = document.createElement("option");
    opt.value = s.studentId;
    opt.textContent = s.studentId;
    select.appendChild(opt);
  });
  select.addEventListener("change", () => loadStudentAnalytics(select.value));
}

async function loadStudentAnalytics(studentId) {
  const detail = document.getElementById("analytics-student-detail");
  if (!studentId) {
    detail.innerHTML = "";
    return;
  }
  detail.innerHTML = '<div class="analytics-loading">Loading student analytics…</div>';
  try {
    const a = await apiGet(`/api/v1/analytics/student/${encodeURIComponent(studentId)}`);
    renderStudentDetail(detail, a);
  } catch (e) {
    console.error("Student analytics failed:", e);
    detail.innerHTML = '<div class="analytics-loading analytics-error">Could not load student data.</div>';
  }
}

function renderStudentDetail(detail, a) {
  const topics = (a.topics || []).length
    ? a.topics.map((t) => `<span class="topic-chip">${escapeHtml(t)}</span>`).join("")
    : "<em>No topics recorded yet.</em>";
  const lastActive = a.lastActive ? new Date(a.lastActive).toLocaleString() : "—";
  detail.innerHTML = `
    <div class="student-grid">
      <div class="student-attr"><span>Conversations</span><b>${a.conversationCount ?? 0}</b></div>
      <div class="student-attr"><span>Messages</span><b>${a.messageCount ?? 0}</b></div>
      <div class="student-attr"><span>Documents analyzed</span><b>${a.documentsAnalyzed ?? 0}</b></div>
      <div class="student-attr"><span>Quiz attempts</span><b>${a.quizAttempts ?? 0}</b></div>
      <div class="student-attr"><span>Average score</span><b>${Math.round((a.averageScore ?? 0) * 10) / 10}%</b></div>
      <div class="student-attr"><span>Last active</span><b>${lastActive}</b></div>
    </div>
    <div class="student-topics"><strong>Topics</strong><div class="topic-chips">${topics}</div></div>`;
}

function setupTokenRefresh() {
  setInterval(async () => {
    try {
      await keycloak.updateToken(30);
    } catch (err) {
      console.error("Token refresh failed:", err);
      keycloak.login();
    }
  }, 30000);
}

async function loadHistory(threadId) {
  const spinner = document.getElementById("history-spinner");
  if (spinner) spinner.hidden = false;

  try {
    await keycloak.updateToken(5);
  } catch (err) {
    console.error("Token refresh failed before history fetch:", err);
    if (spinner) spinner.hidden = true;
    return;
  }

  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/chat/history/${encodeURIComponent(threadId)}`,
      {
        headers: { Authorization: `Bearer ${keycloak.token}` },
      },
    );
    if (!response.ok) {
      if (response.status === 403 || response.status === 404) {
        // Stale thread (e.g. owned by another user): start fresh.
        currentThreadId = newThreadId();
        saveThreadId();
        clearChat();
        showWelcome();
        loadThreads();
        return;
      }
      throw new Error("History fetch failed");
    }

    const history = await response.json();
    const messages = history?.messages || [];
    clearChat();
    if (!messages.length) {
      currentThreadId = null;
      localStorage.removeItem(threadIdKey());
      showWelcome();
      return;
    }
    for (const message of messages) {
      if (message.role === "user") appendMessage("user", message.content);
      else if (message.role === "assistant") appendMessage("bot", message.content);
    }
  } catch (error) {
    console.warn("Could not restore conversation history:", error);
  } finally {
    if (spinner) spinner.hidden = true;
  }
}

function clearChat() {
  const chatContainer = document.getElementById("chat-container");
  chatContainer.innerHTML = "";
}

function showWelcome() {
  const chatContainer = document.getElementById("chat-container");
  if (document.querySelector(".welcome-screen")) return;
  const welcome = document.createElement("div");
  welcome.classList.add("welcome-screen");
  welcome.innerHTML = `
    <h2 id="welcome-name">Hello.</h2>
    <p>What would you like to learn today?</p>
    <div class="quick-actions">
      <button class="action-btn">Explain Neural Networks</button>
      <button class="action-btn">History of Rome</button>
      <button class="action-btn">Quantum Physics 101</button>
    </div>`;
  chatContainer.appendChild(welcome);
  const nameEl = welcome.querySelector("#welcome-name");
  const username = keycloak.tokenParsed?.preferred_username || "friend";
  nameEl.textContent = `Hello, ${username}.`;
  welcome.querySelectorAll(".action-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      document.getElementById("user-input").value = btn.textContent;
      sendMessage();
    });
  });
}

function openThreadsPanel() {
  document.getElementById("threads-panel").classList.add("open");
  document.getElementById("threads-backdrop").classList.add("open");
  loadThreads();
}

function closeThreadsPanel() {
  document.getElementById("threads-panel").classList.remove("open");
  document.getElementById("threads-backdrop").classList.remove("open");
}

async function loadThreads() {
  try {
    await keycloak.updateToken(5);
  } catch (err) {
    console.error("Token refresh failed before thread fetch:", err);
    return;
  }

  try {
    const response = await fetch(`${API_BASE_URL}/v1/chat/threads`, {
      headers: { Authorization: `Bearer ${keycloak.token}` },
    });
    if (!response.ok) throw new Error("Thread list fetch failed");
    const threads = await response.json();
    renderThreadList(threads || []);
  } catch (error) {
    console.warn("Could not load thread list:", error);
  }
}

function renderThreadList(threads) {
  const list = document.getElementById("thread-list");
  list.innerHTML = "";
  if (!threads || !threads.length) {
    const emptyItem = document.createElement("li");
    emptyItem.classList.add("thread-empty");
    emptyItem.textContent = "No conversations yet";
    list.appendChild(emptyItem);
    return;
  }
  for (const thread of threads) {
    if (thread.title) pendingTitles.delete(thread.sessionId);
    const item = document.createElement("li");
    item.classList.add("thread-item");
    if (thread.sessionId === currentThreadId) item.classList.add("active");

    const titleRow = document.createElement("div");
    titleRow.classList.add("thread-item-title-row");

    const title = document.createElement("div");
    title.classList.add("thread-item-title");
    title.textContent = thread.title || pendingTitles.get(thread.sessionId) || "New chat";

    const actions = document.createElement("div");
    actions.classList.add("thread-item-actions");

    const renameBtn = document.createElement("button");
    renameBtn.classList.add("thread-action-btn");
    renameBtn.title = "Rename";
    renameBtn.innerHTML = RENAME_ICON;
    renameBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      startRename(item, title, thread);
    });

    const deleteBtn = document.createElement("button");
    deleteBtn.classList.add("thread-action-btn", "danger");
    deleteBtn.title = "Delete";
    deleteBtn.innerHTML = DELETE_ICON;
    deleteBtn.addEventListener("click", (e) => {
      e.stopPropagation();
      deleteThread(thread.sessionId, thread.title);
    });

    actions.appendChild(renameBtn);
    actions.appendChild(deleteBtn);

    titleRow.appendChild(title);
    titleRow.appendChild(actions);

    const meta = document.createElement("div");
    meta.classList.add("thread-item-meta");
    const count = thread.messageCount ?? 0;
    const when = formatRelativeTime(thread.lastActive);
    meta.textContent = count ? `${count} messages · ${when}` : when;

    item.appendChild(titleRow);
    item.appendChild(meta);
    item.addEventListener("click", () => switchThread(thread.sessionId));
    list.appendChild(item);
  }
}

const RENAME_ICON = `
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="M17 3a2.85 2.83 0 1 1 4 4L7.5 20.5 2 22l1.5-5.5Z"/>
  </svg>`;

const DELETE_ICON = `
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
    <path d="M3 6h18M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2m3 0v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6h14"/>
  </svg>`;

function startRename(item, titleEl, thread) {
  const input = document.createElement("input");
  input.type = "text";
  input.classList.add("thread-rename-input");
  input.value = thread.title || "";
  input.maxLength = 60;
  titleEl.replaceWith(input);
  input.focus();
  input.select();
  input.addEventListener("click", (e) => e.stopPropagation());

  let done = false;
  const finish = async (commit) => {
    if (done) return;
    done = true;
    const newTitle = input.value.trim();
    if (commit && newTitle && newTitle !== thread.title) {
      await renameThread(thread.sessionId, newTitle);
    }
    loadThreads();
  };

  input.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      finish(true);
    } else if (e.key === "Escape") {
      e.stopPropagation();
      finish(false);
    }
  });
  input.addEventListener("blur", () => finish(false));
}

async function renameThread(threadId, title) {
  try {
    await keycloak.updateToken(5);
  } catch (err) {
    keycloak.login();
    return;
  }
  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/chat/threads/${encodeURIComponent(threadId)}`,
      {
        method: "PATCH",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${keycloak.token}`,
        },
        body: JSON.stringify({ title }),
      },
    );
    if (!response.ok) throw new Error("Rename failed");
  } catch (error) {
    console.warn("Rename failed:", error);
  }
}

async function deleteThread(threadId, title) {
  const confirmed = confirm(`Delete "${title || "New chat"}"? This conversation will be removed.`);
  if (!confirmed) return;
  try {
    await keycloak.updateToken(5);
  } catch (err) {
    keycloak.login();
    return;
  }
  try {
    const response = await fetch(
      `${API_BASE_URL}/v1/chat/threads/${encodeURIComponent(threadId)}`,
      {
        method: "DELETE",
        headers: { Authorization: `Bearer ${keycloak.token}` },
      },
    );
    if (!response.ok) throw new Error("Delete failed");
    if (threadId === currentThreadId) {
      currentThreadId = newThreadId();
      saveThreadId();
      clearChat();
      showWelcome();
    }
  } catch (error) {
    console.warn("Delete failed:", error);
    return;
  }
  loadThreads();
}

function formatRelativeTime(iso) {
  if (!iso) return "";
  const seconds = Math.floor((Date.now() - new Date(iso).getTime()) / 1000);
  if (seconds < 60) return "just now";
  if (seconds < 3600) return `${Math.floor(seconds / 60)}m ago`;
  if (seconds < 86400) return `${Math.floor(seconds / 3600)}h ago`;
  return `${Math.floor(seconds / 86400)}d ago`;
}

function newChat() {
  currentThreadId = newThreadId();
  saveThreadId();
  clearChat();
  showWelcome();
  closeThreadsPanel();
  renderThreadList([]);
}

async function switchThread(threadId) {
  if (threadId === currentThreadId) {
    closeThreadsPanel();
    return;
  }
  currentThreadId = threadId;
  saveThreadId();
  closeThreadsPanel();
  await loadHistory(threadId);
  loadThreads();
}

function startSSE() {
  const chatContainer = document.getElementById("chat-container");
  const parsed = keycloak.tokenParsed;
  const currentUsername = parsed.sub || "";

  const eventSource = new EventSource(
    `${API_BASE_URL}/updates?token=${encodeURIComponent(keycloak.token)}`,
  );

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      if (data.user_id === currentUsername) {
        appendMessage("bot", data.response);
      }
    } catch (e) {
      console.error("Error parsing SSE data:", e);
    }
  };

  eventSource.onerror = (err) => {
    console.error("SSE connection failed:", err);
  };
}

function setupEventListeners() {
  const chatContainer = document.getElementById("chat-container");
  const userInput = document.getElementById("user-input");
  const sendBtn = document.getElementById("send-btn");
  const themeToggle = document.getElementById("theme-toggle");
  const body = document.body;

  themeToggle.addEventListener("click", () => {
    body.classList.toggle("dark-mode");
    localStorage.setItem(
      "theme",
      body.classList.contains("dark-mode") ? "dark" : "light",
    );
  });

  if (localStorage.getItem("theme") === "light")
    body.classList.remove("dark-mode");

  userInput.addEventListener("input", () => {
    userInput.style.height = "auto";
    userInput.style.height = userInput.scrollHeight + "px";
  });

  const sendMessage = async () => {
    const text = userInput.value.trim();
    if (!text) return;

    if (!currentThreadId) {
      currentThreadId = newThreadId();
      saveThreadId();
      pendingTitles.set(currentThreadId, fallbackTitle(text));
    }

    const welcome = document.querySelector(".welcome-screen");
    if (welcome) welcome.remove();

    appendMessage("user", text);
    userInput.value = "";
    userInput.style.height = "auto";
    showTypingIndicator();

    try {
      await keycloak.updateToken(5);
    } catch (err) {
      console.error("Token refresh failed before request:", err);
      removeTypingIndicator();
      keycloak.login();
      return;
    }

    try {
      const response = await fetch(`${API_BASE_URL}/interact`, {
        method: "POST",
        headers: {
          "Content-Type": "application/json",
          Authorization: `Bearer ${keycloak.token}`,
        },
        body: JSON.stringify({
          message: text,
          thread_id: currentThreadId,
        }),
      });

      if (!response.ok) throw new Error("Gateway unreachable");

      const data = await response.json();
      removeTypingIndicator();
      appendAssistantContent(data);
      loadThreads();
      // Server title is generated asynchronously (LLM) — refresh again later.
      setTimeout(loadThreads, 45000);
    } catch (error) {
      console.error("API Error:", error);
      removeTypingIndicator();
      appendMessage("bot", "⚠️ Backend unavailable — your message wasn't processed. Please try again.");
    }
  };

  sendBtn.addEventListener("click", sendMessage);
  userInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter" && !e.shiftKey) {
      e.preventDefault();
      sendMessage();
    }
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") closeThreadsPanel();
  });

  document.getElementById("threads-toggle").addEventListener("click", openThreadsPanel);
  document.getElementById("threads-close").addEventListener("click", closeThreadsPanel);
  document.getElementById("threads-backdrop").addEventListener("click", closeThreadsPanel);
  document.getElementById("new-chat-btn").addEventListener("click", newChat);
}

function setupUploadHandlers() {
  const attachBtn = document.getElementById("attach-btn");
  const fileInput = document.getElementById("file-input");

  if (!attachBtn || !fileInput) return;

  const triggerFileInput = (e) => {
    e.preventDefault();
    fileInput.value = "";
    fileInput.click();
  };

  attachBtn.addEventListener("click", triggerFileInput);

  fileInput.addEventListener("change", async (e) => {
    const files = e.target.files;
    if (!files || !files.length) return;
    for (const file of files) {
      await uploadFile(file);
    }
    fileInput.value = "";
  });

  const dropZone = document.querySelector(".input-wrapper");
  if (dropZone) {
    ["dragenter", "dragover"].forEach((eventName) => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.add("drag-over");
      });
    });

    ["dragleave", "drop"].forEach((eventName) => {
      dropZone.addEventListener(eventName, (e) => {
        e.preventDefault();
        e.stopPropagation();
        dropZone.classList.remove("drag-over");
      });
    });

    dropZone.addEventListener("drop", async (e) => {
      const files = e.dataTransfer?.files;
      if (!files || !files.length) return;
      for (const file of files) {
        await uploadFile(file);
      }
    });
  }
}

async function uploadFile(file) {
  const welcome = document.querySelector(".welcome-screen");
  if (welcome) welcome.remove();

  if (!currentThreadId) {
    currentThreadId = newThreadId();
    saveThreadId();
  }

  appendMessage("user", `📎 Uploading: ${file.name}`);

  try {
    await keycloak.updateToken(5);
  } catch (err) {
    appendMessage("bot", "Session expired. Please refresh the page.");
    return;
  }

  const formData = new FormData();
  formData.append("file", file);

  try {
    const response = await fetch(`${API_BASE_URL}/v1/content/upload`, {
      method: "POST",
      headers: {
        Authorization: `Bearer ${keycloak.token}`,
      },
      body: formData,
    });

    if (!response.ok) {
      const errText = await response.text().catch(() => "Upload failed");
      throw new Error(errText);
    }

    const data = await response.json();
    const uploadSessionId = data.sessionId || "";
    const docId = data.docId || "";
    appendMessage("bot", data.message || "Uploaded — analyzing your document…");

    if (uploadSessionId.startsWith("upload:") && docId) {
      pollUploadStatus(docId, uploadSessionId);
    } else {
      loadThreads();
    }
  } catch (error) {
    appendMessage("bot", `Upload failed: ${error.message}. Files up to 50MB supported.`);
  }
}

const UPLOAD_POLL_INTERVAL_MS = 2500;
const UPLOAD_POLL_MAX_MS = 5 * 60 * 1000;

function pollUploadStatus(docId, sessionId) {
  const startedAt = Date.now();

  const check = async () => {
    if (Date.now() - startedAt > UPLOAD_POLL_MAX_MS) {
      appendMessage(
        "bot",
        "Your document is still processing. Once ready, the analysis will appear.",
      );
      return;
    }
    try {
      await keycloak.updateToken(5);
      const response = await fetch(
        `${API_BASE_URL}/v1/content/status/${encodeURIComponent(docId)}`,
        { headers: { Authorization: `Bearer ${keycloak.token}` } },
      );
      if (!response.ok) {
        setTimeout(check, UPLOAD_POLL_INTERVAL_MS);
        return;
      }
      const status = await response.json();
      if (status.status === "INDEXED") {
        showQuizActionBar(sessionId);
        loadThreads();
        return;
      }
      if (status.status === "FAILED") {
        appendMessage(
          "bot",
          `Analysis failed: ${status.error || "Unknown error"}. Please try uploading again.`,
        );
        loadThreads();
        return;
      }
    } catch (e) {
      console.error("Upload status poll failed:", e);
    }
    setTimeout(check, UPLOAD_POLL_INTERVAL_MS);
  };

  setTimeout(check, UPLOAD_POLL_INTERVAL_MS);
}

function showQuizActionBar(sessionId) {
  const bar = document.createElement("div");
  bar.classList.add("message", "bot-message", "quiz-action-bar");
  const btn = document.createElement("button");
  btn.classList.add("quiz-action-btn");
  btn.textContent = "✨ Generate Quiz";
  btn.title = "Create a quiz grounded in this document's analysis";
  btn.addEventListener("click", () => {
    const input = document.getElementById("user-input");
    input.value = "Generate quiz about the uploaded document";
    input.dispatchEvent(new Event("input"));
    document.getElementById("send-btn").click();
  });
  bar.appendChild(btn);
  document.getElementById("chat-container").appendChild(bar);
  requestAnimationFrame(() => {
    document.getElementById("chat-container").scrollTop =
      document.getElementById("chat-container").scrollHeight;
  });
}

function renderMarkdown(text) {
  if (typeof marked === "undefined" || typeof DOMPurify === "undefined") {
    return escapeHtml(text);
  }
  return DOMPurify.sanitize(marked.parse(text || "", { breaks: true }));
}

function escapeHtml(text) {
  const div = document.createElement("div");
  div.textContent = text || "";
  return div.innerHTML;
}

function showTypingIndicator() {
  removeTypingIndicator();
  const chatContainer = document.getElementById("chat-container");
  const indicator = document.createElement("div");
  indicator.id = "typing-indicator";
  indicator.classList.add("message", "bot-message", "typing-indicator");
  indicator.innerHTML = `<span class="dot"></span><span class="dot"></span><span class="dot"></span>`;
  chatContainer.appendChild(indicator);
  requestAnimationFrame(() => {
    chatContainer.scrollTop = chatContainer.scrollHeight;
  });
}

function removeTypingIndicator() {
  const existing = document.getElementById("typing-indicator");
  if (existing) existing.remove();
}

function enhanceCodeBlocks(container) {
  const pres = container.querySelectorAll("pre");
  pres.forEach((pre) => {
    if (pre.querySelector(".copy-code-btn")) return;
    const btn = document.createElement("button");
    btn.classList.add("copy-code-btn");
    btn.title = "Copy code";
    btn.textContent = "Copy";
    btn.addEventListener("click", async () => {
      const codeText = pre.querySelector("code")?.textContent || pre.textContent;
      try {
        await navigator.clipboard.writeText(codeText);
        btn.textContent = "Copied!";
        setTimeout(() => (btn.textContent = "Copy"), 2000);
      } catch (e) {
        console.error("Copy failed:", e);
      }
    });
    pre.style.position = "relative";
    pre.appendChild(btn);
  });
}

function appendMessage(sender, text) {
  const chatContainer = document.getElementById("chat-container");
  const messageDiv = document.createElement("div");
  messageDiv.classList.add("message", `${sender}-message`);

  const tokens = tokenizeYouTubeLinks(text);

  if (tokens.some((token) => token.type === "video")) {
    let prevWasVideo = false;
    for (const token of tokens) {
      if (token.type === "video") {
        const videoContainer = document.createElement("div");
        videoContainer.classList.add("video-container");
        videoContainer.innerHTML = `<iframe src="https://www.youtube-nocookie.com/embed/${token.videoId}?origin=${window.location.origin}" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>`;
        messageDiv.appendChild(videoContainer);
        prevWasVideo = true;
      } else if (token.content.trim()) {
        // A trailing "— Title" belongs to the video above: render as caption.
        const captionMatch = token.content.match(/^\s*—\s*(.+)$/s);
        if (captionMatch && prevWasVideo) {
          const lastContainer = messageDiv.querySelector(".video-container:last-of-type");
          const caption = document.createElement("div");
          caption.classList.add("video-caption");
          caption.textContent = captionMatch[1].trim();
          lastContainer.appendChild(caption);
        } else {
          const contentDiv = document.createElement("div");
          if (sender === "bot") contentDiv.innerHTML = renderMarkdown(token.content);
          else contentDiv.textContent = token.content;
          messageDiv.appendChild(contentDiv);
        }
        prevWasVideo = false;
      }
    }
  } else if (sender === "bot") {
    messageDiv.innerHTML = renderMarkdown(text);
  } else {
    messageDiv.textContent = text;
  }

  if (sender === "bot") {
    enhanceCodeBlocks(messageDiv);
  }

  chatContainer.appendChild(messageDiv);
  requestAnimationFrame(() => {
    chatContainer.scrollTop = chatContainer.scrollHeight;
  });
  return messageDiv;
}

function appendAssistantContent(data) {
  const msgEl = appendMessage("bot", data?.message || "");
  if (Array.isArray(data?.metadata?.citations) && data.metadata.citations.length) {
    attachCitations(msgEl, data.metadata.citations);
  }
  if (data?.metadata?.items && Array.isArray(data.metadata.items)) {
    renderQuizCard(data.metadata);
  }
}

function attachCitations(messageEl, citations) {
  const numbers = new Set(citations.map((c) => c.number));
  highlightCitationMarkers(messageEl, numbers);

  const details = document.createElement("details");
  details.classList.add("citation-footnotes");

  const summary = document.createElement("summary");
  summary.textContent = `Sources (${citations.length})`;
  details.appendChild(summary);

  const list = document.createElement("ul");
  citations.forEach((c) => {
    const li = document.createElement("li");
    const label = document.createElement("span");
    label.classList.add("citation-label");
    label.textContent = `[${c.number}]`;
    const body = document.createElement("span");
    const fileName = c.documentName || c.source || "document";
    const excerpt = (c.text || "").trim();
    const truncated =
      excerpt.length > 160 ? `${excerpt.slice(0, 160)}…` : excerpt;
    body.textContent = `From "${fileName}", chunk ${(Number.isFinite(c.chunkIndex) ? c.chunkIndex : -1) + 1}${excerpt ? `: ${truncated}` : ""}`;
    li.appendChild(label);
    li.appendChild(body);
    list.appendChild(li);
  });
  details.appendChild(list);
  messageEl.appendChild(details);
}

function highlightCitationMarkers(root, numbers) {
  if (!numbers || numbers.size === 0) return;
  const walker = document.createTreeWalker(root, NodeFilter.SHOW_TEXT);
  const toReplace = [];
  while (walker.nextNode()) {
    const node = walker.currentNode;
    const parent = node.parentNode || root;
    if (parent.closest && parent.closest("pre, code")) continue;
    if (/\[\d+\]/.test(node.nodeValue || "")) toReplace.push(node);
  }
  toReplace.forEach((node) => {
    const frag = document.createDocumentFragment();
    const regex = /\[(\d+)\]/g;
    let last = 0;
    let m;
    while ((m = regex.exec(node.nodeValue || ""))) {
      if (m.index > last) {
        frag.appendChild(document.createTextNode(node.nodeValue.slice(last, m.index)));
      }
      const num = parseInt(m[1], 10);
      if (numbers.has(num)) {
        const sup = document.createElement("sup");
        sup.classList.add("citation-ref");
        sup.textContent = num;
        frag.appendChild(sup);
      } else {
        frag.appendChild(document.createTextNode(m[0]));
      }
      last = m.index + m[0].length;
    }
    if (last < (node.nodeValue || "").length) {
      frag.appendChild(document.createTextNode(node.nodeValue.slice(last)));
    }
    node.parentNode.replaceChild(frag, node);
  });
}

function renderQuizCard(meta) {
  const container = document.getElementById("chat-container");
  const card = document.createElement("div");
  card.classList.add("quiz-card");

  const difficulty = capitalize(meta.difficulty || "medium");
  const header = document.createElement("div");
  header.classList.add("quiz-header");
  header.textContent = `📝 Quiz · ${difficulty} · ${meta.items.length} questions`;
  card.appendChild(header);

  const questions = meta.items.map((item, i) => buildQuizQuestion(i, item));
  questions.forEach((q) => card.appendChild(q));

  const checkBtn = document.createElement("button");
  checkBtn.classList.add("quiz-submit-btn");
  checkBtn.textContent = "Check answers";
  card.appendChild(checkBtn);

  const result = document.createElement("div");
  result.classList.add("quiz-result");
  result.hidden = true;
  card.appendChild(result);

  checkBtn.addEventListener("click", () => evaluateQuiz(meta, questions, result));

  container.appendChild(card);
  requestAnimationFrame(() => {
    container.scrollTop = container.scrollHeight;
  });
}

function buildQuizQuestion(index, item) {
  const q = document.createElement("div");
  q.classList.add("quiz-question");

  const prompt = document.createElement("div");
  prompt.classList.add("quiz-question-text");
  prompt.textContent = `${index + 1}. ${item.question || ""}`;
  q.appendChild(prompt);

  if (item.type === "true_false") {
    const wrap = document.createElement("div");
    wrap.classList.add("quiz-options");
    ["true", "false"].forEach((opt) => {
      wrap.appendChild(quizRadio(index, opt, opt[0].toUpperCase() + opt.slice(1)));
    });
    q.appendChild(wrap);
  } else if (Array.isArray(item.options) && item.options.length) {
    const wrap = document.createElement("div");
    wrap.classList.add("quiz-options");
    item.options.forEach((opt) => wrap.appendChild(quizRadio(index, opt, opt)));
    q.appendChild(wrap);
  } else {
    const input = document.createElement("input");
    input.type = "text";
    input.placeholder = "Type your answer…";
    input.classList.add("quiz-input");
    q.appendChild(input);
  }

  const explanation = document.createElement("div");
  explanation.classList.add("quiz-explanation");
  explanation.hidden = true;
  q.appendChild(explanation);
  return q;
}

function quizRadio(index, value, label) {
  const labelEl = document.createElement("label");
  const radio = document.createElement("input");
  radio.type = "radio";
  radio.name = `quiz-q${index}`;
  radio.value = value;
  labelEl.appendChild(radio);
  labelEl.appendChild(document.createTextNode(label));
  return labelEl;
}

function evaluateQuiz(meta, questions, resultEl) {
  const answers = {};
  let score = 0;
  questions.forEach((q, i) => {
    const item = meta.items[i];
    let given = "";
    const checked = q.querySelector("input[type=radio]:checked");
    if (checked) given = checked.value;
    const text = q.querySelector(".quiz-input");
    if (text && text.value.trim()) given = text.value.trim();

    answers[item.question] = given;
    const expected = item.answer || "";
    const isCorrect = given && given.toLowerCase() === expected.toLowerCase();
    if (isCorrect) score++;

    const badge = document.createElement("span");
    badge.classList.add("quiz-badge", isCorrect ? "quiz-correct" : "quiz-wrong");
    badge.textContent = isCorrect ? "✓" : "✗";
    q.querySelector(".quiz-question-text").appendChild(badge);

    const exp = q.querySelector(".quiz-explanation");
    if (exp) {
      const parts = [];
      if (item.explanation) parts.push(`Explanation: ${item.explanation}`);
      if (!isCorrect && expected) parts.push(`Correct answer: ${expected}`);
      exp.textContent = parts.join("  ·  ");
      exp.hidden = false;
    }
  });

  const total = questions.length;
  resultEl.hidden = false;
  resultEl.textContent = `Score: ${score}/${total}${score * 2 >= total ? " — nice work! 🎉" : " — review the answers above to reinforce weak areas."}`;
  resultEl.classList.toggle("quiz-result-pass", score * 2 >= total);
  submitQuizResult(meta, answers, score, total);
}

async function submitQuizResult(meta, answers, score, total) {
  try {
    await keycloak.updateToken(5);
  } catch {
    return;
  }
  try {
    await fetch(`${API_BASE_URL}/v1/chat/quiz/submit`, {
      method: "POST",
      headers: {
        "Content-Type": "application/json",
        Authorization: `Bearer ${keycloak.token}`,
      },
      body: JSON.stringify({
        sessionId: currentThreadId,
        contentId: meta.contentId,
        questions: meta.items,
        answers,
        score,
        total,
      }),
    });
  } catch (e) {
    console.warn("Quiz result submit failed:", e);
  }
}

function capitalize(value) {
  return value ? value.charAt(0).toUpperCase() + value.slice(1) : "";
}

const YOUTUBE_URL_RE =
  /https?:\/\/(?:www\.)?(?:youtube\.com\/(?:watch\?(?:.*&)?v=|embed\/|shorts\/)|youtu\.be\/)([A-Za-z0-9_-]{11})(?:[&?][^\s]*)?(?=[\s&?)"'\]<]|$)/g;

const YOUTUBE_MARKDOWN_RE =
  /\[([^\]]*)\]\((https?:\/\/[^)\s]*youtube\.com\/[^)\s]*|https?:\/\/[^)\s]*youtu\.be\/[^)\s]*)\)/g;

const YOUTUBE_TOKEN_RE = /\u0000([A-Za-z0-9_-]{11})\u0000/g;

const PLACEHOLDER_ID_RE = /your|video|link|sample|example|placeholder/i;

function extractYouTubeIds(text) {
  const ids = [];
  for (const match of text.matchAll(YOUTUBE_URL_RE)) {
    const videoId = match[1];
    if (PLACEHOLDER_ID_RE.test(videoId)) continue;
    if (!ids.includes(videoId)) ids.push(videoId);
  }
  return ids;
}

function tokenizeYouTubeLinks(text) {
  const seen = new Set();
  const markerFor = (match, videoId) => {
    if (PLACEHOLDER_ID_RE.test(videoId)) return match;
    if (seen.has(videoId)) return match;
    seen.add(videoId);
    return `\u0000${videoId}\u0000`;
  };

  text = text.replace(YOUTUBE_MARKDOWN_RE, (match, label, url) =>
    extractYouTubeIds(url).length ? `${label}\u0000${extractYouTubeIds(url)[0]}\u0000` : match,
  );
  text = text.replace(YOUTUBE_URL_RE, (match, videoId) => markerFor(match, videoId));

  const tokens = [];
  const parts = text.split(YOUTUBE_TOKEN_RE);
  parts.forEach((part, i) => {
    if (i % 2 === 1) tokens.push({ type: "video", videoId: part });
    else if (part) tokens.push({ type: "text", content: part });
  });
  return tokens;
}
