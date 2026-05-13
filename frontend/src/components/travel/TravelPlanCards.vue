<template>
  <div v-if="plan" class="plan-result">
    <StarCard title="Summary" :description="displaySummary">
      <template #meta>{{ plan.destination || 'Destination TBD' }}</template>
      <dl class="profile-facts">
        <div v-if="displayDays">
          <dt>Days</dt>
          <dd>{{ displayDays }}</dd>
        </div>
        <div v-if="shouldShowTravelers">
          <dt>Travelers</dt>
          <dd>{{ plan.travelers || 'TBD' }}</dd>
        </div>
        <div v-if="displayDeparture">
          <dt>Departure</dt>
          <dd>{{ displayDeparture }}</dd>
        </div>
      </dl>
    </StarCard>

    <StarCard v-if="plan.budget" title="Budget" :description="displayBudgetNote">
      <template #meta>{{ plan.budget.total || 'Estimate' }} {{ plan.budget.currency || '' }}</template>
      <div class="budget-list">
        <article v-for="item in budgetItems" :key="item.key" class="budget-item">
          <strong>{{ item.label }}</strong>
          <p v-if="item.note">{{ item.note }}</p>
        </article>
      </div>
    </StarCard>

    <StarCard v-if="plan.itineraryDays?.length" title="Itinerary Days">
      <div class="itinerary-list">
        <article v-for="day in plan.itineraryDays" :key="day.day || day.title">
          <details open>
            <summary>
              <strong>Day {{ day.day || '?' }} {{ day.theme || day.title || '' }}</strong>
              <span v-if="day.pace">{{ day.pace }}</span>
            </summary>
            <div class="day-details">
              <p v-if="day.summary || day.description">{{ day.summary || day.description }}</p>
              <p v-if="day.transport"><strong>Transport:</strong> {{ day.transport }}</p>
              <div v-if="day.activities?.length" class="activity-list">
                <article v-for="(activity, activityIndex) in day.activities" :key="`${day.day || 'day'}-${activityIndex}`">
                  <div class="activity-heading">
                    <span v-if="activity.time">{{ activity.time }}</span>
                    <strong>{{ activity.title || 'Activity' }}</strong>
                  </div>
                  <p v-if="activity.description">{{ activity.description }}</p>
                  <p v-if="activity.area || activity.costLevel" class="activity-meta">
                    {{ [activity.area, activity.costLevel && `Cost: ${activity.costLevel}`].filter(Boolean).join(' · ') }}
                  </p>
                  <ul v-if="activity.tips?.length" class="mini-list">
                    <li v-for="tip in activity.tips" :key="tip">{{ tip }}</li>
                  </ul>
                </article>
              </div>
              <p v-if="day.meals?.length"><strong>Meals:</strong> {{ day.meals.join('；') }}</p>
              <p v-if="day.reminders?.length"><strong>Reminders:</strong> {{ day.reminders.join('；') }}</p>
              <p v-if="day.accommodation"><strong>Accommodation:</strong> {{ day.accommodation }}</p>
            </div>
          </details>
        </article>
      </div>
    </StarCard>

    <StarCard v-if="plan.transportation?.length" title="Transportation">
      <ul class="feature-list">
        <li v-for="item in plan.transportation" :key="item">{{ item }}</li>
      </ul>
    </StarCard>

    <StarCard v-if="plan.accommodation?.length" title="Accommodation">
      <ul class="feature-list">
        <li v-for="item in plan.accommodation" :key="item">{{ item }}</li>
      </ul>
    </StarCard>

    <StarCard v-if="displayRisks.length" title="Risks and Guardrails">
      <ul class="feature-list">
        <li v-for="risk in displayRisks" :key="risk">{{ risk }}</li>
      </ul>
    </StarCard>

    <StarCard v-if="plan.alternatives?.length" title="Alternatives">
      <ul class="feature-list">
        <li v-for="item in plan.alternatives" :key="item">{{ item }}</li>
      </ul>
    </StarCard>

    <StarCard v-if="plan.loadedSkills?.length" title="Loaded Skills">
      <TagList :items="plan.loadedSkills" />
    </StarCard>

    <StarCard title="Plan Quality Score" description="Scores this generated TravelPlan against your current request.">
      <template #meta>Current Request</template>
      <div class="form-actions">
        <button type="button" :disabled="scoreLoading" @click="$emit('score-current-plan')">
          {{ scoreLoading ? 'Scoring...' : 'Score Current Plan' }}
        </button>
      </div>
      <p class="draft-notice">
        Scores this generated TravelPlan against your current request.
      </p>
      <p v-if="scoreError" class="draft-notice">{{ scoreError }}</p>
      <div v-if="scoreResult" class="eval-run-panel compact-eval-panel">
        <div class="eval-score-row">
          <div>
            <p class="area-kicker">Scored Against Current Request</p>
            <h2>{{ scoreResult.result.caseName }}</h2>
            <span :class="['score-chip', scoreResult.result.passed ? 'pass' : 'fail']">
              {{ scoreResult.result.passed ? 'PASS' : 'FAIL' }}
              {{ scoreResult.result.score }}/{{ scoreResult.result.maxScore }}
            </span>
          </div>
          <div class="eval-input-block">
            <strong>Current Request</strong>
            <p>{{ scoreResult.input }}</p>
          </div>
        </div>
        <div class="eval-rule-grid">
          <article
            v-for="rule in scoreResult.result.rules"
            :key="rule.rule"
            :class="['rule-result-sample', rule.passed ? 'passed' : 'failed']"
          >
            <strong>{{ rule.rule }}</strong>
            <span :class="['score-chip', rule.passed ? 'pass' : 'fail']">
              {{ rule.passed ? 'PASS' : 'FAIL' }} {{ rule.score }}/{{ rule.maxScore }}
            </span>
            <p>{{ rule.message }}</p>
          </article>
        </div>
      </div>
    </StarCard>

    <div class="card-actions">
      <router-link :to="{ path: '/trace', query: { chatId: planChatId } }">
        {{ traceLinkLabel }}
      </router-link>
    </div>
  </div>
