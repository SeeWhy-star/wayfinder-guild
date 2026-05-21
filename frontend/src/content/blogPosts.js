export const blogPosts = [
  {
    slug: 'travel-agent-cors-403-postmortem',
    title: 'Wayfinder Guild 线上 Travel Agent 403 / CORS 排障复盘',
    summary:
      '一次看起来像 Owner 权限问题的线上 403，最终定位为生产环境 CORS 白名单遗漏。本文复盘浏览器、curl、Spring Boot、Nginx 与 Public Demo / Owner Live 双模式之间的排障路径。',
    date: '2026-05-21',
    readingTime: '8 min read',
    type: 'Incident Review',
    tags: ['CORS', 'Spring Boot', 'Nginx', 'Travel Agent', 'Public Demo'],
    hero: 'Production debugging notes for an AI travel planning demo.',
    sections: [
      {
        type: 'paragraph',
        text:
          'Wayfinder Guild 的线上公开演示运行在 seewhyai.me。前端由 Vue / Vite 构建并交给 Nginx 托管，后端是 Spring Boot 应用，通过 /api 由 Nginx 反向代理。线上演示采用 Public Demo / Owner Live 双模式：公开用户默认走固定的 TravelPlan、Demo RAG 和录制工具回放；Owner 验证通过后才允许触发真实模型和工具执行。'
      },
      {
        type: 'heading',
        text: '问题现象'
      },
      {
        type: 'paragraph',
        text:
          '在线上 Travel Agent 页面点击 Generate TravelPlan 后，请求失败。浏览器 Console 显示 POST https://seewhyai.me/api/travel/plan 返回 403，Network 预览中出现 Invalid CORS request。与此同时，在服务器环境里直接用 curl 请求 demo 模式接口却能成功返回 TravelPlan JSON。'
      },
      {
        type: 'list',
        items: [
          '浏览器请求失败，HTTP 403。',
          'curl 直测 demo 请求成功，HTTP 200。',
          '浏览器请求体里 liveMode 为 false。',
          'Owner Live 的无 token 403 与当前响应文本不同。'
        ]
      },
      {
        type: 'heading',
        text: '第一轮假设'
      },
      {
        type: 'paragraph',
        text:
          '按照设计，/api/travel/plan 在 liveMode=false 时应该走 demo fixture，公开可访问；只有 liveMode=true 且缺少 Owner 验证时才应该进入 Owner Live 权限拒绝。因此第一轮重点排查前端是否误发 liveMode:true、浏览器是否残留 Owner 状态、Nginx 是否转发异常、后端部署版本是否落后，以及浏览器与 curl 的请求头差异。'
      },
      {
        type: 'heading',
        text: '关键验证'
      },
      {
        type: 'code',
        language: 'bash',
        text:
          'curl -i https://seewhyai.me/api/travel/demo-status'
      },
      {
        type: 'paragraph',
        text:
          'demo-status 返回 200，并确认线上处于 Public Demo 模式。这说明后端服务、HTTPS 和 /api 代理链路基本可用。'
      },
      {
        type: 'code',
        language: 'bash',
        text:
          'curl -i -X POST https://seewhyai.me/api/travel/plan \\\n  -H "Content-Type: application/json" \\\n  -d \'{"message":"Plan a relaxed 5-day family trip from Shanghai to Kyoto.","chatId":"debug-demo-plan","liveMode":false}\''
      },
      {
        type: 'paragraph',
        text:
          '不带 Origin 的 demo 请求返回 200，说明 Controller 的 demo 分支本身没有失效，liveMode=false 也不会触发业务层 403。'
      },
      {
        type: 'code',
        language: 'bash',
        text:
          'curl -i -X POST https://seewhyai.me/api/travel/plan \\\n  -H "Content-Type: application/json" \\\n  -d \'{"message":"Plan a relaxed 5-day family trip from Shanghai to Kyoto.","chatId":"debug-live-plan","liveMode":true}\''
      },
      {
        type: 'paragraph',
        text:
          'liveMode=true 且没有 Owner 验证时，后端返回的是结构化 JSON，消息语义是 Owner Live 需要验证。这与浏览器中看到的纯文本 Invalid CORS request 不一致，说明浏览器 403 不像是业务权限逻辑返回的。'
      },
      {
        type: 'heading',
        text: '真正的转折点'
      },
      {
        type: 'paragraph',
        text:
          '线上前端 bundle 和浏览器 Network 都显示请求负载确实带着 liveMode:false。后端日志也没有出现 TravelPlan Controller 内部的 Owner 权限拒绝记录。此时最重要的线索变成了响应体本身：Invalid CORS request 通常意味着请求已经到达 Spring/Tomcat，但在 Spring MVC 的 CORS 处理阶段被拦下，还没有进入业务 Controller。'
      },
      {
        type: 'heading',
        text: '根因'
      },
      {
        type: 'paragraph',
        text:
          'Spring Boot 的 CORS 配置默认只面向本地开发 Origin。生产环境没有把 https://seewhyai.me 和 https://www.seewhyai.me 加入允许列表。浏览器请求会携带 Origin: https://seewhyai.me，而普通 curl 默认不会携带 Origin，所以出现了“curl 成功，浏览器失败”的错觉。'
      },
      {
        type: 'callout',
        text:
          '这次的 403 不是 Nginx 转发失败，不是后端接口不可用，不是 liveMode 误设为 true，也不是 Owner Token 权限逻辑误杀。根因是生产环境 CORS 白名单缺少线上域名。'
      },
      {
        type: 'heading',
        text: '修复方式'
      },
      {
        type: 'paragraph',
        text:
          '修复思路是在生产环境配置中显式加入公开站点 Origin，然后重启 Spring Boot 服务。这里不需要改动 Travel Agent 业务逻辑，也不需要放宽 Owner Live 权限边界。'
      },
      {
        type: 'code',
        language: 'bash',
        text:
          'WAYFINDER_CORS_ALLOWED_ORIGIN_PATTERNS=https://seewhyai.me,https://www.seewhyai.me,http://localhost:<vite-dev-port>'
      },
      {
        type: 'heading',
        text: '修复后验证'
      },
      {
        type: 'paragraph',
        text:
          '为了复现浏览器行为，验证时要在 curl 里显式加入 Origin 头。这个步骤比单纯 curl API 更接近真实线上路径。'
      },
      {
        type: 'code',
        language: 'bash',
        text:
          'curl -i -X POST https://seewhyai.me/api/travel/plan \\\n  -H "Origin: https://seewhyai.me" \\\n  -H "Content-Type: application/json" \\\n  -d \'{"message":"Plan a relaxed 5-day family trip from Shanghai to Kyoto.","chatId":"debug-cors-plan","liveMode":false}\''
      },
      {
        type: 'list',
        items: [
          '期望 HTTP 200。',
          '响应头包含 Access-Control-Allow-Origin: https://seewhyai.me。',
          '页面点击 Generate TravelPlan 后正常展示结构化 TravelPlan 卡片。',
          'Owner Live 未验证时仍保持受保护，不影响公开 demo。'
        ]
      },
      {
        type: 'heading',
        text: '经验总结'
      },
      {
        type: 'paragraph',
        text:
          '浏览器失败但 curl 成功时，不要只比较请求体，也要比较请求头。CORS 问题的关键往往正是 Origin、Host、代理头和后端允许列表之间的差异。Invalid CORS request 这类响应也提示我们：请求很可能已经到达应用框架，但还没有进入业务 Controller。'
      },
      {
        type: 'paragraph',
        text:
          '另一个经验是要把 Public Demo 和 Owner Live 的 403 明确区分。Owner 权限失败应该是可解释的业务 JSON；CORS 失败通常是框架层的拒绝文本。两者状态码相同，但排障方向完全不同。'
      }
    ]
  }
]

export function getBlogPost(slug) {
  return blogPosts.find((post) => post.slug === slug)
}
