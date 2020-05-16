<template>
    <v-container fluid>
        <GameTreeView :view="view" />
        <GameTreeView :actions="actions" :onAction="onAction" />
        <v-row class="players">
            <v-col v-for="(player, index) in view.players" :key="index">
                <SplendorPlayer :player="player" />
            </v-col>
        </v-row>
        <v-row>
            <v-col cols="10">
                <v-row v-for="level in view.cardLevels" :key="level.level" :class="'card-level-' + level.level">
                    <v-col><v-card><v-card-title>{{ level.remaining }}</v-card-title></v-card></v-col>
                    <v-col v-for="card in level.board" :key="card.id">
                        <SplendorCard :card="card" />
                    </v-col>
                </v-row>
            </v-col>
            <v-col class="nobles">
                <p>Nobles</p>
                <v-row v-for="noble in view.nobles" :key="noble.id">
                    <SplendorCard :noble="noble" />
                </v-row>
            </v-col>
        </v-row>
        <v-row class="stock">
            <v-col v-for="(money, index) in view.stock" :key="index">
                {{ index }} {{ money }}
            </v-col>
        </v-row>
        <v-row class="player">
            <v-col>
                <SplendorPlayer :player="player" controller />
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
import SplendorCard from "./SplendorCard"
import SplendorPlayer from "./SplendorPlayer"
import GameTreeView from "@/components/games/debug/GameTreeView"

export default {
    name: "Splendor",
    props: ["view", "actions", "actionChoice", "onAction", "players"],
    components: { GameTreeView, SplendorPlayer, SplendorCard },
    computed: {
        player() { return this.view.players[this.view.viewer] }
    }
}
</script>