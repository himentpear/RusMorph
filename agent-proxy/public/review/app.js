"use strict";

const state = {
  reviewer: null,
  task: null,
  recorder: null,
  stream: null,
  chunks: [],
  audioBlob: null,
  audioUrl: null,
  timerId: null,
  startedAt: 0,
};

const $ = id => document.getElementById(id);
const loginView = $("loginView");
const appView = $("appView");
const toast = $("toast");

const modeLabels = {
  correct: "正确朗读",
  minor_error: "轻微错误",
  omission: "故意漏读",
  substitution: "故意换词",
  off_target: "完全偏离",
  noise: "噪声样本",
};

const ratingOptions = [
  ["correct", "正确"],
  ["acceptable", "可接受"],
  ["wrong", "发音错误"],
  ["substituted", "读成其他词"],
  ["omitted", "漏读"],
];

async function api(path, options = {}) {
  const response = await fetch(path, { credentials: "same-origin", ...options });
  const type = response.headers.get("content-type") || "";
  const body = type.includes("application/json") ? await response.json() : null;
  if (!response.ok) {
    const error = new Error(body?.error?.message || `请求失败（${response.status}）`);
    error.status = response.status;
    error.code = body?.error?.code;
    throw error;
  }
  return body;
}

function jsonOptions(method, body) {
  return { method, headers: { "content-type": "application/json" }, body: JSON.stringify(body) };
}

function showToast(message) {
  toast.textContent = message;
  toast.classList.add("show");
  window.setTimeout(() => toast.classList.remove("show"), 2400);
}

function setBusy(button, busy, label) {
  button.disabled = busy;
  if (!button.dataset.label) button.dataset.label = button.querySelector("span")?.textContent || button.textContent;
  const target = button.querySelector("span") || button;
  target.textContent = busy ? label : button.dataset.label;
}

async function boot() {
  try {
    const data = await api("/api/review/me");
    enterApp(data.reviewer);
  } catch (error) {
    if (error.status !== 401) $("loginError").textContent = error.message;
    showLogin();
  }
}

function showLogin() {
  loginView.classList.remove("hidden");
  appView.classList.add("hidden");
  $("logoutButton").classList.add("hidden");
  $("sessionName").textContent = "";
}

function enterApp(reviewer) {
  state.reviewer = reviewer;
  loginView.classList.add("hidden");
  appView.classList.remove("hidden");
  $("logoutButton").classList.remove("hidden");
  $("sessionName").textContent = `${reviewer.displayName} · 已登录`;
  void Promise.all([loadStats(), loadSubmissions()]);
}

$("loginForm").addEventListener("submit", async event => {
  event.preventDefault();
  const button = event.submitter;
  $("loginError").textContent = "";
  setBusy(button, true, "正在验证…");
  try {
    const data = await api("/api/review/login", jsonOptions("POST", {
      displayName: $("displayName").value,
      inviteCode: $("inviteCode").value,
    }));
    $("inviteCode").value = "";
    enterApp(data.reviewer);
  } catch (error) {
    $("loginError").textContent = error.message;
  } finally {
    setBusy(button, false);
  }
});

$("logoutButton").addEventListener("click", async () => {
  try { await api("/api/review/logout", { method: "POST" }); } catch {}
  resetRecorder();
  state.reviewer = null;
  showLogin();
});

$("generateForm").addEventListener("submit", async event => {
  event.preventDefault();
  const button = $("generateButton");
  $("generateError").textContent = "";
  setBusy(button, true, "AI 正在准备句子…");
  try {
    const lessonValue = $("lessonNumber").value;
    const data = await api("/api/review/tasks/generate", jsonOptions("POST", {
      sampleMode: $("sampleMode").value,
      difficulty: $("difficulty").value,
      lessonNumber: lessonValue ? Number(lessonValue) : null,
    }));
    activateTask(data.task);
    showToast("新任务已生成");
  } catch (error) {
    $("generateError").textContent = error.message;
  } finally {
    setBusy(button, false);
  }
});

function activateTask(task) {
  resetRecorder();
  state.task = task;
  $("taskPanel").classList.remove("disabled-panel");
  $("emptyTask").classList.add("hidden");
  $("activeTask").classList.remove("hidden");
  $("taskModeBadge").textContent = `第 ${task.lessonNumber} 课 · ${modeLabels[task.sampleMode] || task.sampleMode}`;
  $("taskModeBadge").classList.remove("neutral");
  $("taskInstruction").textContent = task.instruction;
  $("targetText").value = task.targetText;
  $("targetTranslation").value = task.targetTranslation;
  $("readPromptText").value = task.readPromptText;
  $("readPromptTranslation").value = task.readPromptTranslation;
  $("readPromptGroup").classList.toggle("off-target-prompt", task.sampleMode === "off_target");
  renderWordRatings(task.words);
  resetResult();
  $("taskPanel").scrollIntoView({ behavior: "smooth", block: "start" });
}

