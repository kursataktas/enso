<script lang="ts">
export const name = 'Image'
export const icon = 'image'
export const inputType = 'Standard.Image.Image.Image'

interface Data {
  mediaType?: string
  base64: string
}
</script>

<script setup lang="ts">
import { computed } from 'vue'

const props = defineProps<{ data: Data }>()

const DEFAULT_MEDIA_TYPE = 'image/png'

const src = computed(
  () => `data:${props.data.mediaType ?? DEFAULT_MEDIA_TYPE};base64,${props.data.base64}`,
)
</script>

<template>
  <div class="ImageVisualization">
    <img :src="src" />
  </div>
</template>

<style scoped>
.ImageVisualization {
  > img {
    width: 100%;
  }
}
</style>
