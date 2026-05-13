<template>
  <section class="portfolio-page">
    <router-link class="back-link" to="/">&lt;- Back to Wayfinder Guild</router-link>
    <p class="eyebrow">{{ eyebrow }}</p>
    <h1>{{ title }}</h1>
    <p class="subtitle">{{ subtitle }}</p>

    <div class="page-panel">
      <h2>{{ panelTitle }}</h2>
      <p>{{ description }}</p>
      <div class="placeholder-grid">
        <article v-for="item in items" :key="item.title">
          <strong>{{ item.title }}</strong>
          <span>{{ item.text }}</span>
        </article>
      </div>
    </div>
  </section>
</template>

<script>
export default {
  name: 'PortfolioPage',
  props: {
    eyebrow: { type: String, default: 'Wayfinder Guild' },
    title: { type: String, required: true },
    subtitle: { type: String, required: true },
    panelTitle: { type: String, default: 'First Version' },
    description: { type: String, required: true },
    items: {
      type: Array,
      default: () => [
        { title: 'API Ready', text: 'This page is wired into Vue Router and ready to consume backend metadata.' },
        { title: 'MVP Scope', text: 'The first pass keeps the experience simple, warm, and navigable.' }
      ]
    }
  }
}
</script>