async function saveTaskText() {
  if (!state.task) throw new Error("请先生成任务。");
  const targetText = $("targetText").value.trim();
  const targetTranslation = $("targetTranslation").value.trim();
  const readPromptText = $("readPromptText").value.trim();
  const readPromptTranslation = $("readPromptTranslation").value.trim();
  const data = await api(`/api/review/tasks/${encodeURIComponent(state.task.id)}`, jsonOptions("PATCH", {
    targetText, targetTranslation, readPromptText, readPromptTranslation,
  }));
  state.task = { ...state.task, targetText, targetTranslation, readPromptText, readPromptTranslation, words: data.words };
  renderWordRatings(data.words);
}

function supportedMimeType() {
  const candidates = ["audio/webm;codecs=opus", "audio/webm", "audio/mp4", "audio/ogg;codecs=opus"];
  return candidates.find(type => window.MediaRecorder?.isTypeSupported(type)) || "";
}

$("recordButton").addEventListener("click", async () => {
  $("recordError").textContent = "";
  if (state.recorder?.state === "recording") return;
  try {
    await saveTaskText();
    if (!navigator.mediaDevices?.getUserMedia || !window.MediaRecorder) throw new Error("当前浏览器不支持录音，请使用最新版 Chrome 或 Edge。");
    state.stream = await navigator.mediaDevices.getUserMedia({ audio: { echoCancellation: true, noiseSuppression: false, autoGainControl: false } });
    const mimeType = supportedMimeType();
    state.recorder = new MediaRecorder(state.stream, mimeType ? { mimeType, audioBitsPerSecond: 64_000 } : undefined);
    state.chunks = [];
    state.recorder.addEventListener("dataavailable", event => { if (event.data.size) state.chunks.push(event.data); });
    state.recorder.addEventListener("stop", finishRecording, { once: true });
    state.recorder.start(250);
    state.startedAt = Date.now();
    $("recordButton").classList.add("recording");
    $("stopButton").classList.remove("hidden");
    $("recordStatus").textContent = "正在录音";
    updateTimer();
    state.timerId = window.setInterval(updateTimer, 250);
  } catch (error) {
    stopStream();
    $("recordError").textContent = error.message;
  }
});

$("stopButton").addEventListener("click", stopRecording);

function updateTimer() {
  const elapsed = Math.min(30, (Date.now() - state.startedAt) / 1000);
  $("recordTimer").textContent = `${elapsed.toFixed(1)} 秒 / 30 秒`;
  if (elapsed >= 30) stopRecording();
}

function stopRecording() {
  if (state.recorder?.state === "recording") state.recorder.stop();
}

function finishRecording() {
  window.clearInterval(state.timerId);
  state.timerId = null;
  const type = state.recorder?.mimeType || state.chunks[0]?.type || "audio/webm";
  state.audioBlob = new Blob(state.chunks, { type });
  stopStream();
  if (state.audioUrl) URL.revokeObjectURL(state.audioUrl);
  state.audioUrl = URL.createObjectURL(state.audioBlob);
  $("audioPreview").src = state.audioUrl;
  $("audioPreview").classList.remove("hidden");
  $("recordButton").classList.remove("recording");
  $("stopButton").classList.add("hidden");
  $("recordStatus").textContent = "录音完成 · 可播放检查或重新录制";
  $("recordTimer").textContent = `${(state.audioBlob.size / 1024).toFixed(1)} KB`;
  enableRating();
}

function stopStream() {
  state.stream?.getTracks().forEach(track => track.stop());
  state.stream = null;
}

function resetRecorder() {
  if (state.recorder?.state === "recording") state.recorder.stop();
  stopStream();
  window.clearInterval(state.timerId);
  state.timerId = null;
  state.recorder = null;
  state.chunks = [];
  state.audioBlob = null;
  if (state.audioUrl) URL.revokeObjectURL(state.audioUrl);
  state.audioUrl = null;
  $("audioPreview").removeAttribute("src");
  $("audioPreview").classList.add("hidden");
  $("recordButton").classList.remove("recording");
  $("stopButton").classList.add("hidden");
  $("recordStatus").textContent = "准备录音";
  $("recordTimer").textContent = "最长 30 秒 · 建议距离麦克风 20 厘米";
  disableRating();
}

