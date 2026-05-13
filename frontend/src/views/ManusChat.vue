<template>
  <PageShell
    eyebrow="Tool Workshop"
    title="SyManus Tool Agent"
    subtitle="Engineering demos for fixed quality gates, bounded tool calls, generated artifacts, and traceable execution."
  >
    <section class="tool-demo-layout">
      <div class="tool-chat-column">
        <div class="tool-demo-intro">
          <p>
            SyManus is the bounded ReAct tool agent behind Wayfinder Guild. The public workshop replays real engineering runs; Owner mode can run the live backend tools for controlled demos.
          </p>
          <label class="inline-toggle">
            <input v-model="liveRunEnabled" type="checkbox" :disabled="!ownerEnabled" />
            <span>Live Run</span>
          </label>
          <p v-if="liveRunNotice" class="boundary-note">{{ liveRunNotice }}</p>
        </div>

        <ManusDemoPromptBoard
          :stable-demo-examples="stableDemoExamples"
          :live-task-examples="liveTaskExamples"
          :is-streaming="isStreaming"
          :public-demo-mode="isPublicDemoMode"
          :live-task-heading="liveTaskHeading"
          :live-task-subheading="liveTaskSubheading"
          :live-task-copy="liveTaskCopy"
          :stable-demo-subheading="stableDemoSubheading"
          :stable-demo-copy="stableDemoCopy"
          @stable-demo="startExample"
          @live-task="fillLiveTask"
        />

        <ChatWindow
          ref="toolChat"
          :title="toolChatTitle"
          sse-path="/travel/manus/chat"
          storage-key="wayfinder.tool.chat"
          :placeholder="toolChatPlaceholder"
          :empty-text="toolChatEmptyText"
          clear-label="New Chat"
          send-label="Send"
          streaming-label="Running"
          :query-params="{ liveMode: canUseLiveRun }"
          :local-responder="toolLocalResponder"
          @submitted="handleSubmitted"
          @stream-start="handleStreamStart"
          @streaming="handleStreaming"
          @artifact="handleArtifact"
          @completed="handleCompleted"
          @failed="handleFailed"
          @greeting="handleGreeting"
          @local-response="handleLocalResponse"
          @cleared="clearSession"
        />
      </div>

      <aside class="tool-status-panel">
        <div class="status-card">
          <p class="area-kicker">Demo Status</p>
          <h2>{{ currentTask || 'No active task' }}</h2>
          <dl class="task-state-grid">
            <div>
              <dt>Mode</dt>
              <dd>{{ taskMode }}</dd>
            </div>
            <div>
              <dt>Status</dt>
              <dd><span :class="['status-pill', statusClass]">{{ taskStatus }}</span></dd>
            </div>
          </dl>
        </div>

        <ArtifactSideList :artifacts="recentArtifacts" />

        <div class="trace-panel">
          <div class="timeline-head">
            <strong>Execution Trace</strong>
            <span>{{ taskMode }}</span>
          </div>
          <div class="timeline tool-trace">
            <article
              v-for="step in executionSteps"
              :key="step.id"
              :class="['timeline-item', step.state]"
            >
              <span class="timeline-dot"></span>
              <div>
                <div class="timeline-head">
                  <strong>{{ step.title }}</strong>
                  <span>{{ step.status }}</span>
                </div>
                <p>{{ step.description }}</p>
              </div>
            </article>
          </div>
        </div>
      </aside>
    </section>
  </PageShell>
</template>

<script>
import axios, { isOwnerVerified, validateOwnerToken } from '../api'
import ChatWindow from '../components/ChatWindow.vue'
import PageShell from '../components/common/PageShell.vue'
import ArtifactSideList from '../components/manus/ArtifactSideList.vue'
import ManusDemoPromptBoard from '../components/manus/ManusDemoPromptBoard.vue'
import { artifactsFromResult, renderDemoResult } from '../utils/manusDemoRenderer'

