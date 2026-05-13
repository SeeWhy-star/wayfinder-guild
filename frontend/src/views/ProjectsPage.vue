<template>
  <PageShell
    eyebrow="Voyage Forge"
    title="Project Portfolio"
    subtitle="Runnable proofs for Java backend, Agent, RAG, tool calling, trace, eval, and guardrails."
  >
    <StateBlock v-if="loading" type="loading" title="Loading projects" message="Fetching /api/rpg/projects." />
    <StateBlock v-else-if="error" type="error" title="Projects API unavailable" message="Showing local fallback project cards." />

    <div class="card-grid">
      <StarCard
        v-for="project in normalizedProjects"
        :key="project.id"
        :title="project.name"
        :description="project.description"
      >
        <template #meta>
          <span>{{ project.rpgName }}</span>
          <span class="rarity">{{ project.rarity }}</span>
        </template>
        <TagList :items="project.techStack" />
        <div class="section-block">
          <h3>Highlights</h3>
          <ul class="feature-list">
            <li v-for="highlight in project.highlights" :key="highlight">{{ highlight }}</li>
          </ul>
        </div>
        <div class="card-actions">
          <router-link v-if="project.demoRoute" :to="project.demoRoute">Open Demo</router-link>
          <a v-if="project.githubUrl" :href="project.githubUrl" target="_blank" rel="noreferrer">GitHub</a>
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

const PROJECT_FALLBACKS = {
  'wayfinder-guild': { rpgName: 'Travel Cabin Core', rarity: 'Legendary', demoRoute: '/travel-agent' },
  'symanus-tool-agent': { rpgName: 'Tool Workshop Engine', rarity: 'Rare', demoRoute: '/manus-agent' },
  'wayfinder-guild-metadata': { rpgName: 'Portfolio Metadata Foundation', rarity: 'Uncommon' }
}

const FALLBACK_PROJECTS = [
  {
    id: 'wayfinder-guild',
    name: 'Wayfinder Guild Core',
    subtitle: 'Travel Cabin Core',
    description: 'AI 旅行规划与智能体后端系统：基于 Spring Boot 3.4、Java 21、Spring AI 与 DeepSeek，串起多轮流式对话、结构化 TravelPlan、RAG、Skills、受控工具、Trace、Eval 与 Guardrails。',
    tags: ['Java 21', 'Spring Boot 3.4', 'Spring AI', 'DeepSeek', 'RAG', 'SSE', 'Agent Trace'],
    highlights: [
      '按 Controller -> Facade -> Orchestrator -> Service 拆解旅行规划主链路。',
      'TravelPlan 结构化输出带 fallback，避免模型解析失败拖垮接口可用性。',
      'RAG 支持 demo / lightweight / pgvector 三种模式，兼顾本地演示和扩展。',
      'Skills 通过 SKILL.md 动态加载，减少旅行规则硬编码。',
      'Agent Trace、Eval Harness 和 Rust CLI 组成发布前质量门禁。'
    ]
  },
  {
    id: 'symanus-tool-agent',
    name: 'SyManus Tool Agent',
    subtitle: 'Tool Workshop Engine',
    description: '受控工具型 Agent 原型：基于 ReAct 流程封装搜索、网页抓取、文件读写、下载、终端命令和 PDF 生成，并用 Guardrails、maxSteps、AgentState 与 Artifact 注册约束执行边界。',
    tags: ['ReAct', 'Tool Calling', 'SSE', 'Guardrails', 'Artifact Registry'],
    highlights: [
      '将工具能力封装在独立 service 边界内，便于测试和权限控制。',
      '对路径、URL、命令和模型输出做安全校验，降低越界执行风险。',
      '通过 SSE 返回执行过程，方便演示长任务和定位失败步骤。',
      'Artifact 注册返回预览链接、下载链接、文件类型、大小和过期时间，避免暴露真实服务端路径。'
    ]
  },
  {
    id: 'wayfinder-guild-metadata',
    name: 'Wayfinder Guild Metadata',
    subtitle: 'Portfolio Metadata Foundation',
    description: '作品集元数据层：用 JSON 资源描述地图、NPC、技能、项目卡片、能力页面和 Profile 面板，为 Vue 与 Phaser 前端提供稳定渲染数据。',
    tags: ['Metadata API', 'JSON Resources', 'Vue', 'Phaser', 'Portfolio System'],
    highlights: [
      '将作品集内容从前端页面中抽离为可维护资源。',
      'Controller 只处理协议，Service 负责加载和查询资源。',
      '支撑 RPG 地图、项目页和能力页的一致展示。',
      '让作品集不是静态页面，而是一个可扩展的工程作品系统。'
    ]
  }
]

export default {
  name: 'ProjectsPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      projects: FALLBACK_PROJECTS,
      loading: true,
      error: false
    }
  },
  computed: {
    normalizedProjects() {
      return this.projects.map((project) => {
        const fallback = PROJECT_FALLBACKS[project.id] || {}
        return {
          id: project.id || project.name,
          name: project.name,
          rpgName: project.rpgName || fallback.rpgName || project.subtitle || 'Voyage Work',
          rarity: project.rarity || fallback.rarity || 'Rare',
          description: project.description || project.subtitle || '',
          techStack: project.techStack || project.tags || [],
          highlights: project.highlights || [],
          githubUrl: project.githubUrl || project.links?.[0] || '',
          demoRoute: project.demoRoute || fallback.demoRoute || ''
        }
      })
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/projects')
      this.projects = Array.isArray(data) && data.length ? data : FALLBACK_PROJECTS
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  }
}
</script>
