<template>
  <div class="capability-panel artifact-panel">
    <p class="area-kicker">Latest Files</p>
    <div v-if="artifacts.length" class="artifact-side-list">
      <div
        v-for="artifact in artifacts"
        :key="artifact.artifactId"
        class="artifact-side-card"
      >
        <strong>{{ artifact.fileName }}</strong>
        <span>{{ artifact.mimeType }}</span>
        <span>{{ formatBytes(artifact.size) }}</span>
        <span v-if="artifact.expiresAt">Expires {{ formatDate(artifact.expiresAt) }}</span>
        <span v-else>Recorded artifact metadata</span>
      </div>
    </div>
    <p v-else class="boundary-note">
      Portfolio Brief Pack, Trace Card Image, and successful live file tasks appear here with expiring preview/download links.
    </p>
  </div>
</template>

<script>
import { formatBytes, formatDate } from '../../utils/manusDemoRenderer'

export default {
  name: 'ArtifactSideList',
  props: {
    artifacts: { type: Array, default: () => [] }
  },
  methods: {
    formatBytes,
    formatDate
  }
}
</script>
