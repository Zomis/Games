<template>
    <v-card>
        <v-card-text>
            <v-row>
                <v-col cols="2">
                    <h1 class="ma-1" style="text-align:left;">{{ noble.points }}</h1>
                </v-col>
                <v-col cols="10">
                    <v-row justify="end">
                        <div class="ma-1" v-for="(cost, index) in noble.requirements" :key="index">
                            <span :class="'resource-' + index">{{ cost }}</span>
                        </div>
                    </v-row>
                </v-col>
            </v-row>
        </v-card-text>
    </v-card>
</template>
<script>

export default {
    name: "SplendorNoble",
    props: ["noble"]
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
.resource-WHITE {
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
.resource-RED {
    background-color: var(--splendor-red) !important;
}
.resource-BLUE {
    background-color: var(--splendor-blue) !important;
}
.resource-GREEN {
    background-color: var(--splendor-green) !important;
}
.resource-BLACK {
    background-color: var(--splendor-black) !important;
}
.resource-RED,
.resource-BLUE,
.resource-BLACK {
    color: var(--splendor-white) !important;
}
.resource-GREEN,
.resource-WHITE {
    color: var(--splendor-black) !important;
}
</style>
