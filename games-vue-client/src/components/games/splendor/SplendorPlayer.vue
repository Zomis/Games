<template>
    <v-card>
        <v-card-text>
            <p>{{ player.points }}</p>
            <p>Discounts</p>
            <div>
                <span v-for="(value, index) in player.discounts" :key="index" :class="'gems-' + index">{{ value }}</span>
            </div>
            <p>Money</p>
            <div>
                <span v-for="(value, index) in player.money" :key="index" :class="'gems-' + index">{{ value }}</span>
            </div>
            <p v-if="player.reserved">Reserved Cards: {{ player.reserved }}</p>
            <p v-if="player.reservedCards">Reserved</p>
            <v-row justify="start">
                <v-col cols="4" v-for="card in player.reservedCards"  :key="card.id">
                    <SplendorCard :card="card" />
                </v-col>
            </v-row>
        </v-card-text>
    </v-card>
</template>
<script>
import SplendorCard from "./SplendorCard"

export default {
    name: "SplendorPlayer",
    props: ["player"],
    components: { SplendorCard }
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
    padding: 5px 10px;
    margin: 0px 5px;
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
