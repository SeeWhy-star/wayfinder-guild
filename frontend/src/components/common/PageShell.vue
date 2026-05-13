<template>
  <section class="page-shell">
    <router-link v-if="back" class="back-link" to="/">&lt;- Back to Wayfinder Guild</router-link>
    <p class="eyebrow">{{ eyebrow }}</p>
    <div class="page-title-row">
      <div>
        <h1>{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      </div>
      <slot name="action"></slot>
    </div>
    <slot></slot>
  </section>
</template>

<script>
export default {
  name: 'PageShell',
  props: {
    eyebrow: { type: String, default: 'Wayfinder Guild' },
    title: { type: String, required: true },
    subtitle: { type: String, default: '' },
    back: { type: Boolean, default: true }
  }
}
</script>
