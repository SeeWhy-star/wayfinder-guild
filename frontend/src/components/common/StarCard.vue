<template>
  <article class="star-card">
    <div v-if="$slots.meta || meta" class="card-meta">
      <slot name="meta">{{ meta }}</slot>
    </div>
    <h2 v-if="title">{{ title }}</h2>
    <p v-if="description" class="card-description">{{ description }}</p>
    <slot></slot>
  </article>
</template>

<script>
export default {
  name: 'StarCard',
  props: {
    title: { type: String, default: '' },
    description: { type: String, default: '' },
    meta: { type: String, default: '' }
  }
}
</script>
