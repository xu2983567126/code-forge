<template>
  <div class="flex min-h-screen flex-col bg-background">
    <header class="border-b bg-card">
      <div class="container mx-auto flex h-16 items-center gap-3 px-4">
        <img src="@/assets/logo.png" alt="logo" class="h-9 w-9" />
        <span class="text-lg font-bold">CodeForge</span>
      </div>
    </header>
    <main class="flex-1">
      <router-view />
    </main>
  </div>
</template>

<script setup lang="ts"></script>
