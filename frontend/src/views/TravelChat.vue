<template>
  <PageShell
    eyebrow="Travel Cabin"
    title="Travel Agent"
    subtitle="Chat with the travel Agent, or request a structured TravelPlan for UI-ready cards."
  >
    <div class="travel-agent-layout">
      <section class="travel-chat-column">
        <h2>Streaming Chat</h2>
        <div class="form-actions chat-live-actions">
          <label class="inline-toggle">
            <input v-model="liveChatEnabled" type="checkbox" :disabled="!ownerEnabled" @change="handleLiveChatToggle" />
            <span>Live Chat</span>
          </label>
        </div>
        <ChatWindow
          title="Travel Agent"
          sse-path="/travel/chat/stream"
          storage-key="wayfinder.travel.chat"
          clear-label="New Chat"
          :query-params="chatQueryParams"
          :stream-error-text="chatStreamErrorText"
          :local-responder="travelLocalResponder"
          :hidden-line-prefixes="['PLAN_DRAFT:']"
          @submitted="handleChatSubmitted"
          @completed="handleChatCompleted"
          @failed="handleChatFailed"
          @cleared="handleChatCleared"
        />
        <p v-if="liveChatNotice" class="draft-notice">{{ liveChatNotice }}</p>
        <p v-if="planLoading" class="draft-notice">{{ planLoadingChatNotice }}</p>
        <div v-if="chatDraft" class="chat-draft-action">
          <div>
            <strong>{{ chatDraftReadyTitle }}</strong>
            <p>{{ chatDraft }}</p>
          </div>
          <button type="button" :disabled="planLoading" @click="generatePlanFromChatDraft">
            {{ chatDraftActionLabel }}
          </button>
        </div>
      </section>

      <section class="structured-plan-column">
        <h2>Structured Plan</h2>
        <form class="prompt-form" @submit.prevent="generatePlan">
          <textarea
            v-model="planMessage"
            rows="5"
            placeholder="Example: Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget."
          ></textarea>
          <div class="form-actions">
            <button type="submit" :disabled="planLoading || !planMessage.trim()">
              {{ planLoading ? 'Planning...' : 'Generate TravelPlan' }}
            </button>
            <label class="inline-toggle">
              <input v-model="livePlanEnabled" type="checkbox" :disabled="!ownerEnabled || planLoading" />
              <span>Live TravelPlan</span>
            </label>
            <button
              v-if="chatDraft"
              type="button"
              class="secondary-button"
              :disabled="planLoading"
              @click="generatePlanFromChatDraft"
            >
              {{ chatDraftActionLabel }}
            </button>
            <button type="button" class="secondary-button" :disabled="planLoading" @click="clearPlanSession">
              New Demo
            </button>
          </div>
          <p v-if="livePlanNotice" class="draft-notice">{{ livePlanNotice }}</p>
          <p v-if="chatDraftNotice" class="draft-notice">{{ chatDraftNotice }}</p>
        </form>

        <StateBlock
          v-if="planLoading"
          type="loading"
          title="Planning voyage"
          :message="planLoadingMessage"
        />

        <StateBlock v-if="planError" type="error" title="Plan request failed" :message="planError" />

        <TravelPlanCards
          :plan="plan"
          :plan-message="planMessage"
          :plan-chat-id="planChatId"
          :score-loading="scoreLoading"
          :score-error="scoreError"
          :score-result="scoreResult"
          :trace-link-label="traceLinkLabel"
          @score-current-plan="scoreCurrentPlan"
        />
      </section>
    </div>
  </PageShell>
</template>

<script>
import api, { isOwnerVerified, validateOwnerToken } from '../api'
import ChatWindow from '../components/ChatWindow.vue'
import PageShell from '../components/common/PageShell.vue'
import StateBlock from '../components/common/StateBlock.vue'
import TravelPlanCards from '../components/travel/TravelPlanCards.vue'

const DEFAULT_PLAN_MESSAGE = 'Plan a relaxed 5-day family trip from Shanghai to Kyoto with a 15000 CNY budget.'
const DEFAULT_DEMO_STATUS = {
  demoMode: true,
  liveManusAvailable: false,
  searchAvailable: false,
  imageSearchAvailable: false
}

