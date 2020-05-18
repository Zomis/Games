<template>
    <v-container fluid>
        <v-row class="players">
            <v-col v-for="(player, index) in view.players" :key="index">
                <SplendorPlayer :player="player" :playerInfo="players[index]" :actionable="actions" :onAction="onAction" :class="{activePlayer: index == view.currentPlayer}" />
            </v-col>
        </v-row>
        <v-row>
            <v-col cols="9">
                <v-row v-for="level in view.cardLevels" :key="level.level" :class="'card-level-' + level.level">
                    <v-col><v-card><v-card-title>{{ level.remaining }}</v-card-title></v-card></v-col>
                    <v-col v-for="card in level.board" :key="card.id">
                        <SplendorCard :card="card" :actions="actions" :onAction="onAction" />
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
                                <span :class="{ ['bank-' + index]: true, actionable: actions['takeMoney-' + index] }" @click="takeMoney(index)">{{ money }}</span>
                            </v-col>
                        </v-row>
                   </v-card-text>
                </v-card>
            </v-col>
            <v-col class="nobles">
                <p>Nobles</p>
                <v-row v-for="noble in view.nobles" :key="noble.id">
                    <v-col>
                        <SplendorNoble :noble="noble" />
                    </v-col>
                </v-row>
            </v-col>
        </v-row>
        <v-row class="player">
            <v-col>
                <SplendorPlayer :player="player" :playerInfo="players[view.viewer]" controller :actionable="actions" :onAction="onAction" />
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
import SplendorCard from "./SplendorCard"
import SplendorPlayer from "./SplendorPlayer"
import SplendorNoble from "./SplendorNoble"

export default {
    name: "Splendor",
    props: ["view", "actions", "actionChoice", "onAction", "players"],
    components: { SplendorPlayer, SplendorCard, SplendorNoble },
    methods: {
        takeMoney(moneyType) {
            console.log(moneyType)
            this.onAction("takeMoney", 'take-' + moneyType)
        }
    },
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

.activePlayer {
    border-style: solid !important;
        border-width: thick !important;
        border-color: var(--splendor-yellow) !important;
}

.bank-RED,
.bank-BLUE,
.bank-GREEN,
.bank-BLACK,
.bank-WHITE,
.bank-wildcards {
    padding: 9px 14px;
    border-style: solid;
    border-width: thin;
    border-color: var(--splendor-black) !important;
    border-radius: 100%;
}
.bank-RED {
    background-color: var(--splendor-red) !important;
}
.bank-BLUE {
    background-color: var(--splendor-blue) !important;
}
.bank-GREEN {
    background-color: var(--splendor-green) !important;
}
.bank-BLACK {
    background-color: var(--splendor-black) !important;
}

.bank-WHITE,
.bank-WHITE {
    background-color: var(--splendor-white) !important;
}
.bank-wildcards {
    background-color: var(--splendor-yellow) !important;
}
.bank-RED,
.bank-BLUE,
.bank-BLACK {
    color: var(--splendor-white) !important;
}
.bank-GREEN,
.bank-WHITE,
.bank-wildcards {
    color: var(--splendor-black) !important;
}
</style>