function enableRating() {
  $("ratingPanel").classList.remove("disabled-panel");
  $("ratingPanel").querySelector(".status-chip").textContent = "待人工判断";
  $("ratingPanel").querySelector(".status-chip").classList.remove("neutral");
  $("ratingEmpty").classList.add("hidden");
  $("ratingForm").classList.remove("hidden");
}

function disableRating() {
  $("ratingPanel").classList.add("disabled-panel");
  $("ratingPanel").querySelector(".status-chip").textContent = "先录音";
  $("ratingPanel").querySelector(".status-chip").classList.add("neutral");
  $("ratingEmpty").classList.remove("hidden");
  $("ratingForm").classList.add("hidden");
  $("consent").checked = false;
  $("notes").value = "";
  $("actualReadText").value = "";
}

function renderWordRatings(words) {
  const container = $("wordRatings");
  container.replaceChildren();
  words.forEach((word, index) => {
    const row = document.createElement("div");
    row.className = "word-rating";
    const number = document.createElement("span");
    number.className = "word-index";
    number.textContent = String(index + 1).padStart(2, "0");
    const label = document.createElement("strong");
    label.lang = "ru";
    label.textContent = word;
    const select = document.createElement("select");
    select.dataset.index = String(index);
    select.dataset.word = word;
    select.setAttribute("aria-label", `${word} 的人工判断`);
    ratingOptions.forEach(([value, text]) => {
      const option = document.createElement("option");
      option.value = value;
      option.textContent = text;
      select.append(option);
    });
    row.append(number, label, select);
    container.append(row);
  });
}

$("contentMatch").addEventListener("change", () => {
  const match = $("contentMatch").value;
  const suggested = { exact: 90, mostly: 75, partial: 45, off_target: 5, unintelligible: 0 }[match];
  $("overallScore").value = String(suggested);
  $("scoreOutput").textContent = String(suggested);
  if (match === "off_target" || match === "unintelligible") {
    const rating = match === "off_target" ? "substituted" : "wrong";
    $("wordRatings").querySelectorAll("select").forEach(select => { select.value = rating; });
  }
});

$("overallScore").addEventListener("input", event => { $("scoreOutput").textContent = event.target.value; });

$("ratingForm").addEventListener("submit", async event => {
  event.preventDefault();
  const button = $("submitButton");
  $("submitError").textContent = "";
  if (!state.task || !state.audioBlob) {
    $("submitError").textContent = "请先生成任务并完成录音。";
    return;
  }
  if (!$("consent").checked) {
    $("submitError").textContent = "请确认录音用于评分校准。";
    return;
  }
  setBusy(button, true, "上传并复评中…");
  const form = new FormData();
  const extension = state.audioBlob.type.includes("mp4") ? "m4a" : state.audioBlob.type.includes("ogg") ? "ogg" : "webm";
  form.set("task_id", state.task.id);
  form.set("audio", state.audioBlob, `review.${extension}`);
  form.set("content_match", $("contentMatch").value);
  form.set("overall_score", $("overallScore").value);
  form.set("actual_read_text", $("actualReadText").value);
  form.set("notes", $("notes").value);
  form.set("consent", "true");
  form.set("word_ratings", JSON.stringify([...$("wordRatings").querySelectorAll("select")].map(select => ({
    index: Number(select.dataset.index),
    word: select.dataset.word,
    rating: select.value,
  }))));
  try {
    const data = await api("/api/review/submissions", { method: "POST", body: form });
    showResult(data);
    await Promise.all([loadStats(), loadSubmissions()]);
    showToast("样本已保存并完成机器复评");
  } catch (error) {
    $("submitError").textContent = error.message;
  } finally {
    setBusy(button, false);
  }
});

function showResult(data) {
  $("resultPanel").classList.remove("disabled-panel");
  $("resultPanel").querySelector(".status-chip").textContent = "已完成";
  $("resultPanel").querySelector(".status-chip").classList.remove("neutral");
  $("resultEmpty").classList.add("hidden");
  $("resultContent").classList.remove("hidden");
  $("humanResult").textContent = String(data.humanScore);
  const machine = data.machineResult?.overall_score;
  $("machineResult").textContent = typeof machine === "number" ? String(Math.round(machine)) : "不评分";
  const delta = data.scoreDelta;
  $("deltaMessage").textContent = delta == null
    ? "机器认为当前证据不足，未给出分数；该样本将用于调整“不评分”门槛。"
    : Math.abs(delta) <= 10
      ? `机器与人工相差 ${Math.abs(delta).toFixed(1)} 分，处于可接受范围。`
      : `机器比人工${delta > 0 ? "高" : "低"} ${Math.abs(delta).toFixed(1)} 分，已记录为重点校准样本。`;
  $("resultPanel").scrollIntoView({ behavior: "smooth", block: "center" });
}

