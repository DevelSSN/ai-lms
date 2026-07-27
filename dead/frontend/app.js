document.addEventListener("DOMContentLoaded", () => {
  const chatContainer = document.getElementById("chat-container");
  const userInput = document.getElementById("user-input");
  const sendBtn = document.getElementById("send-btn");
  const themeToggle = document.getElementById("theme-toggle");
  const body = document.body;

  const API_BASE_URL = "http://localhost:10080/api";

  // 1. Initialize EventSource for Real-time SSE Updates from Quarkus
  const eventSource = new EventSource(`${API_BASE_URL}/updates`);

  eventSource.onmessage = (event) => {
    try {
      const data = JSON.parse(event.data);
      console.log("Received async update:", data);

      // Only show message if it's for the current user
      if (data.user_id === "john_doe") {
        appendMessage("bot", data.response);
      }
    } catch (e) {
      console.error("Error parsing SSE data:", e);
    }
  };

  eventSource.onerror = (err) => {
    console.error("SSE connection failed:", err);
    // Fallback to mock logic if connection stays down
  };

  // Theme Toggle
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
      // Send interaction to Gateway
      const response = await fetch(`${API_BASE_URL}/interact`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          message: text,
          user_id: "john_doe",
          thread_id: "default_session",
        }),
      });

      if (!response.ok) throw new Error("Gateway unreachable");

      // Note: We don't append a response here because it will come via SSE
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

  function appendMessage(sender, text) {
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

  document.querySelectorAll(".action-btn").forEach((btn) => {
    btn.addEventListener("click", () => {
      userInput.value = btn.textContent;
      sendMessage();
    });
  });
});
