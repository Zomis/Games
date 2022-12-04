<template>
  <v-card>
    <v-card-title>{{ card.name }}</v-card-title>
    <v-card-text>
      {{ card.id }}
      <div v-for="(count, threat) in card.threats" :key="threat">
        <v-icon v-for="i in count" :key="i">{{ threats[threat] }}</v-icon>
      </div>
      <p v-if="card.name">
        {{ card.description }}
      </p>
    </v-card-text>
  </v-card>
</template>
<script>
export default {
  name: "GrizzledCard",
  props: ["card", "threats"]
}
</script>