<template>
  <div v-if="items?.length" class="tag-list">
    <span v-for="item in items" :key="String(item)" class="tag">{{ item }}</span>
  </div>
</template>

<script>
export default {
  name: 'TagList',
  props: {
    items: { type: Array, default: () => [] }
  }
}
</script>
