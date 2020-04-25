<template>
    <v-col>
    <v-card class="hanabi-card" :class="{ 'actionable': action }" :style="{ 'background-color': 'black' }">
        <v-card-title :class="'color-' + cardColor">
            {{ card.value }}
        </v-card-title>
        <v-card-actions v-if="action">
            <v-btn @click="action('Play', 'play-' + index)">Play</v-btn>
            <v-btn @click="action('Discard', 'discard-' + index)">Discard</v-btn>
        </v-card-actions>
    </v-card>
    </v-col>
</template>
<script>
export default {
    name: "HanabiCard",
    props: ["card", "action"],
    computed: {
        cardColor() {
            if (this.card.color) {
                return this.card.color.toLowerCase()
            }
            return 'unknown'
        }
    },

}
</script>
<style>
.color-unknown {
    color: gray;
}
.color-red {
    color: red;
}
.color-green {
    color: lime;
}
.color-blue {
    color: cyan;
}
.color-white {
    color: white;
}
.color-yellow {
    color: yellow;
}
.hanabi-card.actionable {
    width: 160px;
    height: 80px;
}
.hanabi-card {
    width: 48px;
    height: 48px;
}
</style>
