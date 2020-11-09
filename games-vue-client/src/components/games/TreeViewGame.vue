<template>
  <v-container fluid>
    <GameTreeView :view="view" />
    <GameTreeView :actions="actions" />
  </v-container>
</template>
<script>
import GameTreeView from "@/components/games/debug/GameTreeView"

export default {
    name: "TreeViewGame",
    props: ["view", "actions", "players"],
    components: { GameTreeView }
}
</script>