import Phaser from 'phaser'

const AREA_ROUTES = {
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

const BUILDING_COLORS = {
  entry: 0xc87850,
  profile: 0x8fa58f,
  'agent-demo': 0xb89462,
  tooling: 0x9f7457,
  rag: 0x718b78,
  skills: 0x9a8b72,
  quality: 0xc4a96b,
  projects: 0xa97a5a,
  writing: 0x7d8d86,
  contact: 0xa66e5b
}

export default class WayfinderScene extends Phaser.Scene {
  constructor() {
    super('WayfinderScene')
    this.world = null
    this.onFocusArea = null
    this.onEnterArea = null
    this.buildings = []
    this.focusedArea = null
  }

  init(data) {
    this.world = data.world
    this.onFocusArea = data.onFocusArea
    this.onEnterArea = data.onEnterArea
  }

  create() {
    this.drawSky()
    this.drawRoads()
    this.createBuildings()
    this.createPlayer()
    this.createControls()
    this.createHintSign()
  }

  update() {
    this.movePlayer()
    this.updateFocusedArea()
  }

  drawSky() {
    const { width, height } = this.scale
    const bg = this.add.graphics()
    bg.fillGradientStyle(0x202d32, 0x304044, 0x3d4b4b, 0x1e2a2d, 1)
    bg.fillRect(0, 0, width, height)

    const starColors = [0xe9dfc8, 0xc4d0c5, 0xd7c4a7]
    for (let i = 0; i < 120; i += 1) {
      const x = Phaser.Math.Between(12, width - 12)
      const y = Phaser.Math.Between(12, height - 12)
      const radius = Phaser.Math.FloatBetween(0.8, 2.2)
      const alpha = Phaser.Math.FloatBetween(0.35, 0.9)
      const color = Phaser.Utils.Array.GetRandom(starColors)
      this.add.circle(x, y, radius, color, alpha)
    }

    const mist = this.add.graphics()
    mist.fillStyle(0x879080, 0.14)
    mist.fillEllipse(width * 0.45, height * 0.52, width * 0.92, height * 0.56)
    mist.fillStyle(0xc08b61, 0.08)
    mist.fillEllipse(width * 0.56, height * 0.62, width * 0.75, height * 0.36)
  }

  drawRoads() {
    const { width, height } = this.scale
    const center = { x: width / 2, y: height / 2 }
    const road = this.add.graphics()
    road.lineStyle(18, 0xb8a88e, 0.28)

    ;(this.world?.areas || []).forEach((area) => {
      const point = this.toScenePosition(area.position)
      road.beginPath()
      road.moveTo(center.x, center.y)
      road.lineTo(point.x, point.y)
      road.strokePath()
    })

    road.lineStyle(4, 0xd8c8a6, 0.3)
    road.strokeCircle(center.x, center.y, Math.min(width, height) * 0.19)
  }

  createBuildings() {
    this.buildings = (this.world?.areas || []).map((area) => {
      const point = this.toScenePosition(area.position)
      const color = BUILDING_COLORS[area.type] || 0xffd36e
      const glow = this.add.circle(point.x, point.y, 26, color, 0.18)
      const base = this.add.circle(point.x, point.y, 17, color, 0.92)
      base.setStrokeStyle(3, 0xe6d7b9, 0.95)
      const roof = this.add.triangle(point.x, point.y - 21, 0, 18, 18, 18, 9, 0, 0x3b4545, 0.95)
      const label = this.add.text(point.x, point.y + 28, area.nameZh || area.nameEn, {
        fontFamily: 'Arial, sans-serif',
        fontSize: '12px',
        color: '#f0e8d4',
        align: 'center',
        backgroundColor: 'rgba(32, 45, 50, 0.82)',
        padding: { x: 6, y: 3 }
      }).setOrigin(0.5, 0)

      this.tweens.add({
        targets: glow,
        scale: 1.18,
        alpha: 0.34,
        yoyo: true,
        repeat: -1,
        duration: Phaser.Math.Between(1300, 2100),
        ease: 'Sine.easeInOut'
      })

      return { area, x: point.x, y: point.y, glow, base, roof, label }
    })
  }

  createPlayer() {
    const { width, height } = this.scale
    this.playerGlow = this.add.circle(width / 2, height / 2 + 42, 18, 0xc87850, 0.24)
    this.player = this.add.circle(width / 2, height / 2 + 42, 10, 0xe1b86d, 1)
    this.player.setStrokeStyle(3, 0xa9c3b0, 1)
    this.playerName = this.add.text(width / 2, height / 2 + 60, 'The Wayfinder', {
      fontFamily: 'Arial, sans-serif',
      fontSize: '12px',
      color: '#f0e8d4'
    }).setOrigin(0.5, 0)
  }

  createControls() {
    this.cursors = this.input.keyboard.createCursorKeys()
    this.keys = this.input.keyboard.addKeys('W,A,S,D,E')
    this.input.keyboard.on('keydown-E', () => {
      if (!this.focusedArea || !this.onEnterArea) return
      this.onEnterArea({
        area: this.focusedArea,
        route: AREA_ROUTES[this.focusedArea.id] || '/'
      })
    })
  }

  createHintSign() {
    const { width } = this.scale
    this.hintSign = this.add.text(width / 2, 22, 'Move with WASD / Arrow Keys · Approach a building and press E', {
      fontFamily: 'Arial, sans-serif',
      fontSize: '14px',
      color: '#f0e8d4',
      backgroundColor: 'rgba(32, 45, 50, 0.82)',
      padding: { x: 12, y: 8 }
    }).setOrigin(0.5, 0)
  }

  movePlayer() {
    const speed = 3
    let dx = 0
    let dy = 0
    if (this.cursors.left.isDown || this.keys.A.isDown) dx -= speed
    if (this.cursors.right.isDown || this.keys.D.isDown) dx += speed
    if (this.cursors.up.isDown || this.keys.W.isDown) dy -= speed
    if (this.cursors.down.isDown || this.keys.S.isDown) dy += speed

    if (dx !== 0 && dy !== 0) {
      dx *= 0.72
      dy *= 0.72
    }

    const { width, height } = this.scale
    this.player.x = Phaser.Math.Clamp(this.player.x + dx, 24, width - 24)
    this.player.y = Phaser.Math.Clamp(this.player.y + dy, 48, height - 24)
    this.playerGlow.setPosition(this.player.x, this.player.y)
    this.playerName.setPosition(this.player.x, this.player.y + 18)
  }

  updateFocusedArea() {
    let nearest = null
    let nearestDistance = Number.MAX_VALUE

    this.buildings.forEach((building) => {
      const distance = Phaser.Math.Distance.Between(this.player.x, this.player.y, building.x, building.y)
      const isNear = distance < 62
      building.base.setScale(isNear ? 1.18 : 1)
      building.label.setColor(isNear ? '#ffffff' : '#f0e8d4')
      if (isNear && distance < nearestDistance) {
        nearest = building.area
        nearestDistance = distance
      }
    })

    if (nearest?.id !== this.focusedArea?.id) {
      this.focusedArea = nearest
      if (this.onFocusArea) this.onFocusArea(nearest)
    }
  }

  toScenePosition(position) {
    const { width, height } = this.scale
    return {
      x: Phaser.Math.Clamp((position?.x || 50) / 100 * width, 42, width - 42),
      y: Phaser.Math.Clamp((position?.y || 50) / 100 * height, 72, height - 42)
    }
  }
}
