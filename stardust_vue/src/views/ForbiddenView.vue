<template>
  <section class="page-shell empty-state">
    <span class="empty-symbol" aria-hidden="true">⊘</span>
    <p class="eyebrow">访问受限</p>
    <h1>当前角色无权访问此页面。</h1>
    <p>后端接口同样会进行权限校验，请返回工作区。</p>
    <router-link class="primary-button inline-button" to="/">
      返回首页
    </router-link>
  </section>
</template>
