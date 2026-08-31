# RusMorph 俄语语音后端

FastAPI 后端提供：

- `POST /api/asr/transcribe`：俄语文件转写、词时间戳、独立语言/ASR 置信度和搜索判定。
- `WS /api/asr/stream`：`start`、二进制音频分片、`partial`、`stop`、`cancel` 协议。
- `POST /api/examples/preprocess`：上下文重音、音节和元音核预处理。
- `POST /api/pronunciation/analyze`：已知文本跟读的 ASR 校验、MFA 对齐、Praat 声学分析、重音/完整度/流利度与中文反馈。
- `GET /health`：运行依赖与当前评分能力。

## 可靠性边界

程序不会根据文件名或文本编造发音分数。朗读评分必须依次通过 FFmpeg、真实 ASR、MFA
强制对齐和 Parselmouth/Praat 声学提取。MFA 失败返回 `alignment_failed`，不返回音节分数；
整体证据低于阈值返回 `score_available=false`。

当前 MVP 没有声学模型的音素后验概率，因此不是 GOP。音节分数明确标记
`score_method=alignment_acoustic_proxy`，使用对齐置信度、时长、强度、F0 与 F1/F2
可用性，置信度上限为 0.72。真正的 GOP 属于下一阶段，接入音素后验模型后才应把
`score_method` 改为 `gop`。重音检测同时使用时长、强度和 F0，不以“最响音节”单独判断。

## 本地安装

推荐 Python 3.11。MFA 官方推荐 Conda：

```powershell
conda create -n rusmorph-speech -c conda-forge python=3.11 ffmpeg montreal-forced-aligner
conda activate rusmorph-speech
pip install -r requirements.txt
Copy-Item .env.example .env
.\scripts\download_models.ps1
uvicorn app.main:app --host 0.0.0.0 --port 8000
```

模型、路径和设备全部通过 `SPEECH_` 环境变量配置，见 `.env.example`。首次 ASR 会按
`SPEECH_ASR_MODEL` 下载 faster-whisper 权重；生产环境应预下载并将该变量改成本地模型目录。

CPU：

```text
SPEECH_ASR_DEVICE=cpu
SPEECH_ASR_COMPUTE_TYPE=int8
```

NVIDIA GPU：

```text
SPEECH_ASR_DEVICE=cuda
SPEECH_ASR_COMPUTE_TYPE=float16
```

## Docker

```powershell
Copy-Item .env.example .env
docker compose build
docker compose run --rm speech-cpu mfa model download acoustic russian_mfa
docker compose run --rm speech-cpu mfa model download dictionary russian_mfa
docker compose run --rm speech-cpu mfa model download g2p russian_mfa
docker compose up speech-cpu
```

GPU 使用：

```powershell
docker compose --profile gpu up speech-gpu
```

需要 NVIDIA Container Toolkit。CPU 与 GPU 服务不能同时绑定本机 8000 端口。

## API 示例

```powershell
curl.exe -X POST http://localhost:8000/api/asr/transcribe `
  -F "audio=@sample_audio/russian-word.wav" `
  -F "language=ru" `
  -F "search_mode=true"

curl.exe -X POST http://localhost:8000/api/pronunciation/analyze `
  -F "audio=@sample_audio/reading.wav" `
  -F "target_text=Я изучаю русский язык." `
  -F "difficulty=beginner"

curl.exe -X POST http://localhost:8000/api/examples/preprocess `
  -H "Content-Type: application/json" `
  -d '{"text":"Я изучаю русский язык."}'
```

WebSocket 客户端先发送 `{"type":"start","format":"webm"}`，再发送同一录音容器的二进制
分片。需要临时转写时发送 `{"type":"partial"}`；结束发送 `{"type":"stop"}`。服务端实际调用
ASR 后返回 `partial`/`final`。取消发送 `{"type":"cancel"}`。

## 测试

```powershell
pip install -r requirements-test.txt
pytest -q
python scripts/generate_sample_audio.py
```

自动化测试用注入的确定性 ASR 替身验证 API 控制逻辑，不把合成音当作真实俄语识别准确率
证据。`generate_sample_audio.py` 生成三段 CC0 诊断录音（静音、过短、低音量）。真实模型
验收应另行录制至少一个清晰词、一个清晰句子和一个高噪声句子；录制者需明确同意用于测试。

## 数据与隐私

- 上传格式、大小、时长均受限；外部命令使用参数数组，不拼接 shell 字符串。
- 原始上传与规范化音频仅保存在 `temp/`，请求结束后删除；WebSocket 断开会清空内存分片。
- 默认不保存录音、分析记录或用户学习记录。日志不写原始音频、完整音频字节或访问令牌。
- 若产品以后允许“保存练习”，必须通过独立显式同意，并将原始音频、分析结果和学习记录分表保存。

## 模型许可与降级

- faster-whisper：MIT；具体 Whisper 权重的许可与模型卡随所选模型检查。
- Montreal Forced Aligner：MIT；`russian_mfa` 模型的许可和引用以 MFA 模型仓库元数据为准。
- Praat/Parselmouth：GPL-3.0；分发产品前需由法务确认组合分发义务。
- RUAccent：以安装版本仓库中的许可为准，商用前必须复核；不可用时仅对内置已校验词标重音并返回警告。

降级原则：ASR 不可用返回 503；重音模型不可用可保守降级并警告；MFA 或 Parselmouth
不可用则拒绝音节评分；没有 GOP 后验模型时只返回明确标记的声学代理分数。
