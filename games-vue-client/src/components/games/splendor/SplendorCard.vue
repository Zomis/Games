<template>
    <v-card :class="'discount-' + discountColor">
        <v-card-text>
            <p>{{ card.points }}</p>
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
<style>
.discount-RED {
    background-color: red !important;
}
.discount-WHITE {
    background-color: white !important;
}
.discount-BLACK {
    background-color: black !important;
}
.discount-GREEN {
    background-color: green !important;
}
.discount-BLUE {
    background-color: blue !important;
}

.discount-RED .v-card__text,
.discount-BLACK .v-card__text,
.discount-BLUE .v-card__text,
.discount-GREEN .v-card__text {
    color: white !important;
}

</style>