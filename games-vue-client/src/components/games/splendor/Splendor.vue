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
            <v-col cols="9">
                <v-row v-for="level in view.cardLevels" :key="level.level" :class="'card-level-' + level.level">
                    <v-col><v-card><v-card-title>{{ level.remaining }}</v-card-title></v-card></v-col>
                    <v-col v-for="card in level.board" :key="card.id">
                        <SplendorCard :card="card" />
                    </v-col>
                </v-row>
            </v-col>
            <v-col cols="1" class="stock">
                <v-card>
                    <v-card-title>
                        Bank
                    </v-card-title>
                    <v-card-text>
                        <v-row v-for="(money, index) in view.stock" :key="index">
                            <v-col>
                                <span :class="'gems-' + index">{{ money }}</span>
                            </v-col>
                        </v-row>
                   </v-card-text>
                </v-card>
            </v-col>
            <v-col class="nobles">
                <p>Nobles</p>
                <v-row v-for="noble in view.nobles" :key="noble.id">
                    <SplendorCard :noble="noble" />
                </v-row>
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
<style>
:root{
    --splendor-red: #ef476f;
    --splendor-blue: #118AB2;
    --splendor-green: #06D6A0;
    --splendor-black: #011627;
    --splendor-white: #f0e6ef;
    --splendor-yellow: #ffd166;
}

.gems-RED,
.gems-BLUE,
.gems-GREEN,
.gems-BLACK,
.gems-WHITE,
.gems-wildcards {
    padding: 9px 14px;
    border-style: solid;
    border-width: thin;
    border-color: var(--splendor-black) !important;
    border-radius: 100%;
}
.gems-RED {
    background-color: var(--splendor-red) !important;
}
.gems-BLUE {
    background-color: var(--splendor-blue) !important;
}
.gems-GREEN {
    background-color: var(--splendor-green) !important;
}
.gems-BLACK {
    background-color: var(--splendor-black) !important;
}

.gems-WHITE,
.gems-WHITE {
    background-color: var(--splendor-white) !important;
}
.gems-wildcards {
    background-color: var(--splendor-yellow) !important;
}
.gems-RED,
.gems-BLUE,
.gems-BLACK {
    color: var(--splendor-white) !important;
}
.gems-GREEN,
.gems-WHITE,
.gems-wildcards {
    color: var(--splendor-black) !important;
}
</style>