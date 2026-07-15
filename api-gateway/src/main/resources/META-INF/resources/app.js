const API_BASE_URL = "/api";

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
    startSSE();
    setupEventListeners();
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
          thread_id: "default_session",
        }),
      });

      if (!response.ok) throw new Error("Gateway unreachable");

      console.log(
        "Request sent to gateway, waiting for async response via SSE...",
      );
    } catch (error) {
      console.warn("API Error, falling back to mock:", error);
      setTimeout(() => {
        appendMessage("bot", generateMockResponse(text));
      }, 1000);
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

function appendMessage(sender, text) {
  const chatContainer = document.getElementById("chat-container");
  const messageDiv = document.createElement("div");
  messageDiv.classList.add("message", `${sender}-message`);

  const videoId = extractYouTubeId(text);
  let content = text;

  if (videoId) {
    content = text.replace(
      /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/watch\?v=|youtu\.be\/)([a-zA-Z0-9_-]{11})/,
      "",
    );
    messageDiv.innerHTML = `<div>${content}</div>`;
    const videoContainer = document.createElement("div");
    videoContainer.classList.add("video-container");
    videoContainer.innerHTML = `<iframe src="https://www.youtube-nocookie.com/embed/${videoId}?origin=${window.location.origin}" title="YouTube video player" frameborder="0" allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture" referrerpolicy="strict-origin-when-cross-origin" allowfullscreen></iframe>`;
    messageDiv.appendChild(videoContainer);
  } else {
    messageDiv.textContent = text;
  }

  chatContainer.appendChild(messageDiv);
  chatContainer.scrollTop = chatContainer.scrollHeight;
}

function extractYouTubeId(text) {
  const regex =
    /(?:https?:\/\/)?(?:www\.)?(?:youtube\.com\/watch\?v=|youtu\.be\/)([a-zA-Z0-9_-]{11})/;
  const match = text.match(regex);
  return match ? match[1] : null;
}

function generateMockResponse(query) {
  if (query.toLowerCase().includes("neural network")) {
    return "Neural networks are inspired by the human brain. Here's a great visual explanation: https://www.youtube.com/watch?v=aircAruvnKk";
  }
  return "Backend is currently in mock mode. Please ensure all microservices are running for real-time AI responses.";
}
