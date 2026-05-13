<template>
  <div class="chat-root">
    <div class="chat-header">
      <div>
        <span>{{ title }}</span>
        <small>{{ chatId }}</small>
      </div>
      <button v-if="storageKey" type="button" class="chat-clear-button" @click="clearSession">
        {{ clearLabel }}
      </button>
    </div>
    <div ref="body" class="chat-body">
      <div
        v-for="(message, index) in messages"
        :key="index"
        :class="['msg', message.role]"
      >
        <div class="bubble" v-html="message.html || formatMessage(message.content)"></div>
      </div>
      <div v-if="messages.length === 0" class="empty-chat">
        {{ emptyText }}
      </div>
    </div>
    <form class="chat-footer" @submit.prevent="send">
      <input
        v-model="input"
        :disabled="streaming"
        :placeholder="placeholder"
      />
      <button type="submit" :disabled="streaming || !input.trim()">
        {{ streaming ? streamingLabel : sendLabel }}
      </button>
    </form>
  </div>
</template>

<script>
import axios from '../api'

export default {
  props: {
    title: { type: String, default: 'Chat' },
    ssePath: { type: String, required: true },
    placeholder: { type: String, default: 'Type a message, then press Enter' },
    storageKey: { type: String, default: '' },
    emptyText: { type: String, default: 'Start with a concrete question and watch how the Agent responds.' },
    clearLabel: { type: String, default: 'New Chat' },
    sendLabel: { type: String, default: 'Send' },
    streamingLabel: { type: String, default: 'Streaming' },
    streamErrorText: {
      type: String,
      default: '[Hint] The stream ended or the backend is unavailable. Please check that the backend service is running.'
    },
    greetingResponse: { type: String, default: '' },
    localResponder: { type: Function, default: null },
    hiddenLinePrefixes: { type: Array, default: () => [] },
    queryParams: { type: Object, default: () => ({}) }
  },
  emits: ['submitted', 'stream-start', 'streaming', 'completed', 'failed', 'greeting', 'local-response', 'artifact', 'cleared'],
  data() {
    return {
      chatId: '',
      input: '',
      messages: [],
      es: null,
      currentAiIndex: -1,
      streaming: false,
      lastPrompt: '',
      taskStatus: 'Idle'
    }
  },
  mounted() {
    this.restoreSession()
    if (!this.chatId) this.chatId = this.generateChatId()
    this.saveSession()
  },
  beforeUnmount() {
    this.closeStream()
  },
  methods: {
    generateChatId() {
      return `chat-${Math.random().toString(36).slice(2, 10)}`
    },
    send() {
      this.sendText(this.input)
    },
    fillInput(text) {
      this.input = text
    },
    appendLocalExchange(userText, assistantText, assistantHtml = '') {
      const text = String(userText || '').trim()
      if (text) {
        this.messages.push({ role: 'user', content: text })
      }
      this.messages.push({ role: 'ai', content: String(assistantText || ''), html: assistantHtml || '' })
      this.taskStatus = 'Completed'
      this.saveSession()
      this.scrollToBottom()
    },
    sendText(value) {
      const text = String(value || '').trim()
      if (!text || this.streaming) return

      this.input = ''
      this.lastPrompt = text
      this.messages.push({ role: 'user', content: text })

      const localResponse = this.resolveLocalResponse(text)
      if (localResponse) {
        this.messages.push({ role: 'ai', content: localResponse.content })
        this.taskStatus = 'Idle'
        this.saveSession()
        const payload = { chatId: this.chatId, message: text, intent: localResponse.intent || 'local-response' }
        if (localResponse.intent === 'greeting') this.$emit('greeting', payload)
        this.$emit('local-response', payload)
        this.scrollToBottom()
        return
      }

      this.currentAiIndex = this.messages.push({ role: 'ai', content: '' }) - 1
      this.streaming = true
      this.taskStatus = 'Streaming'
      this.saveSession()
      this.$emit('submitted', { chatId: this.chatId, message: text })
      this.$emit('stream-start', { chatId: this.chatId, message: text })
      this.scrollToBottom()

      const base = (axios.defaults.baseURL || '').replace(/\/+$/, '')
      const params = new URLSearchParams({ message: text, chatId: this.chatId })
      Object.entries(this.queryParams || {}).forEach(([key, value]) => {
        if (value !== undefined && value !== null && value !== '') {
          params.set(key, String(value))
        }
      })
      const url = `${base}${this.ssePath}?${params.toString()}`

      this.closeStream()
      this.es = new EventSource(url, { withCredentials: true })
      this.es.onmessage = (event) => {
        if (!event.data || event.data === '[DONE]' || event.data === '__DONE__') {
          this.finishStream()
          return
        }
        const artifacts = this.extractArtifacts(event.data)
        this.messages[this.currentAiIndex].content += event.data
        this.saveSession()
        artifacts.forEach((artifact) => this.$emit('artifact', { chatId: this.chatId, message: text, artifact }))
        this.$emit('streaming', { chatId: this.chatId, message: text, chunk: event.data })
        this.scrollToBottom()
      }
      this.es.onerror = () => {
        if (!this.messages[this.currentAiIndex]?.content) {
          this.messages[this.currentAiIndex].content = this.streamErrorText
          this.taskStatus = 'Failed'
          this.$emit('failed', {
            chatId: this.chatId,
            message: text,
            queryParams: { ...(this.queryParams || {}) }
          })
        }
        this.finishStream()
      }
    },
    finishStream() {
      this.streaming = false
      if (this.taskStatus !== 'Failed') {
        this.taskStatus = 'Completed'
        const aiContent = this.messages[this.currentAiIndex]?.content || ''
        this.$emit('completed', { chatId: this.chatId, message: this.lastPrompt, aiContent })
      }
      this.currentAiIndex = -1
      this.closeStream()
      this.saveSession()
      this.scrollToBottom()
    },
    closeStream() {
      if (this.es) {
        this.es.close()
        this.es = null
      }
    },
    scrollToBottom() {
      this.$nextTick(() => {
        const el = this.$refs.body
        if (el) el.scrollTop = el.scrollHeight
      })
    },
    clearSession() {
      this.closeStream()
      this.chatId = this.generateChatId()
      this.input = ''
      this.messages = []
      this.currentAiIndex = -1
      this.streaming = false
      this.lastPrompt = ''
      this.taskStatus = 'Idle'
      if (this.storageKey) sessionStorage.removeItem(this.storageKey)
      this.saveSession()
      this.$emit('cleared', { chatId: this.chatId })
    },
    restoreSession() {
      if (!this.storageKey) return
      try {
        const raw = sessionStorage.getItem(this.storageKey)
        if (!raw) return
        const saved = JSON.parse(raw)
        this.chatId = saved.chatId || ''
        this.messages = Array.isArray(saved.messages) ? saved.messages : []
        this.lastPrompt = saved.lastPrompt || ''
        this.taskStatus = saved.taskStatus || 'Idle'
      } catch (error) {
        console.warn('Could not restore chat session.', error)
      }
    },
    saveSession() {
      if (!this.storageKey) return
      const payload = {
        chatId: this.chatId,
        messages: this.messages,
        lastPrompt: this.lastPrompt,
        taskStatus: this.taskStatus
      }
      sessionStorage.setItem(this.storageKey, JSON.stringify(payload))
    },
    isGenericGreeting(text) {
      const normalized = text.trim().toLowerCase().replace(/[!?.。！？\s]/g, '')
      return ['hi', 'hello', 'hey', '你好', '你好呀', '您好', '哈喽', '嗨', 'hello你好'].includes(normalized)
    },
    resolveLocalResponse(text) {
      if (this.localResponder) {
        const response = this.localResponder(text)
        if (typeof response === 'string' && response.trim()) {
          return { content: response.trim(), intent: 'local-response' }
        }
        if (response?.content) return response
      }
      if (this.greetingResponse && this.isGenericGreeting(text)) {
        return { content: this.greetingResponse, intent: 'greeting' }
      }
      return null
    },
    formatMessage(text) {
      if (!text) return ''
      const visible = this.visibleMessageText(this.stripArtifactMarkers(text))
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
        .replace(/\r\n/g, '\n')
        .replace(/\n/g, '<br/>')
      const artifacts = this.extractArtifacts(text)
      const artifactHtml = artifacts.map((artifact) => this.renderArtifact(artifact)).join('')
      return `${visible}${artifactHtml}`
    },
    visibleMessageText(text) {
      const prefixes = this.hiddenLinePrefixes
        .map((prefix) => String(prefix || '').trim())
        .filter(Boolean)
      if (!prefixes.length) return String(text)
      return String(text)
        .split(/\r?\n/)
        .filter((line) => !prefixes.some((prefix) => line.trim().startsWith(prefix)))
        .join('\n')
        .trim()
    },
    extractArtifacts(text) {
      const artifacts = []
      const pattern = /\[ARTIFACT\]([\s\S]*?)\[\/ARTIFACT\]/g
      let match
      while ((match = pattern.exec(String(text || ''))) !== null) {
        try {
          artifacts.push(JSON.parse(match[1]))
        } catch (error) {
          console.warn('Could not parse artifact marker.', error)
        }
      }
      return artifacts
    },
    stripArtifactMarkers(text) {
      const hasArtifact = this.extractArtifacts(text).length > 0
      let visible = String(text || '')
        .replace(/\[ARTIFACT\][\s\S]*?\[\/ARTIFACT\]/g, '')
        .replace(/(File written successfully to:|PDF generated successfully to:|Image downloaded successfully to:|Resource downloaded successfully to:|The image was downloaded successfully to:|The resource was downloaded successfully to:)\s+`?[^`\r\n]+`?/gi, '$1 secure artifact link registered')
      if (hasArtifact) {
        visible = visible
          .split(/\r?\n/)
          .filter((line) => !/^\s*(File written successfully to:|PDF generated successfully to:|Image downloaded successfully to:|Resource downloaded successfully to:|The image was downloaded successfully to:|The resource was downloaded successfully to:|Success path:)/i.test(line.trim()))
          .join('\n')
      }
      return visible
        .trim()
    },
    renderArtifact(artifact) {
      if (!artifact) return ''
      const previewUrl = this.escapeHtml(this.absoluteUrl(artifact.previewUrl))
      const downloadUrl = this.escapeHtml(this.absoluteUrl(artifact.downloadUrl))
      const fileName = this.escapeHtml(artifact.fileName)
      const mimeType = this.escapeHtml(artifact.mimeType || '')
      const expires = this.escapeHtml(this.formatDate(artifact.expiresAt))
      const image = artifact.mimeType?.startsWith('image/')
        ? `<img src="${previewUrl}" alt="${fileName}" />`
        : ''
      return `
        <div class="artifact-result">
          <strong>Generated File</strong>
          ${image}
          <div class="artifact-meta">
            <span>${fileName}</span>
            <span>${mimeType}</span>
            <span>${this.formatBytes(artifact.size)}</span>
            <span>Expires ${expires}</span>
          </div>
          <div class="artifact-actions">
            <a href="${previewUrl}" target="_blank" rel="noreferrer">Preview</a>
            <a href="${downloadUrl}" download="${fileName}" target="_blank" rel="noreferrer">Download</a>
          </div>
        </div>
      `
    },
    absoluteUrl(path) {
      if (!path) return ''
      if (/^https?:\/\//.test(path)) return path
      const base = (axios.defaults.baseURL || '').replace(/\/+$/, '')
      const joined = `${base}${path}`.replace(/([^:]\/)\/+/g, '$1')
      return new URL(joined, window.location.origin).href
    },
    escapeHtml(value) {
      return String(value || '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
    },
    formatBytes(size) {
      const value = Number(size || 0)
      if (value < 1024) return `${value} B`
      if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
      return `${(value / 1024 / 1024).toFixed(1)} MB`
    },
    formatDate(value) {
      return value ? new Date(value).toLocaleString() : 'soon'
    }
  }
}
</script>
