<template>
  <section class="rpg-home">
    <div class="hero-copy">
      <p class="eyebrow">Wayfinder Guild</p>
      <h1>Where AI finds its way.</h1>
      <p class="tagline">&#35753; AI &#25214;&#21040;&#27491;&#30830;&#30340;&#36335;&#12290;</p>
      <p class="intro">
        &#36825;&#37324;&#19981;&#26159;&#19968;&#20221;&#26222;&#36890;&#31616;&#21382;&#65292;&#32780;&#26159;&#19968;&#24231;&#21487;&#20197;&#25506;&#32034;&#30340; AI &#24037;&#31243;&#23567;&#38215;&#12290;
        &#38752;&#36817;&#24314;&#31569;&#65292;&#25353; E &#36827;&#20837;&#23545;&#24212;&#30340;&#31995;&#32479;&#23637;&#21306;&#12290;
      </p>
    </div>

    <div class="quick-routes" aria-label="Quick Routes">
      <router-link
        v-for="route in quickRoutes"
        :key="route.id"
        :to="resolveQuickRoute(route)"
      >
        <span>{{ route.labelEn }}</span>
        <small>{{ route.labelZh }}</small>
      </router-link>
    </div>

    <div class="map-shell">
      <div ref="gameContainer" class="game-container"></div>
      <aside class="area-card">
        <p class="area-kicker">当前地标</p>
        <h2>{{ activeArea?.nameZh || fallbackAreaName }}</h2>
        <p>
          {{ activeArea?.description || fallbackAreaDescription }}
        </p>
        <button v-if="activeArea" type="button" @click="enterArea">
          按 E 进入
        </button>
      </aside>
    </div>

    <p v-if="loadError" class="load-error">
      &#23567;&#38215;&#37197;&#32622;&#26242;&#26102;&#26410;&#33021;&#21152;&#36733;&#65292;&#24050;&#20351;&#29992;&#26412;&#22320;&#21344;&#20301;&#22320;&#22270;&#12290;
      &#35831;&#30830;&#35748;&#21518;&#31471; /api/rpg/world &#24050;&#21551;&#21160;&#12290;
    </p>
  </section>
</template>

<script>
import Phaser from 'phaser'
import api from '../api'
import WayfinderScene from '../game/WayfinderScene'

const QUICK_ROUTE_FALLBACK = [
  { id: 'view-resume', labelEn: 'View Resume', labelZh: '\u67e5\u770b\u7b80\u5386', path: '/profile' },
  { id: 'try-travel-agent', labelEn: 'Try Travel Agent', labelZh: '\u4f53\u9a8c\u65c5\u884c Agent', path: '/travel-agent' },
  { id: 'explore-projects', labelEn: 'Explore Projects', labelZh: '\u6d4f\u89c8\u9879\u76ee\u4f5c\u54c1', path: '/projects' },
  { id: 'read-architecture', labelEn: 'Read Architecture', labelZh: '\u9605\u8bfb\u7cfb\u7edf\u67b6\u6784', path: '/architecture' },
  { id: 'view-agent-trace', labelEn: 'View Agent Trace', labelZh: '\u67e5\u770b\u6267\u884c\u8f68\u8ff9', path: '/trace' }
]

const QUICK_ROUTE_MAP = {
  'profile-journal': '/profile',
  'travel-cabin': '/travel-agent',
  'voyage-forge': '/projects',
  'agentic-travel-backend': '/architecture',
  'eval-lighthouse': '/trace'
}

const FALLBACK_WORLD = {
  quickRoutes: QUICK_ROUTE_FALLBACK,
  areas: [
    {
      id: 'starlit-square',
      nameEn: 'Starlit Square',
      nameZh: '\u661f\u706f\u5e7f\u573a',
      type: 'entry',
      description: 'Wayfinder Guild \u7684\u5165\u53e3\u5e7f\u573a\u3002',
      position: { x: 50, y: 50 }
    }
  ]
}

export default {
  name: 'RpgHome',
  data() {
    return {
      world: null,
      activeArea: null,
      activeRoute: '/',
      game: null,
      loadError: false,
      fallbackAreaName: '\u661f\u706f\u5e7f\u573a',
      fallbackAreaDescription: '\u7528\u65b9\u5411\u952e\u6216 WASD \u5728\u5c0f\u9547\u4e2d\u79fb\u52a8\uff0c\u9760\u8fd1\u53d1\u5149\u5efa\u7b51\u67e5\u770b\u8bf4\u660e\u3002'
    }
  },
  computed: {
    quickRoutes() {
      return this.world?.quickRoutes?.length ? this.world.quickRoutes : QUICK_ROUTE_FALLBACK
    }
  },
  async mounted() {
    await this.loadWorld()
    this.createGame()
  },
  beforeUnmount() {
    if (this.game) {
      this.game.destroy(true)
      this.game = null
    }
  },
  methods: {
    async loadWorld() {
      try {
        const { data } = await api.get('/rpg/world')
        this.world = data
      } catch (error) {
        console.warn('Failed to load RPG world, using fallback.', error)
        this.world = FALLBACK_WORLD
        this.loadError = true
      }
    },
    createGame() {
      this.game = new Phaser.Game({
        type: Phaser.AUTO,
        parent: this.$refs.gameContainer,
        width: 960,
        height: 620,
        backgroundColor: '#10183f',
        scale: {
          mode: Phaser.Scale.FIT,
          autoCenter: Phaser.Scale.CENTER_BOTH
        },
        scene: WayfinderScene,
        render: {
          antialias: true
        }
      })

      this.game.scene.start('WayfinderScene', {
        world: this.world,
        onFocusArea: this.setActiveArea,
        onEnterArea: this.navigateToArea
      })
    },
    setActiveArea(area) {
      this.activeArea = area
      this.activeRoute = this.routeForArea(area)
    },
    navigateToArea(payload) {
      this.$router.push(payload.route)
    },
    enterArea() {
      this.$router.push(this.activeRoute || '/')
    },
    routeForArea(area) {
      if (!area) return '/'
      const routes = {
        'profile-journal': '/profile',
        'travel-cabin': '/travel-agent',
        'tool-workshop': '/manus-agent',
        'memory-library': '/rag-library',
        'constellation-hall': '/skills',
        'eval-lighthouse': '/evals',
        'voyage-forge': '/projects',
        'scroll-tower': '/blog',
        'tavern-board': '/tavern',
        'starlit-square': '/'
      }
      return routes[area.id] || '/'
    },
    resolveQuickRoute(route) {
      return QUICK_ROUTE_MAP[route.targetId] || route.path || '/'
    }
  }
}
</script>
