<template>
  <PageShell
    class="architecture-page-shell"
    eyebrow="Wayfinder Architecture"
    title="System Architecture"
    subtitle="Request path, agent boundary, runtime modes, and quality loop for the Agentic Travel Backend."
  >
    <div class="architecture-map">
      <section class="architecture-panel system-chain-panel" aria-labelledby="request-chain-title">
        <div class="architecture-section-head">
          <div>
            <p class="architecture-kicker">Main request chain</p>
            <h2 id="request-chain-title">Controller to TravelPlan Cards</h2>
          </div>
          <span class="architecture-badge">Resume core</span>
        </div>

        <ol class="request-flow" aria-label="Primary travel planning request flow">
          <li v-for="node in requestChain" :key="node.title" class="request-step">
            <span class="step-index">{{ node.step }}</span>
            <span class="step-meta">{{ node.meta }}</span>
            <strong>{{ node.title }}</strong>
            <p>{{ node.description }}</p>
            <TagList :items="node.tags" />
          </li>
        </ol>

        <div class="support-layer-grid" aria-label="Supporting architecture layers">
          <article v-for="layer in supportLayers" :key="layer.title" class="support-layer">
            <span>{{ layer.label }}</span>
            <strong>{{ layer.title }}</strong>
            <p>{{ layer.description }}</p>
          </article>
        </div>
      </section>

      <section class="architecture-panel" aria-labelledby="tool-boundary-title">
        <div class="architecture-section-head">
          <div>
            <p class="architecture-kicker">Agent tool boundary</p>
            <h2 id="tool-boundary-title">SyManus Executes Through Guarded Edges</h2>
          </div>
          <span class="architecture-badge live-boundary">Tool loop</span>
        </div>

        <div class="boundary-layout">
          <ol class="boundary-flow" aria-label="SyManus tool boundary">
            <li v-for="node in toolBoundary" :key="node.title" class="boundary-node">
              <span>{{ node.meta }}</span>
              <strong>{{ node.title }}</strong>
              <p>{{ node.description }}</p>
            </li>
          </ol>

          <aside class="guardrail-stack" aria-label="Layered guardrails">
            <div class="guardrail-stack-head">
              <span>Layered guardrails</span>
              <strong>Every edge has a check</strong>
            </div>
            <article v-for="guardrail in guardrails" :key="guardrail.title" class="guardrail-layer">
              <span>{{ guardrail.layer }}</span>
              <div>
                <strong>{{ guardrail.title }}</strong>
                <p>{{ guardrail.description }}</p>
              </div>
            </article>
          </aside>
        </div>
      </section>

      <section class="architecture-panel quality-panel" aria-labelledby="quality-loop-title">
        <div class="architecture-section-head">
          <div>
            <p class="architecture-kicker">Quality loop</p>
            <h2 id="quality-loop-title">Observable, Scored, Verified</h2>
          </div>
          <span class="architecture-badge">Release gate</span>
        </div>

        <ol class="quality-loop" aria-label="Quality feedback loop">
          <li v-for="item in qualityLoop" :key="item.title" class="quality-step">
            <span>{{ item.meta }}</span>
            <strong>{{ item.title }}</strong>
            <p>{{ item.description }}</p>
          </li>
        </ol>
      </section>

      <section class="architecture-panel runtime-panel" aria-labelledby="runtime-modes-title">
        <div class="architecture-section-head">
          <div>
            <p class="architecture-kicker">Runtime modes</p>
            <h2 id="runtime-modes-title">Demo Stability, Live Capability, RAG Fallback</h2>
          </div>
        </div>

        <div class="runtime-grid">
          <article v-for="mode in runtimeModes" :key="mode.title" class="runtime-card" :class="[mode.type, mode.state]">
            <span class="runtime-meta">{{ mode.meta }}</span>
            <strong>{{ mode.title }}</strong>
            <span class="runtime-status">{{ mode.status }}</span>
            <p>{{ mode.description }}</p>
            <TagList :items="mode.tags" />
          </article>
        </div>
      </section>
    </div>
  </PageShell>
