<template>
    <v-container fluid>
        <GameTreeView :view="view" />
        <GameTreeView :actions="actions" :onAction="onAction" />
    </v-container>
</template>
<script>
import GameTreeView from "@/components/games/debug/GameTreeView"

export default {
    name: "TreeViewGame",
    props: ["view", "actions", "actionChoice", "onAction", "players"],
    components: { GameTreeView }
}
</script>