export default {
  name: 'TravelChat',
  components: { ChatWindow, PageShell, StateBlock, TravelPlanCards },
  data() {
    return {
      planMessage: DEFAULT_PLAN_MESSAGE,
      plan: null,
      planChatId: '',
      planLoading: false,
      planError: '',
      chatDraft: '',
      chatDraftNotice: '',
      chatDraftLanguage: 'en',
      activePlanStartedAt: '',
      planSessionPoller: null,
      scoreLoading: false,
      scoreError: '',
      scoreResult: null,
      ownerEnabled: isOwnerVerified(),
      livePlanEnabled: false,
      livePlanNotice: '',
      demoStatus: { ...DEFAULT_DEMO_STATUS },
      liveChatEnabled: false,
      liveChatNotice: ''
    }
  },
  computed: {
    requestMessage() {
      return this.planMessage.trim()
    },
    chatDraftActionLabel() {
      return this.chatDraftLanguage === 'zh' ? '生成结构化计划' : 'Generate Structured Plan'
    },
    chatDraftReadyTitle() {
      return this.chatDraftLanguage === 'zh' ? '结构化计划草案已就绪' : 'TravelPlan draft ready'
    },
    planLoadingChatNotice() {
      const language = this.preferredLanguage(this.chatDraft || this.planMessage)
      return language === 'zh'
        ? '结构化计划正在生成中，聊天回复可能稍慢。'
        : 'Structured plan is generating; chat replies may be slower.'
    },
    planLoadingMessage() {
      if (!this.canUseLivePlan) {
        return 'Demo mode is returning the frozen TravelPlan fixture and its matching trace.'
      }
      return 'The structured TravelPlan can take 30-90 seconds when the live model is composing itinerary, budget, risks, and loaded Skills.'
    },
    traceLinkLabel() {
      if (!this.canUseLivePlan) return 'View demo voyage trace'
      if (this.demoStatus.liveManusAvailable) return 'View this live Agent trace'
      return 'View trace fixture'
    },
    canUseLivePlan() {
      return this.ownerEnabled && this.livePlanEnabled
    },
    canUseLiveChat() {
      return this.ownerEnabled && this.liveChatEnabled
    },
    chatQueryParams() {
      return this.canUseLiveChat ? { liveMode: true } : {}
    },
    chatStreamErrorText() {
      return this.canUseLiveChat
        ? 'Live Chat was disabled; demo stream remains active.'
        : '[Hint] The stream ended or the backend is unavailable. Please check that the backend service is running.'
    }
  },
  mounted() {
    this.restorePlanSession()
    this.fetchDemoStatus()
    window.addEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
  },
  beforeUnmount() {
    this.stopPlanSessionPolling()
    window.removeEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
  },
  methods: {
    refreshOwnerState() {
      const hadLiveChat = this.liveChatEnabled
      this.ownerEnabled = isOwnerVerified()
      if (!this.ownerEnabled) {
        this.liveChatEnabled = false
        this.livePlanEnabled = false
        if (hadLiveChat) {
          this.liveChatNotice = 'Live Chat was disabled; demo stream remains active.'
        }
      }
      this.fetchDemoStatus()
    },
    handleLiveChatToggle() {
      this.liveChatNotice = this.liveChatEnabled
        ? ''
        : 'Live Chat is off; demo stream remains active.'
    },
    async fetchDemoStatus() {
      try {
        const { data } = await api.get('/travel/demo-status')
        this.demoStatus = { ...DEFAULT_DEMO_STATUS, ...data }
      } catch (error) {
        this.demoStatus = { ...DEFAULT_DEMO_STATUS }
      }
    },
    async generatePlan() {
      this.planLoading = true
      this.planError = ''
      this.plan = null
      this.planChatId = `travel-plan-${Date.now()}`
      this.activePlanStartedAt = new Date().toISOString()
      this.savePlanSession()
      try {
        const { data } = await this.requestTravelPlan(this.canUseLivePlan)
        this.plan = data
        this.scoreResult = null
        this.scoreError = ''
        this.savePlanSession()
      } catch (err) {
        if (this.canUseLivePlan && err?.response?.status === 403) {
          await validateOwnerToken()
          this.ownerEnabled = isOwnerVerified()
          this.livePlanEnabled = false
          this.livePlanNotice = 'Live TravelPlan was disabled; showing the demo fixture.'
          try {
            const { data } = await this.requestTravelPlan(false)
            this.plan = data
            this.scoreResult = null
            this.scoreError = ''
            this.savePlanSession()
            return
          } catch (fallbackErr) {
            this.planError = this.planErrorMessage(fallbackErr)
            this.savePlanSession()
            return
          }
        }
        this.planError = this.planErrorMessage(err)
        this.savePlanSession()
      } finally {
        this.planLoading = false
        this.activePlanStartedAt = ''
        this.savePlanSession()
      }
    },
    requestTravelPlan(liveMode) {
      return api.post(
        '/travel/plan',
        {
          message: this.requestMessage,
          chatId: this.planChatId,
          liveMode
        },
        {
          timeout: liveMode ? 120000 : 30000
        }
      )
    },
    clearPlanSession() {
      sessionStorage.removeItem('wayfinder.travel.plan')
      this.stopPlanSessionPolling()
      this.planMessage = DEFAULT_PLAN_MESSAGE
      this.plan = null
      this.planChatId = ''
      this.planLoading = false
      this.planError = ''
      this.chatDraft = ''
      this.chatDraftNotice = ''
      this.activePlanStartedAt = ''
      this.scoreLoading = false
      this.scoreError = ''
      this.scoreResult = null
      this.livePlanNotice = ''
    },
    restorePlanSession() {
      try {
        const raw = sessionStorage.getItem('wayfinder.travel.plan')
        if (!raw) return
        const saved = JSON.parse(raw)
        this.planMessage = saved.lastPrompt || saved.planMessage || this.planMessage
        this.plan = saved.structuredPlan || saved.plan || null
        this.planChatId = saved.chatId || ''
        this.planError = saved.planError || ''
        this.chatDraft = saved.chatDraft || ''
        this.chatDraftNotice = saved.chatDraftNotice || ''
        this.chatDraftLanguage = saved.chatDraftLanguage || this.preferredLanguage(this.chatDraft || this.planMessage)
        this.scoreResult = saved.scoreResult || null
        this.scoreError = saved.scoreError || ''
        this.activePlanStartedAt = saved.activePlanStartedAt || ''
        this.planLoading = saved.taskStatus === 'Planning' && !this.plan && !this.planError
        if (this.planLoading) this.startPlanSessionPolling()
      } catch (error) {
        console.warn('Could not restore travel plan session.', error)
      }
    },
    savePlanSession() {
      sessionStorage.setItem('wayfinder.travel.plan', JSON.stringify({
        chatId: this.planChatId,
        messages: [],
        structuredPlan: this.plan,
        generatedPlan: this.plan,
        taskStatus: this.planLoading ? 'Planning' : this.planError ? 'Failed' : this.plan ? 'Completed' : 'Idle',
        lastPrompt: this.planMessage,
        planError: this.planError,
        chatDraft: this.chatDraft,
        chatDraftNotice: this.chatDraftNotice,
        chatDraftLanguage: this.chatDraftLanguage,
        scoreResult: this.scoreResult,
        scoreError: this.scoreError,
        activePlanStartedAt: this.activePlanStartedAt
      }))
    },
    startPlanSessionPolling() {
      this.stopPlanSessionPolling()
      this.planSessionPoller = window.setInterval(() => {
        this.refreshPlanSessionFromStorage()
      }, 1000)
    },
    stopPlanSessionPolling() {
      if (this.planSessionPoller) {
        window.clearInterval(this.planSessionPoller)
        this.planSessionPoller = null
      }
    },
    refreshPlanSessionFromStorage() {
      try {
        const raw = sessionStorage.getItem('wayfinder.travel.plan')
        if (!raw) return
        const saved = JSON.parse(raw)
        this.planMessage = saved.lastPrompt || saved.planMessage || this.planMessage
        this.planChatId = saved.chatId || this.planChatId
        this.chatDraft = saved.chatDraft || this.chatDraft
        this.chatDraftNotice = saved.chatDraftNotice || this.chatDraftNotice
        this.chatDraftLanguage = saved.chatDraftLanguage || this.chatDraftLanguage
        this.activePlanStartedAt = saved.activePlanStartedAt || this.activePlanStartedAt
        if (saved.structuredPlan || saved.plan || saved.planError || saved.taskStatus !== 'Planning') {
          this.plan = saved.structuredPlan || saved.plan || this.plan
          this.planError = saved.planError || ''
          this.scoreResult = saved.scoreResult || this.scoreResult
          this.scoreError = saved.scoreError || ''
          this.planLoading = false
          this.activePlanStartedAt = ''
          this.stopPlanSessionPolling()
        }
      } catch (error) {
        console.warn('Could not refresh travel plan session.', error)
      }
    },
    async scoreCurrentPlan() {
      if (!this.plan || this.scoreLoading) return
      this.scoreLoading = true
      this.scoreError = ''
      this.scoreResult = null
      try {
        const { data } = await api.post('/rpg/evals/score-current-plan', {
          input: this.planMessage,
          chatId: this.planChatId,
          plan: this.plan,
          observedToolCalls: []
        })
        this.scoreResult = data
        this.savePlanSession()
      } catch (error) {
        this.scoreError = error?.response?.data?.message || error?.message || 'Could not score this TravelPlan.'
        this.savePlanSession()
      } finally {
        this.scoreLoading = false
      }
    },
    handleChatCompleted(payload) {
      const aiContent = payload?.aiContent || ''
      const draft = this.extractPlanDraft(aiContent) || this.extractConfirmedDraft(aiContent)
      if (!draft) return
      this.chatDraft = draft
      this.chatDraftLanguage = this.preferredLanguage(draft)
      this.planMessage = draft
      this.chatDraftNotice = this.chatDraftLanguage === 'zh'
        ? '已从聊天整理出结构化计划草案，可直接生成 TravelPlan。'
        : 'Chat details ready for TravelPlan.'
      this.savePlanSession()
    },
    handleChatSubmitted() {
      this.liveChatNotice = ''
      this.clearChatDraftState()
      this.savePlanSession()
    },
    async handleChatFailed() {
      if (!this.liveChatEnabled) return
      await validateOwnerToken()
      this.ownerEnabled = isOwnerVerified()
      this.liveChatEnabled = false
      this.liveChatNotice = 'Live Chat was disabled; demo stream remains active.'
    },
    handleChatCleared() {
      this.clearChatCollectionState()
    },
    clearChatDraftState() {
      this.chatDraft = ''
      this.chatDraftNotice = ''
      this.chatDraftLanguage = 'en'
    },
    clearChatCollectionState() {
      this.clearChatDraftState()
      this.planMessage = DEFAULT_PLAN_MESSAGE
      this.savePlanSession()
    },
    extractPlanDraft(text) {
      const match = String(text || '').match(/(?:^|\n)\s*PLAN_DRAFT:\s*(.+?)\s*$/im)
      return match ? match[1].trim() : ''
    },
    extractConfirmedDraft(text) {
      const value = String(text || '')
      if (!/(信息[^。；\n]*(?:足够|够用)|可以点击\s*Generate\s*TravelPlan|GenerateTravelPlan)/i.test(value)) {
        return ''
      }
      const match = value.match(/(?:已记录|已确认)(?:本轮)?(?:字段|信息)?[:：]\s*([^\n。]+(?:。)?)/)
      if (!match) return ''
      return this.normalizeConfirmedDraft(match[1])
    },
    normalizeConfirmedDraft(text) {
      return String(text || '')
        .replace(/[。；;]\s*$/g, '')
        .replace(/出发地\s*([^\s，,、；;。]+)/g, '$1出发')
        .replace(/目的地\s*([^\s，,、；;。]+)/g, '去$1')
        .replace(/预算\s*(\d+)\s*(CNY|RMB|人民币)/gi, '预算$1 CNY')
        .replace(/主题\s*([^\s，,、；;。]+)/g, '主题$1')
        .replace(/偏好\s*([^\s，,、；;。]+)/g, '偏好$1')
        .replace(/\s+/g, ' ')
        .replace(/,/g, '，')
        .trim()
    },
    generatePlanFromChatDraft() {
      if (this.chatDraft) {
        this.planMessage = this.chatDraft
        this.savePlanSession()
      }
      this.generatePlan()
    },
    planErrorMessage(err) {
      if (err?.code === 'ECONNABORTED') {
        return 'The live model is still taking too long. Try a shorter request, enable Demo Mode for interviews, or check the backend logs for slow model responses.'
      }
      if (err?.response?.data?.message) {
        return err.response.data.message
      }
      return 'Could not generate a structured TravelPlan. Check backend and model configuration.'
    },
    travelLocalResponder(text) {
      const language = this.preferredLanguage(text)
      if (this.isCapabilityQuestion(text)) {
        return {
          intent: 'capability',
          content: language === 'zh'
            ? '我是 Wayfinder Travel Agent，一个旅行规划 Demo Agent。我的作用是把模糊旅行需求整理成可展示的 TravelPlan，包括每日行程、交通、住宿假设、预算拆分和风险提示。'
            : 'I am the Wayfinder Travel Agent, a travel-planning demo agent. I turn rough trip ideas into a structured TravelPlan with daily itinerary, transport, lodging assumptions, budget breakdown, and risk notes.'
        }
      }
      if (this.isGreetingOnly(text)) {
        return {
          intent: 'greeting',
          content: language === 'zh'
            ? '你好，我是 Wayfinder Travel Agent，可以帮你把旅行想法整理成行程、交通与住宿假设、预算拆分、家庭友好提示和风险提醒。你可以告诉我目的地、出发地、天数、人数和预算。'
            : 'Hi, I am the Wayfinder Travel Agent. Tell me your destination, departure city, days, travelers, budget, and travel style, and I can draft an itinerary, transport and lodging assumptions, budget breakdown, family-friendly notes, and risks.'
        }
      }
      return null
    },
    preferredLanguage(text) {
      return /[\u4e00-\u9fff]/.test(String(text || '')) ? 'zh' : 'en'
    },
    isGreetingOnly(text) {
      const normalized = String(text || '').trim().toLowerCase().replace(/[!?.。！？\s]/g, '')
      return ['hi', 'hello', 'hey', '你好', '你好呀', '您好', '哈喽', '嗨', 'hello你好'].includes(normalized)
    },
    isCapabilityQuestion(text) {
      const value = String(text || '').trim().toLowerCase()
      return /你是谁|你是[？?]?$|你好[？?]?你是|你是干嘛|有什么用|能做什么|你能做什么/.test(value)
        || /who are you|what can you do|what are you for/.test(value)
    }
  }
}
</script>
