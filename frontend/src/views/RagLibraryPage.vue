<template>
  <PageShell
    eyebrow="Memory Library"
    title="Explainable RAG Library"
    subtitle="Ask the travel knowledge base and inspect query rewrite, retrieved documents, and final answer."
  >
    <div class="demo-question-rail">
      <button
        v-for="question in demoQuestions"
        :key="question"
        type="button"
        @click="useDemoQuestion(question)"
      >
        {{ question }}
      </button>
    </div>

    <p class="rag-demo-note">Demo Mode uses fixed local Markdown snippets, but retrieval and answers vary by query to show the RAG explanation path without database cost.</p>

    <form class="prompt-form" @submit.prevent="askRag">
      <textarea v-model="message" rows="4" placeholder="问一个旅行知识库问题..."></textarea>
      <button type="submit" :disabled="loading || !message.trim()">{{ loading ? 'Explaining...' : 'Explain RAG Answer' }}</button>
    </form>

    <StateBlock v-if="error" type="error" title="RAG request failed" :message="error" />
    <StateBlock
      v-if="response?.degraded"
      type="empty"
      title="Graceful fallback"
      :message="response.degradationReason || 'RAG explain returned a degraded response.'"
    />

    <div v-if="response" class="rag-explain-grid">
      <StarCard :title="modeTitle(response.mode)" :description="modeDescription(response.mode)">
        <template #meta>Current retrieval mode</template>
        <TagList :items="[response.degraded ? 'degraded gracefully' : 'healthy path']" />
      </StarCard>

      <StarCard title="Query Path" description="The query the user typed and the rewritten retrieval query.">
        <dl class="profile-facts">
          <div>
            <dt>Original</dt>
            <dd>{{ response.originalQuery }}</dd>
          </div>
          <div>
            <dt>Rewritten</dt>
            <dd>{{ response.rewrittenQuery }}</dd>
          </div>
          <div>
            <dt>Chat ID</dt>
            <dd>{{ response.chatId }}</dd>
          </div>
          <div>
            <dt>Documents</dt>
            <dd>{{ response.documents?.length || 0 }}</dd>
          </div>
        </dl>
      </StarCard>

      <StarCard title="Final Answer" :description="response.answer">
        <template #meta>Grounded answer</template>
      </StarCard>
    </div>

    <div v-if="response?.documents?.length" class="card-grid">
      <StarCard
        v-for="(doc, index) in response.documents"
        :key="`${doc.documentId || doc.source}-${index}`"
        :title="doc.title || `Document ${index + 1}`"
        :description="doc.snippet"
      >
        <template #meta>
          <span>{{ doc.source || 'unknown source' }}</span>
          <span v-if="doc.documentId">#{{ doc.documentId }}</span>
        </template>
        <dl class="metadata-grid rag-document-meta">
          <div v-if="doc.updated">
            <dt>Updated</dt>
            <dd>{{ doc.updated }}</dd>
          </div>
          <div v-if="doc.sourceType">
            <dt>Source Type</dt>
            <dd>{{ doc.sourceType }}</dd>
          </div>
        </dl>
        <TagList :items="documentTags(doc)" />
      </StarCard>
    </div>
  </PageShell>
</template>

<script>
import api from '../api'
import PageShell from '../components/common/PageShell.vue'
import StarCard from '../components/common/StarCard.vue'
import TagList from '../components/common/TagList.vue'
import StateBlock from '../components/common/StateBlock.vue'

export default {
  name: 'RagLibraryPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      message: '我和父母 3 个人 6 月去日本 7 天，预算 2 万，想轻松一点，怎么安排？',
      response: null,
      loading: false,
      error: '',
      demoQuestions: [
        '我和父母 3 个人 6 月去日本 7 天，预算 2 万，想轻松一点，怎么安排？',
        '日本旅行交通券怎么选，JR Pass 一定划算吗？',
        '日本旅行遇到下雨天，有什么备选方案？',
        '低预算旅行怎么控制住宿、交通和餐饮？',
        '带老人小孩旅行有哪些风险要提前考虑？'
      ]
    }
  },
  methods: {
    async askRag() {
      this.loading = true
      this.error = ''
      this.response = null
      try {
        const { data } = await api.post('/travel/rag/explain', {
          message: this.message,
          chatId: `rag-explain-${Date.now()}`
        })
        this.response = data
      } catch (err) {
        this.error = 'Could not query the explainable RAG endpoint. The legacy RAG endpoint remains unchanged.'
      } finally {
        this.loading = false
      }
    },
    scoreLabel(score) {
      return score === null || score === undefined ? 'score: n/a' : `score: ${Number(score).toFixed(3)}`
    },
    documentTags(doc) {
      return [this.scoreLabel(doc.score), ...(doc.tags || [])]
    },
    useDemoQuestion(question) {
      this.message = question
    },
    modeTitle(mode) {
      const normalized = mode || 'demo'
      if (normalized === 'pgvector') return 'PgVector Live'
      if (normalized === 'lightweight' || normalized === 'lightweight-fallback') return 'Lightweight Retrieval'
      return 'Demo Mode'
    },
    modeDescription(mode) {
      const normalized = mode || 'demo'
      if (normalized === 'pgvector') {
        return 'Owner Live Mode uses VectorStore retrieval for deeper local or controlled demos.'
      }
      if (normalized === 'lightweight-fallback') {
        return 'PgVector was requested but unavailable, so the public-safe Markdown retriever handled the request without a server error.'
      }
      if (normalized === 'lightweight') {
        return 'Public cost-control mode searches local Markdown snippets without PgVector or cloud database cost.'
      }
      return 'Demo Mode uses fixed local Markdown snippets, but retrieval and answers vary by query to show the RAG explanation path without PgVector or database cost.'
    }
  }
}
</script>
