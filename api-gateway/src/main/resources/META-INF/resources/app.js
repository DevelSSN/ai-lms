const API_BASE_URL = "/api";

const THREAD_ID_KEY = "ailms_thread_id";
function getThreadId() {
  let id = localStorage.getItem(THREAD_ID_KEY);
  if (!id) {
    id = "default_session";
    localStorage.setItem(THREAD_ID_KEY, id);
  }
  return id;
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
    loadHistory();
    startSSE();
    setupEventListeners();
  setupUploadHandlers();
  } catch (error) {
    console.error("Keycloak init failed:", error);
  }
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

async function loadHistory() {
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
      `${API_BASE_URL}/v1/chat/history/${encodeURIComponent(getThreadId())}`,
      {
        headers: { Authorization: `Bearer ${keycloak.token}` },
      },
    );
    if (!response.ok) throw new Error("History fetch failed");

    const history = await response.json();
    const messages = history?.messages || [];
    if (!messages.length) return;

    const welcome = document.querySelector(".welcome-screen");
    if (welcome) welcome.remove();

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
          thread_id: getThreadId(),
        }),
      });

      if (!response.ok) throw new Error("Gateway unreachable");

      const data = await response.json();
      appendMessage("bot", data.message);
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

  document.querySelectorAll(".action-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      userInput.value = btn.textContent;
      sendMessage();
    });
  });
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
  } catch (error) {
    appendMessage("bot", `Upload failed: ${error.message}. Files up to 50MB supported.`);
  }
}

function renderMarkdown(text) {
  if (typeof marked === "undefined" || typeof DOMPurify === "undefined") {
    return escapeHtml(text);
  }
  return DOMPurify.sanitize(marked.parse(text || ""));
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
