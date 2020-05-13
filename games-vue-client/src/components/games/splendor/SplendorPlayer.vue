<template>
    <v-card>
        <v-card-text>
            <p>{{ player.points }}</p>
            <p>Discounts</p>
            <div>
                <p v-for="(value, index) in player.discounts" :key="index">{{ value }} {{ index }}</p>
            </div>
            <p>Money</p>
            <div>
                <p v-for="(value, index) in player.money" :key="index">{{ value }} {{ index }}</p>
            </div>
            <p v-if="player.reserved">Reserved Cards: {{ player.reserved }}</p>
            <p v-if="player.reservedCards">Reserved</p>
            <SplendorCard v-for="card in player.reservedCards" :card="card" :key="card" />
        </v-card-text>
    </v-card>
</template>
<script>
export default {
    name: "SplendorPlayer",
    props: ["player"]
}
</script>
