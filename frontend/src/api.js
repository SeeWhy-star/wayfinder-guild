import axios from 'axios'

export const OWNER_TOKEN_STORAGE_KEY = 'wayfinder.ownerToken'
export const OWNER_TOKEN_HEADER = 'X-Wayfinder-Owner-Token'
export const OWNER_TOKEN_COOKIE = 'WAYFINDER_OWNER_TOKEN'

let ownerVerified = false
let ownerStatus = {
  ownerTokenConfigured: false,
  ownerVerified: false
}

const baseURL =
  (typeof import.meta !== 'undefined' &&
    import.meta.env &&
    import.meta.env.VITE_API_BASE) ||
  '/api'

const instance = axios.create({
  baseURL,
  timeout: 30000,
  withCredentials: true
})

export function getOwnerToken() {
  return sessionStorage.getItem(OWNER_TOKEN_STORAGE_KEY) || ''
}

export function hasOwnerToken() {
  return Boolean(getOwnerToken())
}

export function isOwnerVerified() {
  return ownerVerified
}

export function getOwnerStatus() {
  return { ...ownerStatus }
}

export function setOwnerToken(token) {
  const value = String(token || '').trim()
  if (!value) {
    clearOwnerToken()
    return
  }
  sessionStorage.setItem(OWNER_TOKEN_STORAGE_KEY, value)
  document.cookie = `${OWNER_TOKEN_COOKIE}=${encodeURIComponent(value)}; Path=/api; SameSite=Strict`
  ownerVerified = false
  ownerStatus = { ...ownerStatus, ownerVerified: false }
  dispatchOwnerTokenChanged('pending')
}

export function clearOwnerToken(options = {}) {
  sessionStorage.removeItem(OWNER_TOKEN_STORAGE_KEY)
  document.cookie = `${OWNER_TOKEN_COOKIE}=; Path=/api; Max-Age=0; SameSite=Strict`
  ownerVerified = false
  ownerStatus = { ...ownerStatus, ownerVerified: false }
  if (!options.silent) {
    dispatchOwnerTokenChanged('cleared')
  }
}

export async function validateOwnerToken() {
  if (!hasOwnerToken()) {
    ownerVerified = false
    ownerStatus = { ...ownerStatus, ownerVerified: false }
    dispatchOwnerTokenChanged('public')
    return getOwnerStatus()
  }
  try {
    const { data } = await instance.get('/travel/owner-status')
    ownerStatus = {
      ownerTokenConfigured: Boolean(data?.ownerTokenConfigured),
      ownerVerified: Boolean(data?.ownerVerified)
    }
    ownerVerified = ownerStatus.ownerVerified
    if (!ownerVerified) {
      clearOwnerToken({ silent: true })
      ownerStatus = {
        ownerTokenConfigured: Boolean(data?.ownerTokenConfigured),
        ownerVerified: false
      }
    }
    dispatchOwnerTokenChanged(ownerVerified ? 'verified' : 'failed')
    return getOwnerStatus()
  } catch (error) {
    clearOwnerToken({ silent: true })
    ownerStatus = { ownerTokenConfigured: false, ownerVerified: false }
    dispatchOwnerTokenChanged('failed')
    return getOwnerStatus()
  }
}

function dispatchOwnerTokenChanged(state) {
  window.dispatchEvent(new CustomEvent('wayfinder-owner-token-changed', {
    detail: {
      state,
      enabled: ownerVerified,
      ownerVerified,
      ownerTokenConfigured: ownerStatus.ownerTokenConfigured,
      hasToken: hasOwnerToken()
    }
  }))
}

instance.interceptors.request.use((config) => {
  const token = getOwnerToken()
  if (token) {
    config.headers = config.headers || {}
    config.headers[OWNER_TOKEN_HEADER] = token
  }
  return config
})

export default instance
