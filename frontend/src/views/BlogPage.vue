<template>
  <PageShell
    class="blog-page-shell"
    eyebrow="Scroll Tower"
    title="Engineering Notes"
    subtitle="Debugging write-ups, architecture notes, and AI engineering reflections from Wayfinder Guild."
  >
    <div class="blog-list">
      <article v-for="post in posts" :key="post.slug" class="blog-card">
        <div class="blog-card-meta">
          <span>{{ post.type }}</span>
          <span>{{ formatDate(post.date) }}</span>
          <span>{{ post.readingTime }}</span>
        </div>
        <h2>
          <router-link :to="`/blog/${post.slug}`">{{ post.title }}</router-link>
        </h2>
        <p>{{ post.summary }}</p>
        <TagList :items="post.tags" />
        <router-link class="blog-read-link" :to="`/blog/${post.slug}`">Read post</router-link>
      </article>
    </div>
  </PageShell>
</template>

<script>
import PageShell from '../components/common/PageShell.vue'
import TagList from '../components/common/TagList.vue'
import { blogPosts } from '../content/blogPosts'

export default {
  name: 'BlogPage',
  components: { PageShell, TagList },
  data() {
    return {
      posts: blogPosts
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
