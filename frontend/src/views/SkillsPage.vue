<template>
  <PageShell
    eyebrow="Constellation Hall"
    title="Skills System"
    subtitle="See which domain skills and engineering capabilities are selected before the Agent plans."
  >
    <StarCard title="Skill Matching Demo" description="Type a travel request and see which backend Skills would be loaded before planning.">
      <form class="prompt-form" @submit.prevent="matchSkills">
        <textarea v-model="matchMessage" rows="3" placeholder="Example: I will travel to Japan with my parents for 7 days, budget 20000 CNY, relaxed pace."></textarea>
        <button type="submit" :disabled="matching || !matchMessage.trim()">{{ matching ? 'Matching...' : 'Match Skills' }}</button>
      </form>
      <StateBlock v-if="matchError" type="error" title="Match failed" :message="matchError" />
      <div v-if="matchedSkills.length" class="match-results">
        <div class="skill-section-header">
          <div>
            <p class="area-kicker">Matched Travel Skills</p>
            <h2>Domain Skills Selected</h2>
          </div>
          <p>These domain skills are selected from the request before the Travel Agent plans.</p>
        </div>
        <article v-for="match in matchedSkills" :key="match.id" class="match-result">
          <strong>{{ match.name }}</strong>
          <p><span>Matched:</span> {{ displayMatchedTerms(match).join(', ') }}</p>
          <p><span>Related:</span> {{ relatedTriggers(match).join(', ') }}</p>
        </article>
      </div>
    </StarCard>

    <StateBlock v-if="loading" type="loading" title="Loading skills" message="Fetching /api/rpg/skills." />
    <StateBlock v-else-if="error" type="error" title="Skills API unavailable" message="Showing local fallback skill cards." />

    <div class="skill-section-header">
      <div>
        <p class="area-kicker">Backend Compass</p>
        <h2>Engineering Capabilities</h2>
      </div>
      <p>These portfolio engineering capabilities explain how the Agent system is built, evaluated, and guarded, including Spring AI Agent Design, Agent Evaluation, Guardrail Design, and Full-stack Product Sense.</p>
    </div>

    <div class="card-grid">
      <StarCard
        v-for="skill in normalizedSkills"
        :key="skill.id"
        :title="skill.name"
        :description="skill.description"
      >
        <template #meta>
          <span>{{ skill.rpgName }}</span>
          <span class="rarity">Level {{ skill.level }}</span>
        </template>
        <TagList :items="[skill.category, ...skill.keywords]" />
        <div class="section-block">
          <h3>Use Cases</h3>
          <ul class="feature-list">
            <li v-for="item in skill.useCases" :key="item">{{ item }}</li>
          </ul>
        </div>
        <div class="section-block">
          <h3>Related Agents</h3>
          <TagList :items="skill.relatedAgents" />
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

const SKILL_FALLBACKS = {
  'java-backend-architecture': { rpgName: 'Backend Compass', relatedAgents: ['Wayfinder Guild', 'Travel Agent'] },
  'spring-ai-agent-design': { rpgName: 'Agent Lantern', relatedAgents: ['Travel Agent', 'SyManus'] },
  'rag-knowledge-engineering': { rpgName: 'Memory Star', relatedAgents: ['RAG Library'] },
  'agent-evaluation': { rpgName: 'Lighthouse Lens', relatedAgents: ['Eval Harness'] },
  'guardrail-design': { rpgName: 'Safety Rail', relatedAgents: ['Travel Agent', 'Tool Agent'] }
}

const SKILL_USE_CASES = {
  'java-backend-architecture': [
    'Keep controller, service, and domain boundaries explicit.',
    'Make backend behavior easy to test and explain.'
  ],
  'spring-ai-agent-design': [
    'Compose prompts, structured output, tool calls, and traceable runs.',
    'Explain where model behavior ends and application logic begins.'
  ],
  'rag-knowledge-engineering': [
    'Connect retrieved context to grounded answers.',
    'Make knowledge-backed responses easier to inspect.'
  ],
  'agent-evaluation': [
    'Use fixed cases, scoring rules, and regression checks.',
    'Show why an Agent answer is reliable enough to ship.'
  ],
  'guardrail-design': [
    'Set input, tool, and output boundaries for safer demos.',
    'Keep risky actions blocked or clearly explained.'
  ],
  'fullstack-product-sense': [
    'Shape the demo around user paths and inspectable outcomes.',
    'Turn backend capability into a clear portfolio experience.'
  ]
}

const FALLBACK_SKILLS = [
  {
    id: 'spring-ai-agent-design',
    name: 'Spring AI Agent Design',
    category: 'Agent',
    level: 'advanced',
    description: 'Designs chat, structured output, tool calling, and traceable Agent flows.',
    keywords: ['Spring AI', 'DeepSeek', 'ChatClient']
  }
]

export default {
  name: 'SkillsPage',
  components: { PageShell, StarCard, TagList, StateBlock },
  data() {
    return {
      skills: FALLBACK_SKILLS,
      loading: true,
      error: false,
      matchMessage: 'I will travel to Japan with my parents for 7 days, budget 20000 CNY, relaxed pace.',
      matchedSkills: [],
      matching: false,
      matchError: ''
    }
  },
  computed: {
    normalizedSkills() {
      return this.skills.map((skill) => {
        const fallback = SKILL_FALLBACKS[skill.id] || {}
        const keywords = skill.keywords || []
        return {
          id: skill.id || skill.name,
          name: skill.name,
          rpgName: skill.rpgName || fallback.rpgName || `${skill.category || 'Skill'} Star`,
          level: skill.level || 'intermediate',
          category: skill.category || 'Engineering',
          description: skill.description || '',
          keywords,
          useCases: skill.useCases || SKILL_USE_CASES[skill.id] || [
            `Apply ${skill.name} in a real backend capability.`,
            'Explain the engineering boundary in portfolio interviews.'
          ],
          relatedAgents: skill.relatedAgents || fallback.relatedAgents || ['Travel Agent']
        }
      })
    }
  },
  async mounted() {
    try {
      const { data } = await api.get('/rpg/skills')
      this.skills = Array.isArray(data) && data.length ? data : FALLBACK_SKILLS
    } catch (err) {
      this.error = true
    } finally {
      this.loading = false
    }
  },
  methods: {
    async matchSkills() {
      this.matching = true
      this.matchError = ''
      this.matchedSkills = []
      try {
        const { data } = await api.post('/rpg/skills/match', { message: this.matchMessage })
        this.matchedSkills = Array.isArray(data) ? data : []
      } catch (err) {
        this.matchError = 'Could not call /api/rpg/skills/match. Showing no matches.'
      } finally {
        this.matching = false
      }
    },
    displayMatchedTerms(match) {
      const terms = match.matchedTerms || []
      return terms.length ? terms : ['Matched by skill metadata']
    },
    relatedTriggers(match) {
      const matched = new Set((match.matchedTerms || []).map((item) => String(item).toLowerCase()))
      const triggers = (match.triggers || []).filter((item) => !matched.has(String(item).toLowerCase()))
      const visible = triggers.slice(0, 4)
      const remaining = triggers.length - visible.length
      return remaining > 0 ? [...visible, `+${remaining} more`] : visible
    }
  }
}
</script>
