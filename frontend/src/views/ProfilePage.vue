<template>
  <PageShell
    eyebrow="Profile Journal"
    :title="profile.name || 'SeeWhy'"
    :subtitle="profile.title || 'Java & AI Application Engineer'"
  >
    <StateBlock
      v-if="loading"
      type="loading"
      title="Loading profile"
      message="Reading the travel journal from /api/rpg/profile."
    />
    <StateBlock
      v-else-if="error"
      type="error"
      title="Profile API unavailable"
      message="Showing local fallback profile data for now."
    />

    <div class="profile-layout">
      <StarCard class="profile-hero" :description="profile.summary">
        <template #meta>{{ profile.role || 'The Wayfinder' }}</template>
        <dl class="profile-facts">
          <div>
            <dt>Location</dt>
            <dd>{{ profile.location || 'China' }}</dd>
          </div>
          <div v-for="(value, key) in profile.stats || {}" :key="key">
            <dt>{{ key }}</dt>
            <dd>{{ value }}</dd>
          </div>
        </dl>
      </StarCard>

      <StarCard title="Capability Tags" description="面试官可以从这些标签快速定位工程能力。">
        <TagList :items="profile.focusAreas || []" />
      </StarCard>

      <StarCard title="Strengths" description="这个作品集希望证明的能力。">
        <ul class="feature-list">
          <li v-for="item in profile.strengths || []" :key="item">{{ item }}</li>
        </ul>
      </StarCard>

      <StarCard title="Journey Notes" description="从支付链路到 AI 应用后端的工程迁移路径。">
        <div class="milestone-list">
          <div v-for="item in milestones" :key="item.title" class="milestone">
            <strong>{{ item.title }}</strong>
            <span>{{ item.text }}</span>
          </div>
        </div>
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

const FALLBACK_PROFILE = {
  name: 'SeeWhy',
  title: 'Java & AI Application Engineer',
  role: 'The Wayfinder',
  location: 'China',
  summary: 'Java 后端工程师，正在把支付系统中的稳定性、边界设计、状态流转、链路排障经验，迁移到 AI 应用后端、Agent、RAG、工具调用与可观测性工程中。',
  focusAreas: [
    'Java backend architecture',
    'Payment OpenAPI & channel integration',
    'Callback, signature, idempotency, routing',
    'Spring AI, RAG & tool calling',
    'Agent trace, eval & guardrails',
    'Productized portfolio demos'
  ],
  strengths: [
    '能将复杂业务链路拆成清晰的后端边界。',
    '熟悉支付渠道接入中的回调、验签、状态流转、幂等和排障。',
    '重视可测试性、可观测性和演示稳定性。',
    '能把 AI 能力产品化，而不是停留在 prompt demo。'
  ],
  stats: {
    backend: 'Java 21 + Spring Boot 3.4',
    payment: 'OpenAPI integration & callbacks',
    ai: 'Spring AI + RAG + tool calling',
    portfolio: 'Runnable demos + metadata API'
  }
}

export default {
  name: 'ProfilePage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      profile: FALLBACK_PROFILE,
      loading: true,
      error: false,
      milestones: [
        { title: 'Payment Engineering', text: '跨境支付 OpenAPI、第三方渠道接入、收银台联调、异步回调、渠道路由和日志排障。' },
        { title: 'Agentic Backend', text: '旅行规划后端拆分为 chat、plan、RAG、tool、trace、eval、guardrail 等模块。' },
        { title: 'Portfolio System', text: '用 RPG 地图和 metadata API，把工程能力变成可探索、可演示的作品集。' }
      ]
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/profile')
      this.profile = { ...FALLBACK_PROFILE, ...data }
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  }
}
</script>