function resetResult() {
  $("resultPanel").classList.add("disabled-panel");
  $("resultPanel").querySelector(".status-chip").textContent = "等待提交";
  $("resultPanel").querySelector(".status-chip").classList.add("neutral");
  $("resultEmpty").classList.remove("hidden");
  $("resultContent").classList.add("hidden");
}

$("nextTaskButton").addEventListener("click", () => {
  state.task = null;
  resetRecorder();
  resetResult();
  $("taskPanel").classList.add("disabled-panel");
  $("emptyTask").classList.remove("hidden");
  $("activeTask").classList.add("hidden");
  $("taskModeBadge").textContent = "等待任务";
  $("taskModeBadge").classList.add("neutral");
  $("generateForm").scrollIntoView({ behavior: "smooth", block: "center" });
});

async function loadStats() {
  try {
    const stats = await api("/api/review/stats");
    $("sampleCount").textContent = String(stats.samples);
    $("reviewerCount").textContent = String(stats.reviewers);
    $("offTargetCount").textContent = String(stats.modes?.off_target || 0);
    $("averageScore").textContent = stats.averageHumanScore == null ? "—" : String(stats.averageHumanScore);
    $("progressValue").textContent = String(stats.samples);
    $("progressRing").style.setProperty("--progress", `${Math.min(100, stats.samples / stats.targetSamples * 100)}%`);
  } catch (error) {
    if (error.status === 401) showLogin();
  }
}

async function loadSubmissions() {
  try {
    const data = await api("/api/review/submissions");
    renderSubmissions(data.submissions || []);
  } catch (error) {
    if (error.status === 401) showLogin();
  }
}

function renderSubmissions(submissions) {
  const list = $("submissionList");
  list.replaceChildren();
  if (!submissions.length) {
    const empty = document.createElement("p");
    empty.className = "muted";
    empty.style.padding = "24px";
    empty.textContent = "暂无样本。完成第一条录音后会显示在这里。";
    list.append(empty);
    return;
  }
  submissions.forEach(item => {
    const row = document.createElement("article");
    row.className = "submission-item";
    const title = document.createElement("div");
    const h3 = document.createElement("h3");
    h3.lang = "ru";
    h3.textContent = item.targetText;
    const meta = document.createElement("p");
    const lesson = item.lessonNumber ? `第 ${item.lessonNumber} 课 · ` : "";
    meta.textContent = `${lesson}${item.reviewer.displayName} · ${modeLabels[item.sampleMode] || item.sampleMode} · ${new Date(item.createdAt).toLocaleString("zh-CN")}`;
    title.append(h3, meta);
    const human = metric("人工评分", String(item.overallScore));
    const machineScore = item.machineResult?.overall_score;
    const machine = metric("机器评分", typeof machineScore === "number" ? String(Math.round(machineScore)) : "不评分");
    const player = document.createElement("audio");
    player.controls = true;
    player.preload = "none";
    player.src = item.audioUrl;
    const media = document.createElement("div");
    media.className = "submission-media";
    media.append(player);
    if (item.reviewer.id === state.reviewer?.id) {
      const remove = document.createElement("button");
      remove.type = "button";
      remove.className = "delete-sample";
      remove.textContent = "删除我的录音与标注";
      remove.addEventListener("click", async () => {
        if (!window.confirm("确定永久删除这条录音和标注吗？此操作无法撤销。")) return;
        remove.disabled = true;
        try {
          await api(`/api/review/submissions/${encodeURIComponent(item.id)}`, { method: "DELETE" });
          row.remove();
          await loadStats();
          showToast("录音与标注已删除");
        } catch (error) {
          remove.disabled = false;
          showToast(error.message);
        }
      });
      media.append(remove);
    }
    row.append(title, human, machine, media);
    list.append(row);
  });
}

function metric(label, value) {
  const div = document.createElement("div");
  div.className = "metric";
  const span = document.createElement("span");
  span.textContent = label;
  const strong = document.createElement("strong");
  strong.textContent = value;
  div.append(span, strong);
  return div;
}

$("refreshButton").addEventListener("click", () => { void Promise.all([loadStats(), loadSubmissions()]); });

window.addEventListener("beforeunload", () => {
  stopStream();
  if (state.audioUrl) URL.revokeObjectURL(state.audioUrl);
});

void boot();