</template>

<script>
import StarCard from '../common/StarCard.vue'
import TagList from '../common/TagList.vue'

export default {
  name: 'TravelPlanCards',
  components: { StarCard, TagList },
  props: {
    plan: { type: Object, default: null },
    planMessage: { type: String, default: '' },
    planChatId: { type: String, default: '' },
    scoreLoading: { type: Boolean, default: false },
    scoreError: { type: String, default: '' },
    scoreResult: { type: Object, default: null },
    traceLinkLabel: { type: String, default: 'View trace' }
  },
  emits: ['score-current-plan'],
  computed: {
    budgetItems() {
      const items = this.plan?.budget?.items || this.plan?.budget?.breakdown || this.plan?.budget?.itemized || []
      if (!Array.isArray(items)) return []
      return items.map((item, index) => {
        if (typeof item === 'string') return { key: item, label: item, note: '' }
        const amount = item.amount || item.estimate || ''
        const currency = item.currency || this.plan?.budget?.currency || ''
        const label = `${item.name || item.category || 'Item'}: ${amount} ${currency}`.trim()
        return {
          key: `${label}-${index}`,
          label,
          note: item.note || item.description || ''
        }
      })
    },
    displaySummary() {
      if (this.hasExplicitTravelers(this.planMessage)) return this.plan?.summary || ''
      return this.stripInventedTravelerAssumption(this.plan?.summary || '')
    },
    displayBudgetNote() {
      const note = this.plan?.budget?.note || this.plan?.budget?.uncertaintyNote || ''
      if (this.hasExplicitTravelers(this.planMessage)) return note
      return this.stripInventedTravelerAssumption(note)
    },
    displayRisks() {
      const risks = Array.isArray(this.plan?.risks) ? this.plan.risks : []
      return risks
        .map((risk) => this.normalizeMissingFieldRisk(risk))
        .filter(Boolean)
        .map((risk) => this.hasExplicitTravelers(this.planMessage) ? risk : this.stripInventedTravelerAssumption(risk))
        .filter(Boolean)
    },
    shouldShowTravelers() {
      if (!this.plan?.travelers) return false
      return this.hasExplicitTravelers(this.planMessage)
    },
    displayDays() {
      return this.extractDays(this.planMessage) || this.plan?.days || ''
    },
    displayDeparture() {
      return this.extractDeparture(this.planMessage) || this.plan?.departure || ''
    }
  },
  methods: {
    hasExplicitTravelers(text) {
      return /\b\d+\s*(people|persons|travelers|adults|kids|children|family members)\b/i.test(text)
        || /\bfor\s+(two|three|four|five|six|seven|eight|nine|ten)\b/i.test(text)
        || /(\d+|[一二两三四五六七八九十]+)\s*(人|位|个大人|个孩子|个成人|个小孩)/.test(text)
    },
    extractDays(text) {
      const value = String(text || '')
      const englishMatch = value.match(/\b(\d+)\s*[- ]?day\b/i)
      if (englishMatch) return Number(englishMatch[1])
      const chineseMatch = value.match(/(\d+|[一二两三四五六七八九十]+)\s*(天|日|晚)/)
      return chineseMatch ? chineseMatch[1] : ''
    },
    extractDeparture(text) {
      const value = String(text || '')
      const englishMatch = value.match(/\bfrom\s+([A-Za-z][A-Za-z\s-]*?)\s+to\b/i)
      if (englishMatch) return englishMatch[1].trim()
      const chineseMatch = value.match(/(?:从|由)(北京|上海|京都|东京|大阪|杭州|苏州|成都|云南|新疆)(?:去|到|至)/)
      if (chineseMatch) return chineseMatch[1]
      const suffixMatch = value.match(/(北京|上海|京都|东京|大阪|杭州|苏州|成都|云南|新疆)\s*出发/)
      return suffixMatch ? suffixMatch[1] : ''
    },
    normalizeMissingFieldRisk(risk) {
      const text = String(risk || '')
      if (/Still missing key information/i.test(text)) {
        return this.hasExplicitTravelers(this.planMessage) ? '' : this.travelerMissingRisk()
      }
      if (!text.includes('仍需补充关键信息')) return text
      const missing = []
      if (!this.extractDays(this.planMessage)) missing.push('天数')
      if (!this.hasExplicitTravelers(this.planMessage)) missing.push('人数')
      if (!this.hasExplicitBudget(this.planMessage)) missing.push('预算')
      if (!missing.length) return ''
      if (missing.length === 1 && missing[0] === '人数') return this.travelerMissingRisk()
      return `仍需补充：${missing.join('、')}。当前行程和预算仅作草案，确认信息后应重新校准。`
    },
    stripInventedTravelerAssumption(text) {
      return String(text || '')
        .replace(/预算按\s*\d+\s*人估算[，,。.]?/g, this.travelerMissingRisk())
        .replace(/按\s*\d+\s*人估算[，,。.]?/g, '')
        .replace(/estimated for\s*\d+\s*(people|travelers|persons)[,.]?/gi, this.travelerMissingRisk())
        .trim()
    },
    travelerMissingRisk() {
      const budget = this.plan?.budget
      const total = budget?.total && budget?.currency ? `${budget.total} ${budget.currency}` : '当前'
      return `出行人数未指定，${total} 预算需要按实际人数重新校准，尤其是机票、住宿和餐饮。`
    },
    hasExplicitBudget(text) {
      return /\b\d+(?:,\d{3})*\s*(cny|rmb|usd|jpy)\b/i.test(text)
        || /预算|(\d+|[一二两三四五六七八九十]+)\s*(万|元|块)/.test(text)
    }
  }
}
</script>