const STORAGE_KEY = 'wayfinder.tool.session'

const MODE_IDLE = 'Ready'
const MODE_RECORDED = 'Recorded Demo'
const MODE_LIVE_DEMO = 'Live Demo'
const MODE_PUBLIC = MODE_RECORDED
const MODE_LIVE = 'Live Tool Task'
const MODE_EXAMPLE = 'Example Prompt'
const DEFAULT_DEMO_STATUS = {
  demoMode: true,
  liveManusAvailable: false,
  searchAvailable: false,
  imageSearchAvailable: false
}

const IDLE_STEPS = [
  {
    id: 'task',
    title: 'Task framed',
    status: 'Ready',
    state: 'started',
    description: 'Choose a Recorded Demo or send one bounded live tool task.'
  },
  {
    id: 'tool',
    title: 'Tool selected',
    status: 'Queued',
    state: 'skipped',
    description: 'Public demos replay fixed results; Owner demos can run backend tools.'
  },
  {
    id: 'run',
    title: 'Backend executed',
    status: 'Queued',
    state: 'skipped',
    description: 'Recorded mode returns fixed output; Owner mode runs a fixed command, file/PDF/image generator, or live tool call.'
  },
  {
    id: 'result',
    title: 'Result delivered',
    status: 'Queued',
    state: 'skipped',
    description: 'The UI shows recorded output, or Owner-only expiring artifact preview and download links.'
  }
]

const LOCAL_RESPONSES = {
  greeting: 'Hi, I am the SyManus Tool Agent. Start with a Recorded Demo, or send one explicit live tool task.',
  unsupportedDownload: 'This public live task area does not search for or download arbitrary resumes, CVs, private files, or unspecified external files. Use Portfolio Brief Pack for the interview artifact demo, or provide a concrete safe URL for a bounded download task.',
  resumeRedirect: 'For this showcase, generic resume requests are routed to Portfolio Brief Pack. It generates Wayfinder Guild Markdown and PDF artifacts that describe the real Travel Agent, RAG, trace, eval, guardrail, and SyManus boundaries.'
}

const ZH_RESPONSES = {
  greeting: '\u4f60\u597d\uff0c\u6211\u662f SyManus Tool Agent\u3002\u53ef\u4ee5\u5148\u8dd1 Stable Engineering Demo\uff0c\u4e5f\u53ef\u4ee5\u53d1\u9001\u4e00\u4e2a\u660e\u786e\u7684\u53d7\u9650 live tool task\u3002',
  unsupportedDownload: '\u8fd9\u4e2a public live task \u533a\u57df\u4e0d\u4f1a\u641c\u7d22\u6216\u4e0b\u8f7d\u4efb\u610f\u7b80\u5386\u3001CV\u3001\u79c1\u6709\u6587\u4ef6\u6216\u672a\u6307\u5b9a\u5916\u90e8\u6587\u4ef6\u3002\u8bf7\u4f7f\u7528 Portfolio Brief Pack\uff0c\u6216\u63d0\u4f9b\u4e00\u4e2a\u5177\u4f53\u4e14\u5b89\u5168\u7684 URL \u4f5c\u4e3a\u53d7\u9650\u4e0b\u8f7d\u4efb\u52a1\u3002',
  resumeRedirect: '\u8fd9\u4e2a\u5c55\u793a\u9875\u4f1a\u628a\u6cdb\u5316\u7b80\u5386\u8bf7\u6c42\u5f15\u5bfc\u5230 Portfolio Brief Pack\u3002\u5b83\u4f1a\u751f\u6210 Wayfinder Guild \u7684 Markdown \u548c PDF artifact\uff0c\u7528\u771f\u5b9e\u9879\u76ee\u80fd\u529b\u8bf4\u660e Travel Agent\u3001RAG\u3001Trace\u3001Eval\u3001Guardrails \u548c SyManus \u5de5\u5177\u8fb9\u754c\u3002'
}