</template>

<script>
import api from '../api'
import PageShell from '../components/common/PageShell.vue'
import TagList from '../components/common/TagList.vue'

export default {
  name: 'ArchitecturePage',
  components: { PageShell, TagList },
  data() {
    return {
      demoStatus: {
        demoMode: true,
        ragMode: 'demo'
      },
      requestChain: [
        {
          step: '01',
          title: 'Frontend / User Request',
          meta: 'UI entry',
          description: 'Prompt, demo fixture, or live chat request enters the travel workflow.',
          tags: ['Vue 3', 'SSE trace']
        },
        {
          step: '02',
          title: 'WayfinderTravelController',
          meta: 'API gate',
          description: 'Thin HTTP boundary keeps routing and request DTO handling separate.',
          tags: ['Controller', 'DTO']
        },
        {
          step: '03',
          title: 'WayfinderTravelFacade',
          meta: 'Compatibility facade',
          description: 'Keeps API behavior stable while delegating work to split services.',
          tags: ['Facade', 'compat']
        },
        {
          step: '04',
          title: 'TravelOrchestratorService',
          meta: 'Orchestrator',
          description: 'Coordinates requirements, planning, budget, risks, and report assembly.',
          tags: ['workflow', 'service layer']
        },
        {
          step: '05',
          title: 'Requirement / Skills / RAG / LLM',
          meta: 'Support lanes',
          description: 'Extracts slots, injects skill prompts, retrieves context, calls model.',
          tags: ['SKILL.md', 'retrieval']
        },
        {
          step: '06',
          title: 'Budget / Risk / Report',
          meta: 'Domain services',
          description: 'Adds cost estimates, safety notes, and user-facing plan narrative.',
          tags: ['risk', 'budget']
        },
        {
          step: '07',
          title: 'TravelPlan Cards + Trace',
          meta: 'Portfolio output',
          description: 'Renders structured cards with fallback plan and observable timeline.',
          tags: ['TravelPlan', 'fallback']
        }
      ],
      supportLayers: [
        {
          label: 'Layer 1',
          title: 'Requirement shaping',
          description: 'Turns rough intent into destination, days, budget, traveler profile, and missing-slot questions.'
        },
        {
          label: 'Layer 2',
          title: 'Skills prompt injection',
          description: 'Loads SKILL.md front matter, trigger words, and priority to choose domain rules dynamically.'
        },
        {
          label: 'Layer 3',
          title: 'RAG context',
          description: 'Adds grounded documents through demo, lightweight, or pgvector mode with downgrade paths.'
        },
        {
          label: 'Layer 4',
          title: 'Structured plan contract',
          description: 'Normalizes model output into TravelPlan cards and falls back to a safe plan when needed.'
        }
      ],
      toolBoundary: [
        {
          title: 'SyManus',
          meta: 'Controlled agent',
          description: 'Tool-focused agent for portfolio tasks and artifact generation.'
        },
        {
          title: 'BaseAgent / ReActAgent / ToolCallAgent',
          meta: 'Agent spine',
          description: 'State, step loop, reasoning actions, and tool-call contract.'
        },
        {
          title: 'Guarded Tools',
          meta: 'Allowlisted execution',
          description: 'Search, fetch, files, download, terminal, PDF, image search, Terminate.'
        },
        {
          title: 'Artifact Registry',
          meta: 'Output ownership',
          description: 'Generated files are registered with metadata before the UI exposes them.'
        },
        {
          title: 'Secure Preview / Download',
          meta: 'User boundary',
          description: 'Preview and download use safe links instead of raw filesystem paths.'
        }
      ],
      guardrails: [
        {
          title: 'Input Guardrail',
          layer: '01',
          description: 'Screens prompt intent before planning or tool selection.'
        },
        {
          title: 'Tool Guardrail',
          layer: '02',
          description: 'Checks allowlist, paths, command risk, and max-step boundaries.'
        },
        {
          title: 'Artifact Guardrail',
          layer: '03',
          description: 'Registers generated files and exposes only safe artifact links.'
        },
        {
          title: 'Output Guardrail',
          layer: '04',
          description: 'Softens risky claims and keeps uncertainty visible in final answers.'
        }
      ],
      qualityLoop: [
        {
          title: 'Agent Trace',
          meta: 'Observe',
          description: 'Timeline events show skills, RAG, planning, budget, risk, and tool states.'
        },
        {
          title: 'Eval Harness',
          meta: 'Score',
          description: 'Case checks catch missing questions, weak plans, unsafe promises, and regressions.'
        },
        {
          title: 'Rust CLI Doctor',
          meta: 'Inspect',
          description: 'Static checks verify workspace, env, config, and portfolio readiness.'
        },
        {
          title: 'Release Verification',
          meta: 'Gate',
          description: 'Build and demo paths are checked before the project is shown.'
        }
      ],
      runtimeModeCards: [
        {
          title: 'Public Demo Mode',
          meta: 'Stable showcase',
          type: 'demo',
          currentStatus: 'Current Mode',
          standbyStatus: 'Fallback stable fixture',
          currentDescription: 'Uses fixed fixtures and predictable traces so interviewers can review the flow without live cost.',
          standbyDescription: 'Remains available as the stable fixture path when live providers are being used.',
          tags: ['fixtures', 'stable UI', 'no quota risk']
        },
        {
          title: 'Owner Live Mode',
          meta: 'Real capability',
          type: 'live',
          currentStatus: 'Current Mode',
          standbyStatus: 'Gated by owner keys',
          currentDescription: 'Runs real model calls and the SyManus tool loop when provider keys and quota are available.',
          standbyDescription: 'Provider keys and quota unlock the real model calls and SyManus tool loop.',
          tags: ['API keys', 'provider quota', 'tool loop']
        },
        {
          title: 'RAG Modes',
          meta: 'Retrieval fallback',
          type: 'rag',
          currentStatus: 'Current',
          currentDescription: 'Chooses demo, lightweight, or pgvector retrieval and downgrades cleanly when a layer is unavailable.',
          tags: ['demo', 'lightweight', 'pgvector']
        }
      ]
    }
  },
  computed: {
    normalizedRagMode() {
      const mode = String(this.demoStatus.ragMode || 'demo').trim().toLowerCase()
      return ['demo', 'lightweight', 'pgvector'].includes(mode) ? mode : 'demo'
    },
    runtimeModes() {
      const demoCurrent = this.demoStatus.demoMode
      return this.runtimeModeCards.map((mode) => {
        if (mode.type === 'rag') {
          return {
            ...mode,
            state: 'current',
            status: `${mode.currentStatus}: ${this.normalizedRagMode}`,
            description: mode.currentDescription,
            tags: [`Current: ${this.normalizedRagMode}`, ...mode.tags.filter((tag) => tag !== this.normalizedRagMode)]
          }
        }

        const isCurrent = mode.type === 'demo' ? demoCurrent : !demoCurrent
        return {
          ...mode,
          state: isCurrent ? 'current' : 'standby',
          status: isCurrent ? mode.currentStatus : mode.standbyStatus,
          description: isCurrent ? mode.currentDescription : mode.standbyDescription
        }
      })
    }
  },
  mounted() {
    this.fetchDemoStatus()
  },
  methods: {
    async fetchDemoStatus() {
      try {
        const { data } = await api.get('/travel/demo-status')
        this.demoStatus = { ...this.demoStatus, ...data }
      } catch (error) {
        console.warn('Failed to load runtime capability status.', error)
      }
    }
  }
}
</script>
