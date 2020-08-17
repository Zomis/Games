<template>
    <v-container>
        <v-row>
            <v-col>{{view}}</v-col>
        </v-row>
        <v-row>
            <v-col v-for="(amount, index) in view.coins" :key="index">
                <span :class="{['coin-' + index]: true}">
                    {{ amount }}
                </span>
            </v-col>
        </v-row>
    </v-container>
</template>
<script>
    export default {
        name: "SpiceRoad",
        props: ["view", "actions", "context"]
    }
</script>
<style>
    :root{
        --spiceRoad-yellow: #ffd166;
        --spiceRoad-red: #ef476f;
        --spiceRoad-green: #06D6A0;
        --spiceRoad-brown: #a5701e;
        --spiceRoad-silver: #b2b9c7;
        --spiceRoad-gold: #eea12c;
    }

    .coin-gold,
    .coin-silver {
        padding: 9px 14px;
        border-style: solid;
        border-width: thin;
        border-color: black !important;
        border-radius: 100%;
    }
    .coin-gold {
        background-color: var(--spiceRoad-gold) !important;
    }
    .coin-silver {
            background-color: var(--spiceRoad-silver) !important;
        }
</style>