<template>
  <div id="app-root">
    <header class="topbar">
      <router-link class="brand" to="/">
        <span class="brand-mark">*</span>
        <span>
          <strong>Wayfinder Guild</strong>
          <small>&#35753; AI &#25214;&#21040;&#27491;&#30830;&#30340;&#36335;</small>
        </span>
      </router-link>
      <nav aria-label="Main navigation">
        <router-link to="/">Map</router-link>
        <router-link to="/profile">Profile</router-link>
        <router-link to="/projects">Projects</router-link>
        <router-link to="/travel-agent">Travel Agent</router-link>
        <router-link to="/manus-agent">Tool Agent</router-link>
      </nav>
      <form class="owner-token-form" @submit.prevent="saveOwnerToken">
        <input
          v-model="ownerTokenInput"
          type="password"
          autocomplete="off"
          placeholder="Owner token"
          aria-label="Owner token"
        />
        <button type="submit">{{ ownerEnabled ? 'Update' : 'Owner' }}</button>
        <button v-if="ownerEnabled" type="button" class="owner-clear-button" @click="removeOwnerToken">
          Clear
        </button>
        <small v-if="ownerNotice">{{ ownerNotice }}</small>
      </form>
    </header>
    <main>
      <router-view />
    </main>
  </div>
</template>

<script>
import { clearOwnerToken, getOwnerToken, isOwnerVerified, setOwnerToken, validateOwnerToken } from './api'

export default {
  name: 'App',
  data() {
    return {
      ownerTokenInput: '',
      ownerEnabled: isOwnerVerified(),
      ownerNotice: ''
    }
  },
  mounted() {
    this.ownerTokenInput = getOwnerToken()
    window.addEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
    validateOwnerToken()
  },
  beforeUnmount() {
    window.removeEventListener('wayfinder-owner-token-changed', this.refreshOwnerState)
  },
  methods: {
    async saveOwnerToken() {
      setOwnerToken(this.ownerTokenInput)
      this.ownerNotice = 'Verifying Owner token...'
      const status = await validateOwnerToken()
      this.ownerEnabled = status.ownerVerified
      this.ownerNotice = status.ownerVerified ? 'Owner verified. Live actions are unlocked.' : 'Owner verification failed; public demo remains active.'
      if (!status.ownerVerified) this.ownerTokenInput = ''
    },
    removeOwnerToken() {
      clearOwnerToken()
      this.ownerTokenInput = ''
      this.refreshOwnerState()
    },
    refreshOwnerState(event) {
      this.ownerEnabled = isOwnerVerified()
      const state = event?.detail?.state || ''
      if (state === 'cleared' || state === 'public') this.ownerNotice = ''
      if (state === 'failed') this.ownerNotice = 'Owner not enabled; public demo remains active.'
      if (state === 'verified') this.ownerNotice = 'Owner verified. Live actions are unlocked.'
    }
  }
}
</script>
