<template>
    <v-card :class="'discount-' + discountColor">
        <v-card-text>
            <v-row>
                <v-col cols="2">
                    <h1 class="ma-1" style="text-align:left;">{{ card.points }}</h1>
                </v-col>
                <v-col cols="10">
                    <v-row justify="end">
                        <div class="ma-1" v-for="(cost, index) in card.costs" :key="index">
                            <span :class="'cost-' + index">{{ cost }}</span>
                        </div>
                    </v-row>
                </v-col>
            </v-row>
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
:root{
    --splendor-red: #ef476f;
    --splendor-blue: #118AB2;
    --splendor-green: #06D6A0;
    --splendor-black: #011627;
    --splendor-white: #f0e6ef;
}

.discount-RED {
    background-color: var(--splendor-red) !important;
}
.discount-WHITE {
    background-color: var(--splendor-white) !important;
}
.discount-BLACK {
    background-color: var(--splendor-black) !important;
}
.discount-GREEN {
    background-color: var(--splendor-green) !important;
}
.discount-BLUE {
    background-color: var(--splendor-blue) !important;
}
.discount-RED .v-card__text,
.discount-BLACK .v-card__text,
.discount-BLUE .v-card__text {
    color: var(--splendor-white) !important;
}

.cost-RED,
.cost-BLUE,
.cost-GREEN,
.cost-BLACK,
.cost-WHITE {
    padding: 5px 10px;
    border-style: solid;
    border-width: thin;
    border-radius: 100%;
}
.cost-RED {
    background-color: var(--splendor-red) !important;
}
.cost-BLUE {
    background-color: var(--splendor-blue) !important;
}
.cost-GREEN {
    background-color: var(--splendor-green) !important;
}
.cost-BLACK {
    background-color: var(--splendor-black) !important;
}
.cost-WHITE {
    background-color: var(--splendor-white) !important;
}

.cost-RED,
.cost-BLUE,
.cost-BLACK {
    border-color: var(--splendor-white);
    color: var(--splendor-white) !important;
}
.cost-GREEN,
.cost-WHITE {
    border-color: var(--splendor-black);
    color: var(--splendor-black) !important;
}



</style>