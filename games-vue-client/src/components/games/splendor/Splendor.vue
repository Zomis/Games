<template>
    <v-container fluid class="splendor">
        <v-row>
            <v-col>Round {{ view.round }}</v-col>
        </v-row>
        <v-row class="players">
            <v-col v-for="(player, index) in view.players" :key="index">
                <SplendorPlayer :player="player" :playerInfo="context.players[index]"
                 :controllable="index == view.viewer"
                 :actions="actions" :class="{activePlayer: index == view.currentPlayer}" />
            </v-col>
        </v-row>
        <v-row>
            <v-col cols="9">
                <CardZone class="row" v-for="level in view.cardLevels" :key="level.level" :class="'card-level-' + level.level">
                    <v-col :key="'remaining-' + level.level"><v-card><v-card-title>{{ level.remaining }}</v-card-title></v-card></v-col>
                    <v-col v-for="card in level.board" :key="card.id" class="list-complete-item">
                        <SplendorCard :card="card" :actions="actions" />
                    </v-col>
                </CardZone>
            </v-col>
            <v-col cols="1" class="stock">
                <v-card>
                    <v-card-title>
                        Bank
                    </v-card-title>
                    <v-card-text>
                        <v-row v-for="(money, index) in view.stock" :key="index">
                            <v-col>
                                <span :class="{
                                    ['bank-' + index]: true,
                                    actionable: actions.available['take-' + index],
                                    'chosen-once': (actions.chosen) ? actions.chosen.choices.includes(index) : false,
                                    'chose-again': (actions.chosen) ? actions.chosen.choices.includes(index) && actions.available['take-' + index] : false}"
                                    @click="takeMoney(index)">{{ money }}</span>
                            </v-col>
                        </v-row>
                        <v-row>
                            <v-col><v-btn @click="takeCurrentMoney()">Take</v-btn></v-col>
                        </v-row>
                   </v-card-text>
                </v-card>
            </v-col>
            <v-col class="nobles">
                <p>Nobles</p>
                <CardZone>
                    <v-row v-for="noble in view.nobles" :key="noble.id">
                        <v-col>
                            <SplendorNoble :noble="noble" :context="context" />
                        </v-col>
                    </v-row>
                </CardZone>
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
import SplendorCard from "./SplendorCard"
import SplendorPlayer from "./SplendorPlayer"
import SplendorNoble from "./SplendorNoble"
import CardZone from "@/components/games/common/CardZone"

export default {
    name: "Splendor",
    props: ["view", "actions", "context"],
    components: {
        SplendorPlayer, SplendorCard, SplendorNoble, CardZone
    },
    methods: {
        takeMoney(moneyType) {
            this.actions.perform("takeMoney", 'take-' + moneyType)
        },
        takeCurrentMoney() {
            this.actions.performChosen()
        }
    },
    computed: {
        player() { return this.view.players[this.view.viewer] }
    }
}
</script>
<style>

.animate, .list-complete-item {
    transition: all 1.5s linear;
    display: inline-block !important;
    margin-right: 10px;
}

.list-complete-enter {
    opacity: 0;
    transform: translateY(30px);
}
.list-complete-leave-to {
    opacity: 0;
    transform: translateY(-30px);
}
.list-complete-leave-active {
    position: absolute;
}

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

.splendor .chosen-once,
.splendor .actionable:hover {
    border-width: thick !important;
    border-color: var(--splendor-yellow) !important;
}
.chose-again:hover {
    border-width: thick !important;
    border-color: var(--splendor-red) !important;
}
</style>
