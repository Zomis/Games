<template>
    <v-container fluid>
        <GameTreeView :view="view" />
        <GameTreeView :actions="actions2" />
    </v-container>
</template>
<script>
import GameTreeView from "@/components/games/debug/GameTreeView"

export default {
    name: "TreeViewGame",
    props: ["view", "actions2", "players"],
    components: { GameTreeView }
}
</script>