<template>
  <div>
    <template v-if="vertical">
      <v-row
        v-for="(amount, resource) in caravan"
        :key="resource"
      >
        <v-col>
          <v-icon
            v-for="i in amount"
            :key="resource + '-' + i"
            :color="colors[resource]"
          >
            mdi-cube
          </v-icon>
        </v-col>
      </v-row>
    </template>
    <template v-else>
      <span
        v-for="(amount, resource) in caravan"
        :key="resource"
      >
        <v-icon
          v-for="i in amount"
          :key="resource + '-' + i"
          :color="colors[resource]"
        >mdi-cube</v-icon>
      </span>
    </template>
  </div>
</template>
<script>
let colors = {
  "BROWN": "#a5701e",
  "YELLOW": "#ffd166",
  "RED": "#ef476f",
  "GREEN": "#06d6a0"
}
export default {
  name: "SpiceRoadResources",
  props: ["caravan", "vertical"],
  data() {
    return {
      colors: colors
    }
  }
}
</script>
<style>
:root {
  --spiceRoad-orange: orange;
  --spiceRoad-yellow: #ffd166;
  --spiceRoad-red: #ef476f;
  --spiceRoad-green: #06D6A0;
  --spiceRoad-brown: #a5701e;
  --spiceRoad-silver: #b2b9c7;
  --spiceRoad-gold: #eea12c;
}

.resource {
    padding: 9px 14px;
    border-style: solid;
    border-width: thin;
    border-color: black !important;
    border-radius: 100%;
}
.resource-YELLOW {
    background-color: var(--spiceRoad-orange) !important;
}
.resource-GREEN {
    background-color: var(--spiceRoad-green) !important;
}
.resource-RED {
    background-color: var(--spiceRoad-red) !important;
}
.resource-BROWN {
    background-color: var(--spiceRoad-brown) !important;
}
</style>