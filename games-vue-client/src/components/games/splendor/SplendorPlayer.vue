<template>
    <v-card>
        <v-card-text>
            <v-row justify="start">
                <v-col cols="3">
                    <v-avatar :size="32">
                      <img
                        :src="playerInfo.picture"
                        :alt="playerInfo.name" />
                    </v-avatar>
                    <span>
                        {{ playerInfo.name }}
                    </span>
                </v-col>
                <v-col cols="1">
                    <h1>{{ player.points }}</h1>
                </v-col>
                <v-col>
                    <v-row justify="center" align="center">
                        <div class="ma-1" v-for="(value, index) in player.discounts" :key="'resource-' + index">
                            <span :class="'resource-' + index">{{ value }}</span>
                        </div>
                        <div v-for="(value, index) in player.money" :key="'gem-' + index" @click="discard(index)"
                            :class="{ discardable: controllable && actions.available.discardMoney && actions.available.discardMoney['discardMoney-' + index] }">
                            <span :class="'gems-' + index">{{ value }}</span>
                        </div>
                    </v-row>
                </v-col>
            </v-row>
            <p v-if="player.reserved">Reserved Cards: {{ player.reserved }}</p>
            <v-row justify="start">
                <v-col cols="4" v-for="card in player.reservedCards" :key="card.id">
                    <SplendorCard :card="card" :actions="actions" />
                </v-col>
            </v-row>
        </v-card-text>
    </v-card>
</template>
<script>
import SplendorCard from "./SplendorCard"

export default {
    name: "SplendorPlayer",
    props: ["player", "controllable", "actions", "playerInfo"],
    components: { SplendorCard },
    methods: {
        discard(moneyType) {
            if (this.controllable) {
                this.actions.perform("discardMoney", "discardMoney-" + moneyType)
            }
        }
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

.resource-RED,
.resource-BLUE,
.resource-GREEN,
.resource-BLACK,
.resource-WHITE,
.gems-RED,
.gems-BLUE,
.gems-GREEN,
.gems-BLACK,
.gems-WHITE,
.gems-wildcards {
    padding: 5px 10px;
    margin: 0px 5px;
    border-style: solid;
    border-width: thin;
    border-color: var(--splendor-black) !important;
}
.resource-RED,
.resource-BLUE,
.resource-GREEN,
.resource-BLACK,
.resource-WHITE {
    border-radius: 20%;
}
.gems-RED,
.gems-BLUE,
.gems-GREEN,
.gems-BLACK,
.gems-WHITE,
.gems-wildcards {
    border-radius: 100%;
}
.resource-RED,
.gems-RED {
    background-color: var(--splendor-red) !important;
}

.resource-BLUE,
.gems-BLUE {
    background-color: var(--splendor-blue) !important;
}

.resource-GREEN,
.gems-GREEN {
    background-color: var(--splendor-green) !important;
}

.resource-BLACK,
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
.resource-RED,
.resource-BLUE,
.resource-BLACK,
.gems-RED,
.gems-BLUE,
.gems-BLACK {
    color: var(--splendor-white) !important;
}
.resource-GREEN,
.resource-WHITE,
.gems-GREEN,
.gems-WHITE,
.gems-wildcards {
    color: var(--splendor-black) !important;
}
</style>
