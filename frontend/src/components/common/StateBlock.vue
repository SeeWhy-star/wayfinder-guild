<template>
  <div :class="['state-block', type]">
    <strong>{{ title }}</strong>
    <p>{{ message }}</p>
  </div>
</template>

<script>
export default {
  name: 'StateBlock',
  props: {
    type: { type: String, default: 'empty' },
    title: { type: String, required: true },
    message: { type: String, required: true }
  }
}
</script>