function cloneSteps() {
  return IDLE_STEPS.map((step) => ({ ...step }))
}

export default {
  name: 'ManusChat',
  components: { ChatWindow, PageShell, ArtifactSideList, ManusDemoPromptBoard },
  data() {
    return {
      currentTask: '',
      taskStatus: 'Ready',
      taskMode: MODE_IDLE,
      executionSteps: cloneSteps(),
      isStreaming: false,
      recentArtifacts: [],
      ownerEnabled: isOwnerVerified(),
      liveRunEnabled: false,
      liveRunNotice: '',
      demoStatus: { ...DEFAULT_DEMO_STATUS },
      stableDemoExamples: [
        {
          label: 'Wayfinder Doctor',
          description: 'skills, RPG, evals, prompts, RAG docs, naming',
          mode: MODE_RECORDED,
          demoType: 'doctor',
          prompt: 'Run the fixed Wayfinder Doctor engineering check.'
        },
        {
          label: 'Backend Targeted Tests',
          description: 'fixed Maven quality gate',
          mode: MODE_RECORDED,
          demoType: 'backend-tests',
          prompt: 'Run the fixed backend targeted Maven tests.'
        },
        {
          label: 'Java Runtime Check',
          description: 'allowlisted java -version',
          mode: MODE_RECORDED,
          demoType: 'java-runtime',
          prompt: 'Run the fixed Java runtime version check.'
        },
        {
          label: 'Portfolio Brief Pack',
          description: 'Wayfinder MD + PDF artifacts',
          mode: MODE_RECORDED,
          demoType: 'portfolio-brief-pack',
          prompt: 'Generate the Wayfinder Guild Portfolio Brief Pack.'
        },
        {
          label: 'Trace Card Image',
          description: 'fixed Wayfinder trace PNG',
          mode: MODE_RECORDED,
          demoType: 'trace-card-image',
          prompt: 'Generate the fixed Wayfinder trace card image.'
        }
      ],
      liveTaskExamples: [
        {
          label: 'Echo Health',
          description: 'example prompt; requires live model',
          prompt: 'Run this backend tool task now: echo SyManus live health check'
        },
        {
          label: 'Write Wayfinder Note',
          description: 'example prompt; requires live model',
          prompt: 'Run this backend tool task now: write demo-note.txt with a short Wayfinder note'
        },
        {
          label: 'Kyoto Image Search',
          description: 'requires Pexels key + network',
          prompt: 'Search for one image of Kyoto station and download it as a safe artifact'
        }
      ]
    }
  },
  computed: {
    isPublicDemoMode() {
      return !this.canUseLiveRun
    },
    canUseLiveRun() {
      return this.ownerEnabled && this.liveRunEnabled
    },
    stableDemoSubheading() {
      return this.canUseLiveRun ? 'Live Run' : 'Real run replay'
    },
    stableDemoCopy() {
      return this.canUseLiveRun
        ? 'Runs fixed backend tools on your server: checks, Maven tests, runtime verification, and artifact generation.'
        : 'Recorded from real local runs: project checks, targeted tests, runtime verification, portfolio artifacts, and trace-card generation.'
    },
    liveTaskHeading() {
      return this.isPublicDemoMode ? 'Live Tool Task Examples' : 'Live Tool Tasks'
    },
    liveTaskSubheading() {
      return this.isPublicDemoMode ? 'Disabled in public demo mode' : 'Real SyManus tool loop'
    },
    liveTaskCopy() {
      if (this.isPublicDemoMode) {
        return 'These buttons only fill example prompts in public demo mode. Sending them returns the boundary message; real execution requires Owner Live Mode with model/API keys.'
      }
      const searchNote = this.demoStatus.searchAvailable ? 'live search is configured' : 'live search needs Tavily configuration'
      const imageNote = this.demoStatus.imageSearchAvailable ? 'image search is configured' : 'image search needs a Pexels key'
      return `These prompts use the real SyManus loop. Model/API access is enabled; ${searchNote}, and ${imageNote}.`
    },
    toolChatTitle() {
      return this.isPublicDemoMode ? 'Recorded Run Replay' : 'Live Tool Task'
    },
    toolChatPlaceholder() {
      if (this.isPublicDemoMode) {
        return 'Run a recorded engineering demo, or fill an example prompt to see the live boundary.'
      }
      return 'Enter one bounded live task, for example: Run this backend tool task now: echo SyManus live health check'
    },
    toolChatEmptyText() {
      return this.isPublicDemoMode
        ? 'Run a recorded live-run replay, or fill an example prompt to see the live boundary.'
        : 'Run a Live Demo, or send one explicit live tool task.'
    },
    statusClass() {
      return this.taskStatus.toLowerCase().replace(/\s+/g, '-')
    }
  },
  mounted() {
    this.restoreSession()
    this.fetchDemoStatus()
    window.addEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
  },
  beforeUnmount() {
    window.removeEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
  },
  methods: {
    refreshOwnerState() {
      this.ownerEnabled = isOwnerVerified()
      if (!this.ownerEnabled) {
        this.liveRunEnabled = false
      }
      this.fetchDemoStatus()
    },
    async fetchDemoStatus() {
      try {
        const { data } = await axios.get('/travel/demo-status')
        this.demoStatus = { ...DEFAULT_DEMO_STATUS, ...data }
      } catch (error) {
        this.demoStatus = { ...DEFAULT_DEMO_STATUS }
      }
    },
    startExample(example) {
      this.taskMode = this.canUseLiveRun ? MODE_LIVE_DEMO : (example.mode || MODE_RECORDED)
      this.runStableDemo(example)
    },
    fillLiveTask(example) {
      this.currentTask = example.label
      this.taskMode = this.isPublicDemoMode ? MODE_EXAMPLE : MODE_LIVE
      this.taskStatus = 'Ready'
      this.$refs.toolChat?.fillInput(example.prompt)
      this.saveSession()
    },
    async runStableDemo(example) {
      if (this.isStreaming) return
      this.currentTask = example.label
      this.taskMode = this.canUseLiveRun ? MODE_LIVE_DEMO : MODE_RECORDED
      this.taskStatus = 'Running'
      this.isStreaming = true
      this.recentArtifacts = []
      this.executionSteps = cloneSteps()
      this.markStep('task', 'Completed', 'completed')
      this.markStep('tool', 'Selected', 'completed')
      this.markStep('run', 'Running', 'started')
      this.saveSession()

      try {
        const endpoint = this.canUseLiveRun ? '/travel/manus/demo-tool' : '/travel/manus/recorded-demo-tool'
        const { data } = await axios.post(endpoint, { type: example.demoType }, { timeout: this.canUseLiveRun ? 180000 : 30000 })
        const rendered = renderDemoResult(data, { baseUrl: axios.defaults.baseURL })
        this.$refs.toolChat?.appendLocalExchange(example.prompt, rendered.text, rendered.html)
        artifactsFromResult(data).forEach((artifact) => this.addArtifact(artifact))
        this.taskStatus = data?.status === 'error' ? 'Failed' : 'Completed'
        this.markStep('run', this.taskStatus === 'Failed' ? 'Failed' : 'Completed', this.taskStatus === 'Failed' ? 'failed' : 'completed')
        const resultStatus = artifactsFromResult(data).length ? 'Files Ready' : (this.canUseLiveRun ? this.taskStatus : 'Recorded Result')
        this.markStep('result', resultStatus, this.taskStatus === 'Failed' ? 'failed' : 'completed')
      } catch (error) {
        if (this.canUseLiveRun && error?.response?.status === 403) {
          await validateOwnerToken()
          this.ownerEnabled = isOwnerVerified()
          this.liveRunEnabled = false
          this.liveRunNotice = 'Live Run was disabled; showing the recorded replay.'
          const { data } = await axios.post('/travel/manus/recorded-demo-tool', { type: example.demoType }, { timeout: 30000 })
          const rendered = renderDemoResult(data, { baseUrl: axios.defaults.baseURL })
          this.$refs.toolChat?.appendLocalExchange(example.prompt, `Live unavailable. ${rendered.text}`, rendered.html)
          this.taskStatus = 'Completed'
          this.markStep('run', 'Recorded Replay', 'completed')
          this.markStep('result', 'Recorded Result', 'completed')
          return
        }
        const message = error?.response?.data?.message || error?.message || 'The backend demo endpoint is unavailable.'
        this.$refs.toolChat?.appendLocalExchange(example.prompt, `Demo failed: ${message}`)
        this.taskStatus = 'Failed'
        this.markStep('run', 'Failed', 'failed')
        this.markStep('result', 'Failed', 'failed')
      } finally {
        this.isStreaming = false
        this.saveSession()
      }
    },
    toolLocalResponder(text) {
      const language = this.preferredLanguage(text)
      if (this.isGenericResumeRequest(text)) {
        return {
          intent: 'portfolio-brief-redirect',
          content: language === 'zh' ? ZH_RESPONSES.resumeRedirect : LOCAL_RESPONSES.resumeRedirect
        }
      }
      if (this.isUnsupportedPublicDownload(text)) {
        return {
          intent: 'unsupported-public-download',
          content: language === 'zh' ? ZH_RESPONSES.unsupportedDownload : LOCAL_RESPONSES.unsupportedDownload
        }
      }
      if (this.isGreetingOnly(text)) {
        return {
          intent: 'greeting',
          content: language === 'zh' ? ZH_RESPONSES.greeting : LOCAL_RESPONSES.greeting
        }
      }
      if (!this.canUseLiveRun) {
        return {
          intent: 'live-boundary',
          content: 'Live Tool Tasks are unlocked only after Owner verification and the Live Run switch is enabled. Recorded demos remain available above.'
        }
      }
      return null
    },
    preferredLanguage(text) {
      return /[\u4e00-\u9fff]/.test(String(text || '')) ? 'zh' : 'en'
    },
    isGreetingOnly(text) {
      const normalized = String(text || '').trim().toLowerCase().replace(/[!?.\u3002\uff01\uff1f\uff0c,\s]/g, '')
      return /^(hi|hello|hey|\u4f60\u597d|\u60a8\u597d)$/.test(normalized)
    },
    isGenericResumeRequest(text) {
      const value = String(text || '').trim().toLowerCase()
      const looksLikeResume = /resume|cv|\u7b80\u5386/.test(value)
      const asksGenerate = /generate|create|make|write|\u751f\u6210|\u5199/.test(value)
      const hasConcreteLivePrefix = value.includes('run this backend tool task now')
      const mentionsPortfolio = /portfolio|brief|wayfinder/.test(value)
      return looksLikeResume && asksGenerate && !hasConcreteLivePrefix && !mentionsPortfolio
    },
    isUnsupportedPublicDownload(text) {
      const value = String(text || '').trim().toLowerCase()
      const asksDownload = /download|save|\u4e0b\u8f7d/.test(value)
      const asksGenerate = /generate|create|make|write|\u751f\u6210|\u5199/.test(value)
      const looksLikeRestrictedFile = /resume|cv|private file|external file|\u7b80\u5386|\u79c1\u6709\u6587\u4ef6|\u5916\u90e8\u6587\u4ef6/.test(value)
      const hasUrl = /https?:\/\//.test(value)
      return asksDownload && looksLikeRestrictedFile && !asksGenerate && !hasUrl
    },
    handleSubmitted({ message }) {
      this.currentTask = this.displayTask(message)
      this.taskMode = this.isPublicDemoMode ? MODE_EXAMPLE : MODE_LIVE
      this.taskStatus = 'Planning'
      this.isStreaming = true
      this.recentArtifacts = []
      this.executionSteps = cloneSteps()
      this.markStep('task', 'Started', 'started')
      this.saveSession()
    },
    handleStreamStart() {
      this.taskStatus = 'Running'
      this.markStep('tool', 'Selected', 'completed')
      this.markStep('run', 'Running', 'started')
      this.saveSession()
    },
    handleStreaming() {
      this.taskStatus = 'Streaming'
      this.markStep('task', 'Completed', 'completed')
      this.markStep('tool', 'Completed', 'completed')
      this.markStep('run', 'Completed', 'completed')
      this.markStep('result', 'Streaming', 'started')
      this.saveSession()
    },
    handleArtifact({ artifact }) {
      this.addArtifact(artifact)
      this.markStep('result', artifact ? 'File Ready' : 'Streaming', artifact ? 'completed' : 'started')
      this.saveSession()
    },
    handleCompleted() {
      this.taskStatus = 'Completed'
      this.isStreaming = false
      this.markStep('result', 'Completed', 'completed')
      this.saveSession()
    },
    handleFailed() {
      this.taskStatus = 'Failed'
      this.isStreaming = false
      this.markStep('run', 'Check backend', 'failed')
      this.markStep('result', 'Failed', 'failed')
      this.saveSession()
    },
    handleGreeting() {
      this.currentTask = ''
      this.taskMode = MODE_IDLE
      this.taskStatus = 'Ready'
      this.isStreaming = false
      this.executionSteps = cloneSteps()
      this.saveSession()
    },
    handleLocalResponse() {
      this.handleGreeting()
    },
    clearSession() {
      sessionStorage.removeItem(STORAGE_KEY)
      this.currentTask = ''
      this.taskMode = MODE_IDLE
      this.taskStatus = 'Ready'
      this.executionSteps = cloneSteps()
      this.recentArtifacts = []
      this.isStreaming = false
      this.saveSession()
    },
    markStep(id, status, state) {
      this.executionSteps = this.executionSteps.map((step) => (
        step.id === id ? { ...step, status, state } : step
      ))
    },
    displayTask(message) {
      const allExamples = [...this.stableDemoExamples, ...this.liveTaskExamples]
      const matched = allExamples.find((example) => example.prompt === message)
      return matched ? matched.label : message
    },
    restoreSession() {
      try {
        const raw = sessionStorage.getItem(STORAGE_KEY)
        if (!raw) return
        const saved = JSON.parse(raw)
        this.currentTask = saved.currentTask || ''
        this.taskMode = saved.taskMode || MODE_IDLE
        this.taskStatus = saved.taskStatus || 'Ready'
        this.executionSteps = Array.isArray(saved.executionSteps) && saved.executionSteps.length
          ? saved.executionSteps
          : cloneSteps()
        this.recentArtifacts = Array.isArray(saved.recentArtifacts)
          ? saved.recentArtifacts
          : (saved.lastArtifact ? [saved.lastArtifact] : [])
        this.isStreaming = false
      } catch (error) {
        console.warn('Could not restore tool agent session.', error)
      }
    },
    saveSession() {
      sessionStorage.setItem(STORAGE_KEY, JSON.stringify({
        taskMode: this.taskMode,
        taskStatus: this.taskStatus,
        currentTask: this.currentTask,
        executionSteps: this.executionSteps,
        recentArtifacts: this.recentArtifacts
      }))
    },
    addArtifact(artifact) {
      if (!artifact?.artifactId) return
      const next = [
        artifact,
        ...this.recentArtifacts.filter((item) => item.artifactId !== artifact.artifactId)
      ]
      this.recentArtifacts = next.slice(0, 5)
    }
  }
}
</script>
