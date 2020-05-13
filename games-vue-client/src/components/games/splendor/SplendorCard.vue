<template>
    <v-card :class="'discount-' + discountColor">
        <v-card-text>
            <p>{{ card.points }}</p>
            <p>{{ card.discount }}</p>
            <p v-for="(cost, index) in card.costs" :key="index">{{ cost }} {{ index }}</p>
        </v-card-text>
    </v-card>
</template>
<script>
export default {
    name: "SplendorCard",
    props: ["card", "noble"],
    computed: {
        discountColor() {
            return Object.keys(this.card.discount)[0];
        }
    }
}
</script>