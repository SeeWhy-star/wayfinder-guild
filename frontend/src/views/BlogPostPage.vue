<template>
  <PageShell v-if="post" class="blog-post-shell" eyebrow="Incident Review" :title="post.title" :subtitle="post.hero">
    <template #action>
      <router-link class="secondary-button blog-index-link" to="/blog">All posts</router-link>
    </template>

    <article class="blog-post">
      <header class="blog-post-header">
        <div class="blog-card-meta">
          <span>{{ post.type }}</span>
          <span>{{ formatDate(post.date) }}</span>
          <span>{{ post.readingTime }}</span>
        </div>
        <TagList :items="post.tags" />
      </header>

      <div class="blog-content">
        <template v-for="(section, index) in post.sections" :key="index">
          <h2 v-if="section.type === 'heading'">{{ section.text }}</h2>
          <p v-else-if="section.type === 'paragraph'">{{ section.text }}</p>
          <ul v-else-if="section.type === 'list'">
            <li v-for="item in section.items" :key="item">{{ item }}</li>
          </ul>
          <pre v-else-if="section.type === 'code'"><code>{{ section.text }}</code></pre>
          <aside v-else-if="section.type === 'callout'" class="blog-callout">{{ section.text }}</aside>
        </template>
      </div>
    </article>
  </PageShell>

  <PageShell
    v-else
    eyebrow="Scroll Tower"
    title="Post not found"
    subtitle="The requested blog entry is not in the static content index."
  >
    <router-link class="secondary-button blog-index-link" to="/blog">Back to Blog</router-link>
  </PageShell>
</template>

<script>
import PageShell from '../components/common/PageShell.vue'
import TagList from '../components/common/TagList.vue'
import { getBlogPost } from '../content/blogPosts'

export default {
  name: 'BlogPostPage',
  components: { PageShell, TagList },
  computed: {
    post() {
      return getBlogPost(this.$route.params.slug)
    }
  },
  methods: {
    formatDate(date) {
      return new Intl.DateTimeFormat('en', {
        year: 'numeric',
        month: 'short',
        day: '2-digit'
      }).format(new Date(`${date}T00:00:00`))
    }
  }
}
</script>
