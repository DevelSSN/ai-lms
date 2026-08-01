const API_BASE_URL = "/api";

let currentThreadId = null;
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
    if (!response.ok) throw new Error("History fetch failed");

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
  for (const thread of threads) {
    const item = document.createElement("li");
    item.classList.add("thread-item");
    if (thread.sessionId === currentThreadId) item.classList.add("active");

    const titleRow = document.createElement("div");
    titleRow.classList.add("thread-item-title-row");

    const title = document.createElement("div");
    title.classList.add("thread-item-title");
    title.textContent = thread.title || "New chat";

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
    }

    const welcome = document.querySelector(".welcome-screen");
    if (welcome) welcome.remove();

    appendMessage("user", text);
    userInput.value = "";
    userInput.style.height = "auto";

    try {
      await keycloak.updateToken(5);
    } catch (err) {
      console.error("Token refresh failed before request:", err);
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
      appendMessage("bot", data.message);
      loadThreads();
    } catch (error) {
      console.error("API Error:", error);
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

  document.getElementById("threads-toggle").addEventListener("click", openThreadsPanel);
  document.getElementById("threads-close").addEventListener("click", closeThreadsPanel);
  document.getElementById("threads-backdrop").addEventListener("click", closeThreadsPanel);
  document.getElementById("new-chat-btn").addEventListener("click", newChat);
}

function setupUploadHandlers() {
  const attachBtn = document.getElementById("attach-btn");
  const fileInput = document.getElementById("file-input");

  attachBtn.addEventListener("click", () => fileInput.click());

  fileInput.addEventListener("change", async (e) => {
    const file = e.target.files[0];
    if (!file) return;
    await uploadFile(file);
    fileInput.value = "";
  });
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
    appendMessage("bot", data.message);
    loadThreads();
  } catch (error) {
    appendMessage("bot", `Upload failed: ${error.message}. Files up to 50MB supported.`);
  }
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

function appendMessage(sender, text) {
  const chatContainer = document.getElementById("chat-container");
  const messageDiv = document.createElement("div");
  messageDiv.classList.add("message", `${sender}-message`);

  const tokens = tokenizeYouTubeLinks(text);

  if (tokens.some((token) => token.type === "video")) {
    for (const token of tokens) {
      if (token.type === "video") {
        const videoContainer = document.createElement("div");
        videoContainer.classList.add("video-container");
        videoContainer.innerHTML = `<iframe src="https://www.youtube-nocookie.com/embed/${token.videoId}?origin=${window.location.origin}" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>`;
        messageDiv.appendChild(videoContainer);
      } else if (token.content.trim()) {
        const contentDiv = document.createElement("div");
        if (sender === "bot") contentDiv.innerHTML = renderMarkdown(token.content);
        else contentDiv.textContent = token.content;
        messageDiv.appendChild(contentDiv);
      }
    }
  } else if (sender === "bot") {
    messageDiv.innerHTML = renderMarkdown(text);
  } else {
    messageDiv.textContent = text;
  }

  chatContainer.appendChild(messageDiv);
  chatContainer.scrollTop = chatContainer.scrollHeight;
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
