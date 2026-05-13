import RpgHome from '../views/RpgHome.vue'
import ProfilePage from '../views/ProfilePage.vue'
import ProjectsPage from '../views/ProjectsPage.vue'
import SkillsPage from '../views/SkillsPage.vue'
import ArchitecturePage from '../views/ArchitecturePage.vue'
import TracePage from '../views/TracePage.vue'
import EvalsPage from '../views/EvalsPage.vue'
import RagLibraryPage from '../views/RagLibraryPage.vue'
import TravelChat from '../views/TravelChat.vue'
import ManusChat from '../views/ManusChat.vue'
import PortfolioPage from '../views/PortfolioPage.vue'

const page = (props) => ({
  component: PortfolioPage,
  props
})

export default [
  { path: '/', name: 'RpgHome', component: RpgHome },
  { path: '/profile', name: 'Profile', component: ProfilePage },
  { path: '/projects', name: 'Projects', component: ProjectsPage },
  { path: '/skills', name: 'Skills', component: SkillsPage },
  { path: '/architecture', name: 'Architecture', component: ArchitecturePage },
  { path: '/trace', name: 'Trace', component: TracePage },
  { path: '/evals', name: 'Evals', component: EvalsPage },
  { path: '/rag-library', name: 'RagLibrary', component: RagLibraryPage },
  { path: '/travel-agent', name: 'TravelAgent', component: TravelChat },
  { path: '/travel', redirect: '/travel-agent' },
  { path: '/manus-agent', name: 'ManusAgent', component: ManusChat },
  { path: '/manus', redirect: '/manus-agent' },
  {
    path: '/blog',
    name: 'Blog',
    ...page({
      eyebrow: 'Scroll Tower',
      title: 'Engineering Notes',
      subtitle: 'Architecture writing, debugging notes, and AI engineering reflections.',
      description: 'This first version keeps the blog route navigable. Future work can connect markdown posts or a lightweight content index.',
      items: [
        { title: 'Architecture Notes', text: 'Backend boundaries, Agent orchestration, and product decisions.' },
        { title: 'Build Logs', text: 'A lightweight place for future technical writing.' }
      ]
    })
  },
  {
    path: '/tavern',
    name: 'Tavern',
    ...page({
      eyebrow: 'Tavern Board',
      title: 'Contact Board',
      subtitle: 'A warm place for questions, opportunities, and next steps.',
      description: 'Contact links can be wired after profile metadata is finalized.',
      items: [
        { title: 'Open to Talk', text: 'Backend, full-stack, and Agent engineering roles.' },
        { title: 'Next Step', text: 'Add GitHub, blog, email, and resume links from /api/rpg/profile.' }
      ]
    })
  }
]
